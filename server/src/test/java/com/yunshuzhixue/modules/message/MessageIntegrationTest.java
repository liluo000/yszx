package com.yunshuzhixue.modules.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
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

/** 消息中心（S-MSG-01~04）：列表、未读数、标记已读与全部已读 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessageIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private static String phone;
    private static Long studentId;
    private static String token;

    @BeforeAll
    void setUp() throws Exception {
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

    @AfterAll
    void cleanUp() {
        jdbc.update("DELETE FROM student_message WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM student WHERE id = ?", studentId);
    }

    @Test
    void messageCenterLifecycle() throws Exception {
        jdbc.update("INSERT INTO student_message (student_id, message_type, title, content, is_read)"
                        + " VALUES (?, 'feedback_result', '反馈已处理', '处理说明', 0)",
                studentId);
        jdbc.update("INSERT INTO student_message (student_id, message_type, title, content, is_read)"
                        + " VALUES (?, 'agreement_updated', '协议已更新', '请重新同意', 0)",
                studentId);

        MvcResult list = mvc.perform(get("/api/v1/student/messages")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "1").param("page_size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].is_read").value(false))
                .andReturn();
        long firstId = ((Number) JsonPath.read(list.getResponse().getContentAsString(),
                "$.data.items[0].id")).longValue();

        mvc.perform(get("/api/v1/student/messages/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.unread_count").value(2));
        mvc.perform(put("/api/v1/student/messages/" + firstId + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        // 重复标记已读幂等
        mvc.perform(put("/api/v1/student/messages/" + firstId + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/student/messages/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.unread_count").value(1));
        mvc.perform(get("/api/v1/student/messages")
                        .header("Authorization", "Bearer " + token).param("is_read", "true"))
                .andExpect(jsonPath("$.data.total").value(1));

        mvc.perform(put("/api/v1/student/messages/read-all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/student/messages/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.unread_count").value(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM student_message"
                + " WHERE student_id = ? AND read_at IS NOT NULL", Long.class, studentId)).isEqualTo(2);

        mvc.perform(put("/api/v1/student/messages/99999999/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/student/messages")).andExpect(status().isUnauthorized());
    }
}
