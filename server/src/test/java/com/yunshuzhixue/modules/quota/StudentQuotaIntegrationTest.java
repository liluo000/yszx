package com.yunshuzhixue.modules.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.yunshuzhixue.modules.quota.api.AiQuotaApi;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** AI 次数（S-QTA-01）：查询视图、扣减幂等与次数不足 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StudentQuotaIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AiQuotaApi quota;

    private String phone;
    private Long studentId;
    private String token;

    @BeforeAll
    void createStudent() throws Exception {
        phone = "137" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        MvcResult captcha = mvc.perform(post("/api/v1/student/captchas")).andExpect(status().isCreated()).andReturn();
        String captchaId = JsonPath.read(captcha.getResponse().getContentAsString(), "$.data.captcha_id");
        mvc.perform(post("/api/v1/student/sms-codes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"scene\":\"register\",\"captcha_id\":\""
                                + captchaId + "\",\"captcha_code\":\"1234\"}"))
                .andExpect(status().isCreated());
        MvcResult registered = mvc.perform(post("/api/v1/student/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"sms_code\":\"123456\",\"agreements\":["
                                + "{\"agreement_type\":\"user_agreement\",\"version_no\":0},"
                                + "{\"agreement_type\":\"privacy_policy\",\"version_no\":0}]}"))
                .andExpect(status().isCreated())
                .andReturn();
        token = JsonPath.read(registered.getResponse().getContentAsString(), "$.data.token");
        studentId = jdbc.queryForObject("SELECT id FROM student WHERE phone = ?", Long.class, phone);
    }

    @AfterEach
    void resetQuota() {
        jdbc.update("DELETE FROM ai_quota_ledger WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM ai_quota_daily WHERE student_id = ?", studentId);
    }

    @AfterAll
    void cleanUp() {
        resetQuota();
        jdbc.update("DELETE FROM student WHERE id = ?", studentId);
    }

    @Test
    void currentQuotaReturnsLimitsAndRemaining() throws Exception {
        mvc.perform(get("/api/v1/student/ai-quota").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quota_date").isNotEmpty())
                .andExpect(jsonPath("$.data.interview_limit").value(2))
                .andExpect(jsonPath("$.data.scoring_limit").value(20))
                .andExpect(jsonPath("$.data.interview_remaining").value(2))
                .andExpect(jsonPath("$.data.scoring_remaining").value(20));
        mvc.perform(get("/api/v1/student/ai-quota")).andExpect(status().isUnauthorized());
    }

    @Test
    void chargeIsIdempotentAndRejectsWhenExhausted() {
        long businessId = 9_000_000L + ThreadLocalRandom.current().nextInt(100_000);
        int limit = quota.current(studentId).scoringLimit();
        // 同一业务重复扣减只算一次
        assertThat(quota.charge(studentId, "scoring", "practice_answer", businessId)).isTrue();
        assertThat(quota.charge(studentId, "scoring", "practice_answer", businessId)).isTrue();
        assertThat(quota.current(studentId).scoringRemaining()).isEqualTo(limit - 1);

        for (int i = 1; i < limit; i++) {
            assertThat(quota.charge(studentId, "scoring", "practice_answer", businessId + i)).isTrue();
        }
        assertThat(quota.current(studentId).scoringRemaining()).isZero();
        // 次数用尽：返回 false 且不留下流水
        long rejected = businessId + limit;
        assertThat(quota.charge(studentId, "scoring", "practice_answer", rejected)).isFalse();
        assertThat(quota.charge(studentId, "scoring", "practice_answer", rejected)).isFalse();
    }
}
