package com.yunshuzhixue.modules.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.yunshuzhixue.modules.safety.service.SensitiveTextFilterService;
import java.util.Map;
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

/** AI 面试（S-IVW-01~08）与面试报告（S-RPT-01~03）端到端闭环 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterviewIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SensitiveTextFilterService filter;

    private String phone;
    private Long studentId;
    private String token;
    private long routeId;
    private long stageId;

    @BeforeAll
    void setUp() throws Exception {
        phone = "136" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        MvcResult captcha = mvc.perform(post("/api/v1/student/captchas"))
                .andExpect(status().isCreated()).andReturn();
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
                .andExpect(status().isCreated()).andReturn();
        token = JsonPath.read(registered.getResponse().getContentAsString(), "$.data.token");
        studentId = jdbc.queryForObject("SELECT id FROM student WHERE phone = ?", Long.class, phone);

        Map<String, Object> stage = jdbc.queryForMap("SELECT sc.learning_route_id AS route_id,"
                + " sc.route_stage_id AS stage_id FROM stage_course sc"
                + " JOIN interview_question q ON q.course_id = sc.course_id AND q.status = 'on_shelf'"
                + " JOIN learning_route r ON r.id = sc.learning_route_id AND r.status = 'on_shelf'"
                + " GROUP BY sc.route_stage_id, sc.learning_route_id"
                + " HAVING COUNT(DISTINCT q.id) >= 5 ORDER BY sc.route_stage_id LIMIT 1");
        routeId = ((Number) stage.get("route_id")).longValue();
        stageId = ((Number) stage.get("stage_id")).longValue();
    }

    @AfterEach
    void resetInterviewData() {
        jdbc.update("DELETE FROM interview_message WHERE interview_session_id IN"
                + " (SELECT id FROM interview_session WHERE student_id = ?)", studentId);
        jdbc.update("DELETE FROM interview_session_question WHERE interview_session_id IN"
                + " (SELECT id FROM interview_session WHERE student_id = ?)", studentId);
        jdbc.update("DELETE FROM interview_session_stage WHERE interview_session_id IN"
                + " (SELECT id FROM interview_session WHERE student_id = ?)", studentId);
        jdbc.update("DELETE FROM interview_report WHERE interview_session_id IN"
                + " (SELECT id FROM interview_session WHERE student_id = ?)", studentId);
        jdbc.update("DELETE FROM interview_session WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM ai_quota_ledger WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM ai_quota_daily WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM sensitive_word_hit_log WHERE student_id = ?", studentId);
    }

    @AfterAll
    void cleanUp() {
        resetInterviewData();
        jdbc.update("DELETE FROM student_message WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM student WHERE id = ?", studentId);
    }

    @Test
    void optionsAndFullTextInterviewGenerateReport() throws Exception {
        mvc.perform(get("/api/v1/student/interviews/options")
                        .header("Authorization", "Bearer " + token)
                        .param("learning_route_id", String.valueOf(routeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interview_remaining").value(2))
                .andExpect(jsonPath("$.data.main_question_count").value(5))
                .andExpect(jsonPath("$.data.stages").isNotEmpty());

        long sessionId = startSession();
        String claim = claim(sessionId);

        // 长回答跳过追问，每题一次提交；答完最后一题自动结束并生成报告
        String longAnswer = "我在项目中负责核心模块设计，先梳理需求与边界，再按分层架构落地，"
                + "通过单元测试与压测验证效果，并持续复盘优化。具体包括接口幂等、缓存一致性、异常降级与监控告警。";
        for (int i = 0; i < 20; i++) {
            MvcResult result = mvc.perform(post("/api/v1/student/interviews/" + sessionId + "/answers")
                            .header("Authorization", "Bearer " + token)
                            .header("X-Device-Claim", claim)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"" + longAnswer + "\"}"))
                    .andExpect(status().isOk()).andReturn();
            String body = result.getResponse().getContentAsString();
            assertThat(body).contains("message_start");
            Integer status = jdbc.queryForObject("SELECT COUNT(*) FROM interview_session"
                    + " WHERE id = ? AND status = 'ended'", Integer.class, sessionId);
            if (status != null && status > 0) {
                break;
            }
        }

        mvc.perform(get("/api/v1/student/interviews/" + sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ended"))
                .andExpect(jsonPath("$.data.end_reason").value("completed"));

        mvc.perform(get("/api/v1/student/interviews/" + sessionId + "/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("generated"))
                .andExpect(jsonPath("$.data.total_score").isNumber())
                .andExpect(jsonPath("$.data.questions").isNotEmpty())
                .andExpect(jsonPath("$.data.grade").isString());

        mvc.perform(get("/api/v1/student/interviews")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].record_status").value("ended"))
                .andExpect(jsonPath("$.data.items[0].total_score").isNumber());
    }

    @Test
    void sensitiveWordBlocksAnswerWithoutWritingMessage() throws Exception {
        String word = "面试禁词" + ThreadLocalRandom.current().nextInt(10000);
        jdbc.update("INSERT INTO sensitive_word (word, created_by_sys_user_id) VALUES (?, 1)", word);
        filter.refresh();
        try {
            long sessionId = startSession();
            String claim = claim(sessionId);
            int before = messageCount(sessionId);
            mvc.perform(post("/api/v1/student/interviews/" + sessionId + "/answers")
                            .header("Authorization", "Bearer " + token)
                            .header("X-Device-Claim", claim)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"这是一段包含" + word + "的回答\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("SENSITIVE_WORD_BLOCKED"));
            assertThat(messageCount(sessionId)).isEqualTo(before);
            Integer logged = jdbc.queryForObject("SELECT COUNT(*) FROM sensitive_word_hit_log"
                    + " WHERE student_id = ? AND source = 'student'", Integer.class, studentId);
            assertThat(logged).isPositive();
        } finally {
            jdbc.update("DELETE FROM sensitive_word WHERE word = ?", word);
            filter.refresh();
        }
    }

    @Test
    void terminateChargesQuotaAndRegenerateOnlyWhenFailed() throws Exception {
        long sessionId = startSession();
        String claim = claim(sessionId);

        mvc.perform(get("/api/v1/student/interviews/options")
                        .header("Authorization", "Bearer " + token)
                        .param("learning_route_id", String.valueOf(routeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interview_remaining").value(1));

        mvc.perform(post("/api/v1/student/interviews/" + sessionId + "/termination")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Device-Claim", claim))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.end_reason").value("student_ended"));

        mvc.perform(get("/api/v1/student/interviews/" + sessionId + "/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("generated"));

        // 生成成功时不能重新生成
        mvc.perform(post("/api/v1/student/interviews/" + sessionId + "/report/regeneration")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));

        jdbc.update("UPDATE interview_report SET status = 'failed', retry_count = 3,"
                + " total_score = NULL, grade = NULL WHERE interview_session_id = ?", sessionId);
        mvc.perform(post("/api/v1/student/interviews/" + sessionId + "/report/regeneration")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("generated"));
    }

    @Test
    void wsTicketIsOneTime() throws Exception {
        MvcResult issued = mvc.perform(post("/api/v1/student/ws-tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"voice_input\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.expires_in").value(30))
                .andReturn();
        assertThat(JsonPath.<String>read(issued.getResponse().getContentAsString(), "$.data.ticket")).isNotBlank();
        mvc.perform(post("/api/v1/student/ws-tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"unknown\"}"))
                .andExpect(status().isBadRequest());
    }

    private long startSession() throws Exception {
        MvcResult started = mvc.perform(post("/api/v1/student/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"learning_route_id\":" + routeId + ",\"scope_type\":\"selected_stages\","
                                + "\"route_stage_ids\":[" + stageId + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("in_progress"))
                .andExpect(jsonPath("$.data.messages").isNotEmpty())
                .andReturn();
        return ((Number) JsonPath.read(started.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private String claim(long sessionId) throws Exception {
        MvcResult claimed = mvc.perform(post("/api/v1/student/interviews/" + sessionId + "/device-claims")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(claimed.getResponse().getContentAsString(), "$.data.device_claim");
    }

    private int messageCount(long sessionId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM interview_message"
                + " WHERE interview_session_id = ?", Integer.class, sessionId);
        return count == null ? 0 : count;
    }
}
