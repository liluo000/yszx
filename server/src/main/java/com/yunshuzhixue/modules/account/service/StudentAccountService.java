package com.yunshuzhixue.modules.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunshuzhixue.common.agreement.AgreementConsentGateway;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.time.UtcTime;
import com.yunshuzhixue.infra.storage.PublicImageUploader;
import com.yunshuzhixue.modules.account.api.StudentRegisteredEvent;
import com.yunshuzhixue.modules.account.api.StudentStp;
import com.yunshuzhixue.modules.account.entity.Student;
import com.yunshuzhixue.modules.account.mapper.StudentMapper;
import com.yunshuzhixue.modules.content.api.RouteDirectory;
import com.yunshuzhixue.modules.content.api.RouteDirectory.RouteCard;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 学员账号：注册、登录、本人信息、密码、换绑、资料与画像、注销（需求 5.1，S-ACC-01~13） */
@Service
public class StudentAccountService {

    private static final String STATUS_NORMAL = "normal";
    private static final String STATUS_BANNED = "banned";
    private static final String STATUS_CANCELLING = "cancelling";

    /** 注销冷静期 7 天（需求 5.1.5） */
    private static final int CANCELLATION_COOLING_DAYS = 7;

    private static final Set<String> EDUCATIONS = Set.of("high_school_or_below", "junior_college",
            "bachelor", "master_or_above");
    private static final Set<String> WORK_YEARS = Set.of("student_or_graduate", "under_1_year",
            "1_to_3_years", "3_to_5_years", "over_5_years");

    /** 本批协议链路简化点：只校验提交的版本号是否为最新，同意记录由 M17 在下一批按事件订阅补齐 */
    public record AgreementRef(String agreementType, int versionNo) {
    }

    public record StudentProfile(Long id, String phone, String nickname, String avatarUrl, String education,
                                 Long jobIntentLearningRouteId, String jobIntentName, boolean isJobIntentOffShelf,
                                 String workYears, String status, boolean hasPassword,
                                 Instant cancellationEffectiveAt, List<AgreementRef> pendingAgreements) {
    }

    private final StudentMapper students;
    private final StudentVerificationService verification;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final RouteDirectory routes;
    private final PublicImageUploader images;
    private final AgreementConsentGateway consents;
    private final ApplicationEventPublisher events;

    public StudentAccountService(StudentMapper students, StudentVerificationService verification,
                                 PasswordEncoder passwordEncoder, StringRedisTemplate redis, JdbcTemplate jdbc,
                                 RouteDirectory routes, PublicImageUploader images,
                                 AgreementConsentGateway consents, ApplicationEventPublisher events) {
        this.students = students;
        this.verification = verification;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
        this.jdbc = jdbc;
        this.routes = routes;
        this.images = images;
        this.consents = consents;
        this.events = events;
    }

