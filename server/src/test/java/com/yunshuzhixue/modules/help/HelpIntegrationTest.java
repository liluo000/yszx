package com.yunshuzhixue.modules.help;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 帮助中心（S-HLP-03）与协议同意（S-HLP-02）：常见问题、联系方式、重新同意与拦截 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HelpIntegrationTest {

    /** 用一个不会与真实数据冲突的版本号，构造“协议已更新”的场景 */
    private static final int NEW_VERSION = 990_001;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;

    private String phone;
    private Long studentId;
    private String token;
    private Long faqId;

    @BeforeAll
    void setUp() throws Exception {
        phone = "137" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        redis.opsForValue().set("account:sms:register:" + phone, "123456", Duration.ofMinutes(5));
        MvcResult registered = mvc.perform(post("/api/v1/student/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"sms_code\":\"123456\",\"agreements\":["
                                + "{\"agreement_type\":\"user_agreement\",\"version_no\":1},"
                                + "{\"agreement_type\":\"privacy_policy\",\"version_no\":1}]}"))
                .andExpect(status().isCreated()).andReturn();
        token = JsonPath.read(registered.getResponse().getContentAsString(), "$.data.token");
        studentId = jdbc.queryForObject("SELECT id FROM student WHERE phone = ?", Long.class, phone);

        // 注册后再发布更高版本，模拟“协议内容更新”
        jdbc.update("INSERT INTO agreement_version (agreement_type, version_no, content,"
                        + " published_by_sys_user_id, published_at) VALUES"
                        + " ('user_agreement', ?, '新版用户协议正文', 1, NOW(3)),"
                        + " ('privacy_policy', ?, '新版隐私政策正文', 1, NOW(3))",
                NEW_VERSION, NEW_VERSION);

        jdbc.update("INSERT INTO faq (question, answer, sort_no) VALUES (?, ?, ?)",
                "测试问题：如何开始学习？", "在首页选择一条学习路线即可。", 9999);
        faqId = jdbc.queryForObject("SELECT id FROM faq WHERE question = ?", Long.class, "测试问题：如何开始学习？");
    }

    @AfterAll
    void cleanUp() {
        jdbc.update("DELETE FROM student_agreement_consent WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM agreement_version WHERE version_no = ?", NEW_VERSION);
        jdbc.update("DELETE FROM faq WHERE id = ?", faqId);
        jdbc.update("DELETE FROM student WHERE id = ?", studentId);
    }

    @Test
    @Order(1)
    void helpCenterReturnsFaqsAndContact() throws Exception {
        mvc.perform(get("/api/v1/student/help"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.faqs[?(@.question == '测试问题：如何开始学习？')]").exists())
                .andExpect(jsonPath("$.data.contact").exists())
                .andExpect(jsonPath("$.data.contact.wechat_qrcode_url").value(nullValue()))
                .andExpect(jsonPath("$.data.contact.email").value(nullValue()));
    }

    @Test
    @Order(2)
    void registrationRecordedAgreementConsent() {
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM student_agreement_consent"
                + " WHERE student_id = ?", Integer.class, studentId);
        assertThat(rows).isEqualTo(2);
    }

    @Test
    @Order(3)
    void consentRequiredBlocksOtherEndpointsUntilAgreed() throws Exception {
        // 协议更新后，本人信息里的待同意项被带出
        mvc.perform(get("/api/v1/student/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending_agreements.length()").value(2))
                .andExpect(jsonPath("$.data.pending_agreements[0].version_no").value(NEW_VERSION));
        // 其他学员端接口被拦截
        mvc.perform(get("/api/v1/student/wrong-questions").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AGREEMENT_CONSENT_REQUIRED"));
        // 版本不是最新时拒绝
        mvc.perform(post("/api/v1/student/agreement-consents").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreements\":[{\"agreement_type\":\"user_agreement\",\"version_no\":1}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AGREEMENT_VERSION_OUTDATED"));
        // 提交最新版本后解锁
        mvc.perform(post("/api/v1/student/agreement-consents").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreements\":["
                                + "{\"agreement_type\":\"user_agreement\",\"version_no\":" + NEW_VERSION + "},"
                                + "{\"agreement_type\":\"privacy_policy\",\"version_no\":" + NEW_VERSION + "}]}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/student/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending_agreements").isEmpty());
        mvc.perform(get("/api/v1/student/wrong-questions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
