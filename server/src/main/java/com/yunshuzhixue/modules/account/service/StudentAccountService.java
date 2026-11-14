package com.yunshuzhixue.modules.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.account.api.StudentStp;
import com.yunshuzhixue.modules.account.entity.Student;
import com.yunshuzhixue.modules.account.mapper.StudentMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 学员注册、登录、本人信息（需求 5.1，S-ACC-03、04、07） */
@Service
public class StudentAccountService {

    private static final String STATUS_NORMAL = "normal";
    private static final String STATUS_BANNED = "banned";
    private static final String STATUS_CANCELLING = "cancelling";

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

    public StudentAccountService(StudentMapper students, StudentVerificationService verification,
                                 PasswordEncoder passwordEncoder, StringRedisTemplate redis, JdbcTemplate jdbc) {
        this.students = students;
        this.verification = verification;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
        this.jdbc = jdbc;
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

    private Student findByPhone(String phone) {
        return students.selectOne(new LambdaQueryWrapper<Student>().eq(Student::getPhone, phone));
    }

    private StudentProfile profileOf(Student s) {
        Instant effectiveAt = STATUS_CANCELLING.equals(s.getStatus()) && s.getCancellationEffectiveAt() != null
                ? com.yunshuzhixue.common.time.UtcTime.toInstant(s.getCancellationEffectiveAt()) : null;
        return new StudentProfile(s.getId(), s.getPhone(), s.getNickname(), s.getAvatarUrl(), s.getEducation(),
                s.getJobIntentLearningRouteId(), s.getJobIntentName(), false, s.getWorkYears(), s.getStatus(),
                s.getPasswordHash() != null, effectiveAt, List.of());
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