    @Transactional
    public LoginResult register(String phone, String smsCode, List<AgreementRef> agreements) {
        checkAgreements(agreements);
        verification.verifySmsCode("register", phone, smsCode);
        if (findByPhone(phone) != null) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }
        Student student = new Student();
        student.setPhone(phone);
        student.setNickname("学员" + phone.substring(7));
        student.setStatus(STATUS_NORMAL);
        students.insert(student);
        publishRegistered(student.getId(), agreements);
        return login(student, false);
    }

    @Transactional
    public LoginResult login(String loginType, String phone, String smsCode, String password,
                             List<AgreementRef> agreements) {
        if ("sms".equals(loginType)) {
            verification.verifySmsCode("login", phone, smsCode);
            Student student = findByPhone(phone);
            boolean isNew = student == null;
            if (isNew) {
                checkAgreements(agreements);
                student = new Student();
                student.setPhone(phone);
                student.setNickname("学员" + phone.substring(7));
                student.setStatus(STATUS_NORMAL);
                students.insert(student);
                publishRegistered(student.getId(), agreements);
            }
            return login(student, isNew);
        }
        if (!"password".equals(loginType)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "登录方式不正确");
        }
        String lockKey = "account:pwd-lock:" + phone;
        if (Boolean.TRUE.equals(redis.hasKey(lockKey))) {
            throw new BusinessException(ErrorCode.LOGIN_LOCKED);
        }
        Student student = findByPhone(phone);
        if (student == null) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (student.getPasswordHash() == null) {
            throw new BusinessException(ErrorCode.PASSWORD_NOT_SET);
        }
        if (!passwordEncoder.matches(password, student.getPasswordHash())) {
            String failKey = "account:pwd-fail:" + phone;
            Long failures = redis.opsForValue().increment(failKey);
            if (failures != null && failures == 1) {
                redis.expire(failKey, Duration.ofMinutes(15));
            }
            if (failures != null && failures >= 5) {
                redis.delete(failKey);
                redis.opsForValue().set(lockKey, "1", Duration.ofMinutes(15));
                throw new BusinessException(ErrorCode.LOGIN_LOCKED);
            }
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        redis.delete("account:pwd-fail:" + phone);
        return login(student, false);
    }

    private LoginResult login(Student student, boolean isNew) {
        if (STATUS_BANNED.equals(student.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        }
        StudentStp.LOGIC.login(student.getId());
        return new LoginResult(StudentStp.LOGIC.getTokenValue(), isNew, profileOf(student));
    }

    public StudentProfile me() {
        long id = StudentStp.LOGIC.getLoginIdAsLong();
        Student student = students.selectById(id);
        if (student == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return profileOf(student);
    }

    public void logout() {
        StudentStp.LOGIC.logout();
    }

    /** S-ACC-06：通过短信验证码重置密码，成功后全设备退出 */
    @Transactional
    public void resetPassword(String phone, String smsCode, String newPassword) {
        verification.verifySmsCode("reset_password", phone, smsCode);
        Student student = findByPhone(phone);
        if (student == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该手机号尚未注册");
        }
        if (STATUS_BANNED.equals(student.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        }
        savePassword(student, newPassword);
    }

    /** S-ACC-08：修改昵称与求职画像；字段缺省表示不修改，显式传 null 表示清空 */
    @Transactional
    public StudentProfile updateProfile(Map<String, Object> body) {
        Student student = requireActiveCurrent();
        if (body.containsKey("nickname")) {
            Object raw = body.get("nickname");
            String nickname = raw == null ? "" : raw.toString().trim();
            if (nickname.isEmpty() || nickname.length() > 30) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "昵称需要 1~30 个字符");
            }
            student.setNickname(nickname);
        }
        if (body.containsKey("education")) {
            student.setEducation(enumValue(body.get("education"), EDUCATIONS, "学历取值不正确"));
        }
        if (body.containsKey("work_years")) {
            student.setWorkYears(enumValue(body.get("work_years"), WORK_YEARS, "工作年限取值不正确"));
        }
        if (body.containsKey("job_intent_learning_route_id")) {
            Object raw = body.get("job_intent_learning_route_id");
            if (raw == null) {
                student.setJobIntentLearningRouteId(null);
                student.setJobIntentName(null);
            } else {
                long routeId = longValue(raw);
                RouteCard route = routes.publishedRoutes().stream()
                        .filter(card -> card.id() == routeId).findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "该学习路线已下架"));
                student.setJobIntentLearningRouteId(routeId);
                student.setJobIntentName(route.name());
            }
        }
        students.updateById(student);
        return profileOf(student);
    }

    /** S-ACC-09：上传头像，返回访问地址 */
    @Transactional
    public String updateAvatar(MultipartFile file) {
        Student student = requireActiveCurrent();
        String url = images.upload(file, "avatars");
        student.setAvatarUrl(url);
        students.updateById(student);
        return url;
    }

    /** S-ACC-10：设置或修改密码；已设置过密码需原密码，首次设置需短信验证码 */
    @Transactional
    public void changePassword(String oldPassword, String smsCode, String newPassword) {
        Student student = requireActiveCurrent();
        if (student.getPasswordHash() == null) {
            if (smsCode == null || smsCode.isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "首次设置密码需要短信验证码");
            }
            verification.verifySmsCode("reset_password", student.getPhone(), smsCode);
        } else {
            if (oldPassword == null || oldPassword.isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请输入原密码");
            }
            if (!passwordEncoder.matches(oldPassword, student.getPasswordHash())) {
                throw new BusinessException(ErrorCode.OLD_PASSWORD_INCORRECT);
            }
        }
        savePassword(student, newPassword);
    }

    /** S-ACC-11：换绑手机号；原手机号验证码或登录密码二选一，成功后全设备退出 */
    @Transactional
    public void changePhone(String newPhone, String newSmsCode, String oldSmsCode, String password) {
        Student student = requireActiveCurrent();
        if (newPhone.equals(student.getPhone())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "新手机号不能与原手机号相同");
        }
        if (oldSmsCode != null && !oldSmsCode.isBlank()) {
            verification.verifySmsCode("change_phone_old", student.getPhone(), oldSmsCode);
        } else if (password != null && !password.isBlank()) {
            if (student.getPasswordHash() == null) {
                throw new BusinessException(ErrorCode.PASSWORD_NOT_SET);
            }
            if (!passwordEncoder.matches(password, student.getPasswordHash())) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请提供原手机号验证码或登录密码");
        }
        if (findByPhone(newPhone) != null) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }
        verification.verifySmsCode("change_phone_new", newPhone, newSmsCode);
        student.setPhone(newPhone);
        students.updateById(student);
        StudentStp.LOGIC.logout(student.getId());
    }

    /** S-ACC-12：申请注销，进入 7 天冷静期并全设备退出 */
    @Transactional
    public Instant requestCancellation(String smsCode, boolean confirmed) {
        if (!confirmed) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请先阅读并确认注销提示");
        }
        Student student = requireActiveCurrent();
        verification.verifySmsCode("cancel_account", student.getPhone(), smsCode);
        LocalDateTime now = UtcTime.now();
        LocalDateTime effectiveAt = now.plusDays(CANCELLATION_COOLING_DAYS);
        student.setStatus(STATUS_CANCELLING);
        student.setCancellationRequestedAt(now);
        student.setCancellationEffectiveAt(effectiveAt);
        students.updateById(student);
        StudentStp.LOGIC.logout(student.getId());
        return UtcTime.toInstant(effectiveAt);
    }

    /** S-ACC-13：冷静期内撤销注销，账号恢复正常 */
    @Transactional
    public StudentProfile cancelCancellation() {
        long id = StudentStp.LOGIC.getLoginIdAsLong();
        Student student = students.selectById(id);
        if (student == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        if (!STATUS_CANCELLING.equals(student.getStatus())) {
            throw new BusinessException(ErrorCode.CANCELLATION_NOT_PENDING);
        }
        student.setStatus(STATUS_NORMAL);
        student.setCancellationRequestedAt(null);
        student.setCancellationEffectiveAt(null);
        students.updateById(student);
        return profileOf(student);
    }

    private void savePassword(Student student, String newPassword) {
        student.setPasswordHash(passwordEncoder.encode(newPassword));
        students.updateById(student);
        // 修改或重置密码后该账号在所有设备退出（需求 5.1.6）
        StudentStp.LOGIC.logout(student.getId());
    }

    /** 当前登录学员；封禁与注销中的账号不能做账号变更 */
    private Student requireActiveCurrent() {
        long id = StudentStp.LOGIC.getLoginIdAsLong();
        Student student = students.selectById(id);
        if (student == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        if (STATUS_BANNED.equals(student.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        }
        if (STATUS_CANCELLING.equals(student.getStatus())) {
            throw new BusinessException(ErrorCode.STUDENT_CANCELLING);
        }
        return student;
    }

    private static String enumValue(Object raw, Set<String> allowed, String message) {
        if (raw == null) {
            return null;
        }
        String value = raw.toString();
        if (!allowed.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
        }
        return value;
    }

    private static long longValue(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "求职意向取值不正确");
        }
    }

    private Student findByPhone(String phone) {
        return students.selectOne(new LambdaQueryWrapper<Student>().eq(Student::getPhone, phone));
    }

    private StudentProfile profileOf(Student s) {
        Instant effectiveAt = STATUS_CANCELLING.equals(s.getStatus()) && s.getCancellationEffectiveAt() != null
                ? UtcTime.toInstant(s.getCancellationEffectiveAt()) : null;
        return new StudentProfile(s.getId(), s.getPhone(), s.getNickname(), s.getAvatarUrl(), s.getEducation(),
                s.getJobIntentLearningRouteId(), s.getJobIntentName(), isJobIntentOffShelf(s), s.getWorkYears(),
                s.getStatus(), s.getPasswordHash() != null, effectiveAt, pendingAgreements(s.getId()));
    }

    /** 尚未同意的最新协议；由 M17 通过共同接口提供 */
    private List<AgreementRef> pendingAgreements(Long studentId) {
        return consents.pending(studentId).stream()
                .map(item -> new AgreementRef(item.agreementType(), item.versionNo()))
                .toList();
    }

    private void publishRegistered(Long studentId, List<AgreementRef> agreements) {
        if (agreements == null || agreements.isEmpty()) {
            return;
        }
        events.publishEvent(new StudentRegisteredEvent(studentId, agreements.stream()
                .map(item -> new StudentRegisteredEvent.AgreedAgreement(item.agreementType(), item.versionNo()))
                .toList()));
    }

    /** 求职意向对应的路线是否已下架或删除（下架后保留原值，只做标注） */
    private boolean isJobIntentOffShelf(Student s) {
        Long routeId = s.getJobIntentLearningRouteId();
        if (routeId == null) {
            return false;
        }
        return routes.publishedRoutes().stream().noneMatch(card -> card.id().equals(routeId));
    }

    /** 校验提交的协议版本是否为当前最新（协议正文由 sysconfig 维护） */
    private void checkAgreements(List<AgreementRef> agreements) {
        if (agreements == null || agreements.size() != 2) {
            throw new BusinessException(ErrorCode.AGREEMENT_VERSION_OUTDATED);
        }
        for (String type : List.of("user_agreement", "privacy_policy")) {
            Integer latest = jdbc.queryForObject(
                    "SELECT MAX(version_no) FROM agreement_version WHERE agreement_type = ?", Integer.class, type);
            // 后台尚未发布该类协议时不校验版本；发布后自动按最新版本校验
            if (latest == null) {
                continue;
            }
            boolean matched = agreements.stream().anyMatch(a -> type.equals(a.agreementType())
                    && a.versionNo() == latest);
            if (!matched) {
                throw new BusinessException(ErrorCode.AGREEMENT_VERSION_OUTDATED);
            }
        }
    }

    public record LoginResult(String token, boolean isNewStudent, StudentProfile profile) {
    }
}
