package com.yunshuzhixue.modules.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 学员账号核心闭环（S-ACC-01~05、07）：图形验证、短信验证码、注册、登录、本人信息、退出 */
@SpringBootTest
@AutoConfigureMockMvc
class StudentAccountIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private StudentMapper studentMapper;
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
}
