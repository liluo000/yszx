package com.yunshuzhixue.modules.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.hamcrest.Matchers.nullValue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.yunshuzhixue.modules.account.entity.Student;
import com.yunshuzhixue.modules.account.mapper.StudentMapper;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 学员账号（S-ACC-01~13）：注册登录、密码、资料与画像、头像、换绑、注销 */
@SpringBootTest
@AutoConfigureMockMvc
class StudentAccountIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private StudentMapper studentMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redis;

    private String phone;

    @AfterEach
    void cleanUp() {
        if (phone != null) {
            studentMapper.delete(new LambdaQueryWrapper<Student>().eq(Student::getPhone, phone));
        }
    }

    @Test
    void registerThenReadProfileThenLogout() throws Exception {
        phone = randomPhone();
        String captchaId = captcha();
        sendSmsCode(phone, "register", captchaId);

        MvcResult registered = mvc.perform(post("/api/v1/student/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"sms_code\":\"123456\",\"agreements\":["
                                + "{\"agreement_type\":\"user_agreement\",\"version_no\":1},"
                                + "{\"agreement_type\":\"privacy_policy\",\"version_no\":1}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.profile.nickname").value("学员" + phone.substring(7)))
                .andReturn();
        String token = JsonPath.read(registered.getResponse().getContentAsString(), "$.data.token");

        mvc.perform(get("/api/v1/student/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value(phone))
                .andExpect(jsonPath("$.data.status").value("normal"))
                .andExpect(jsonPath("$.data.has_password").value(false));
        mvc.perform(delete("/api/v1/student/sessions/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/student/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicatePhoneIsRejected() throws Exception {
        phone = randomPhone();
        sendSmsCode(phone, "register", captcha());
        register(phone).andExpect(status().isCreated());
        // 60 秒内不能重发验证码；直接注入一枚有效验证码，专门验证「手机号已注册」分支
        redis.opsForValue().set("account:sms:register:" + phone, "123456", Duration.ofMinutes(5));
        register(phone).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_ALREADY_REGISTERED"));
    }

    @Test
    void captchaMustBeValidAndConsumedOnce() throws Exception {
        phone = randomPhone();
        String captchaId = captcha();
        sendSmsCode(phone, "register", captchaId).andExpect(status().isCreated());
        // 图形验证核销后不能重复使用
        mvc.perform(post("/api/v1/student/sms-codes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"scene\":\"register\","
                                + "\"captcha_id\":\"" + captchaId + "\",\"captcha_code\":\"1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPTCHA_INVALID"));
    }

    @Test
    void passwordLoginWithoutPasswordIsRejected() throws Exception {
        phone = randomPhone();
        sendSmsCode(phone, "register", captcha());
        register(phone).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/student/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login_type\":\"password\",\"phone\":\"" + phone + "\",\"password\":\"abc123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_NOT_SET"));
    }

    @Test
    void resetPasswordInvalidatesSessionsAndAllowsPasswordLogin() throws Exception {
        phone = randomPhone();
        String token = registerWithToken(phone);
        seedSms("reset_password", phone);

        mvc.perform(post("/api/v1/student/password-resets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"sms_code\":\"123456\","
                                + "\"new_password\":\"newpass123\"}"))
                .andExpect(status().isCreated());
        // 重置后旧会话失效
        mvc.perform(get("/api/v1/student/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        // 新密码可登录
        mvc.perform(post("/api/v1/student/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login_type\":\"password\",\"phone\":\"" + phone + "\","
                                + "\"password\":\"newpass123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.profile.has_password").value(true));
    }

    @Test
    void profileAndJobIntentCanBeUpdatedAndCleared() throws Exception {
        phone = randomPhone();
        String token = registerWithToken(phone);
        Long routeId = jdbc.queryForObject(
                "SELECT id FROM learning_route WHERE status = 'on_shelf' ORDER BY id LIMIT 1", Long.class);
        String routeName = jdbc.queryForObject("SELECT name FROM learning_route WHERE id = ?", String.class, routeId);

        mvc.perform(patch("/api/v1/student/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"小明同学\",\"education\":\"bachelor\","
                                + "\"work_years\":\"1_to_3_years\",\"job_intent_learning_route_id\":" + routeId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("小明同学"))
                .andExpect(jsonPath("$.data.education").value("bachelor"))
                .andExpect(jsonPath("$.data.work_years").value("1_to_3_years"))
                .andExpect(jsonPath("$.data.job_intent_name").value(routeName))
                .andExpect(jsonPath("$.data.is_job_intent_off_shelf").value(false));

        // 显式传 null 表示清空
        mvc.perform(patch("/api/v1/student/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"education\":null,\"job_intent_learning_route_id\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.education").value(nullValue()))
                .andExpect(jsonPath("$.data.job_intent_learning_route_id").value(nullValue()))
                .andExpect(jsonPath("$.data.job_intent_name").value(nullValue()));

        mvc.perform(patch("/api/v1/student/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"education\":\"phd\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        // 已下架或不存在的路线不能选
        mvc.perform(patch("/api/v1/student/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"job_intent_learning_route_id\":99999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void avatarUploadAcceptsPngAndRejectsNonImage() throws Exception {
        phone = randomPhone();
        String token = registerWithToken(phone);
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

        mvc.perform(multipart("/api/v1/student/me/avatar")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", png))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.avatar_url").isNotEmpty());

        mvc.perform(multipart("/api/v1/student/me/avatar")
                        .file(new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_INVALID"));
    }

    @Test
    void setPasswordThenChangePasswordWithOldOne() throws Exception {
        phone = randomPhone();
        String token = registerWithToken(phone);
        seedSms("reset_password", phone);

        // 首次设置：需要短信验证码
        mvc.perform(put("/api/v1/student/me/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sms_code\":\"123456\",\"new_password\":\"abc123\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/student/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        String token2 = loginWithPassword(phone, "abc123");
        // 修改：原密码错误
        mvc.perform(put("/api/v1/student/me/password").header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"old_password\":\"wrong123\",\"new_password\":\"xyz789\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OLD_PASSWORD_INCORRECT"));
        // 修改：原密码正确
        mvc.perform(put("/api/v1/student/me/password").header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"old_password\":\"abc123\",\"new_password\":\"xyz789\"}"))
                .andExpect(status().isOk());
        loginWithPassword(phone, "xyz789");
    }

    @Test
    void changePhoneBySmsLogsOutAndMovesAccount() throws Exception {
        phone = randomPhone();
        String token = registerWithToken(phone);
        String newPhone = randomPhone();
        seedSms("change_phone_old", phone);
        seedSms("change_phone_new", newPhone);

        mvc.perform(put("/api/v1/student/me/phone").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"new_phone\":\"" + newPhone + "\",\"new_sms_code\":\"123456\","
                                + "\"old_sms_code\":\"123456\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/student/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM student WHERE phone = ?", Integer.class, phone)).isZero();
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM student WHERE phone = ?", Integer.class, newPhone)).isOne();
        // 账号已迁移到新手机号，清理时按新号删除
        phone = newPhone;

        // 新手机号已注册时拒绝
        String otherPhone = randomPhone();
        registerWithToken(otherPhone);
        try {
            seedSms("login", newPhone);
            String freshToken = JsonPath.read(
                    loginWithSms(newPhone).andReturn().getResponse().getContentAsString(), "$.data.token");
            seedSms("change_phone_old", newPhone);
            seedSms("change_phone_new", otherPhone);
            mvc.perform(put("/api/v1/student/me/phone").header("Authorization", "Bearer " + freshToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"new_phone\":\"" + otherPhone + "\",\"new_sms_code\":\"123456\","
                                    + "\"old_sms_code\":\"123456\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PHONE_ALREADY_REGISTERED"));
        } finally {
            studentMapper.delete(new LambdaQueryWrapper<Student>().eq(Student::getPhone, otherPhone));
        }
    }

    @Test
    void cancellationBlocksProfileChangeAndCanBeUndone() throws Exception {
        phone = randomPhone();
        String token = registerWithToken(phone);
        seedSms("cancel_account", phone);

        mvc.perform(post("/api/v1/student/me/cancellation").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sms_code\":\"123456\",\"confirmed\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.cancellation_effective_at").isNotEmpty());
        // 申请后全设备退出
        mvc.perform(get("/api/v1/student/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        seedSms("login", phone);
        String body = loginWithSms(phone).andReturn().getResponse().getContentAsString();
        String newToken = JsonPath.read(body, "$.data.token");
        org.assertj.core.api.Assertions.assertThat(JsonPath.<String>read(body, "$.data.profile.status"))
                .isEqualTo("cancelling");
        // 注销中不能改资料
        mvc.perform(patch("/api/v1/student/me").header("Authorization", "Bearer " + newToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"改名\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDENT_CANCELLING"));
        // 撤销注销
        mvc.perform(delete("/api/v1/student/me/cancellation").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("normal"))
                .andExpect(jsonPath("$.data.cancellation_effective_at").value(nullValue()));
    }

    private String randomPhone() {
        return "199" + String.format("%08d", ThreadLocalRandom.current().nextInt(100000000));
    }

    private String captcha() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/student/captchas"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.captcha_id");
    }

    private org.springframework.test.web.servlet.ResultActions sendSmsCode(String target, String scene,
                                                                           String captchaId) throws Exception {
        return mvc.perform(post("/api/v1/student/sms-codes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + target + "\",\"scene\":\"" + scene + "\","
                        + "\"captcha_id\":\"" + captchaId + "\",\"captcha_code\":\"1234\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions register(String target) throws Exception {
        return mvc.perform(post("/api/v1/student/registrations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + target + "\",\"sms_code\":\"123456\",\"agreements\":["
                        + "{\"agreement_type\":\"user_agreement\",\"version_no\":1},"
                        + "{\"agreement_type\":\"privacy_policy\",\"version_no\":1}]}"));
    }

    /** 直接注入验证码，避免测试反复触发短信/IP 限流 */
    private void seedSms(String scene, String target) {
        redis.opsForValue().set("account:sms:" + scene + ":" + target, "123456", Duration.ofMinutes(5));
    }

    private String registerWithToken(String target) throws Exception {
        seedSms("register", target);
        MvcResult result = register(target).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private String loginWithPassword(String target, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/student/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login_type\":\"password\",\"phone\":\"" + target + "\","
                                + "\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private org.springframework.test.web.servlet.ResultActions loginWithSms(String target) throws Exception {
        return mvc.perform(post("/api/v1/student/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login_type\":\"sms\",\"phone\":\"" + target + "\",\"sms_code\":\"123456\"}"))
                .andExpect(status().isCreated());
    }
}
