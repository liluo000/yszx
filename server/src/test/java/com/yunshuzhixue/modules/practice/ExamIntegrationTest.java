package com.yunshuzhixue.modules.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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

/** 模拟考试（S-EXM-01~08）：开考、设备接管、逐题保存、交卷判分、放弃与重新评分 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExamIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private String phone;
    private Long studentId;
    private String token;
    private long routeId;
    private long stageId;

    @BeforeAll
    void setUp() throws Exception {
        phone = "138" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
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

        // 题量充足且含简答题的已上架阶段
        Map<String, Object> stage = jdbc.queryForMap("SELECT sc.learning_route_id AS route_id,"
                + " sc.route_stage_id AS stage_id FROM stage_course sc"
                + " JOIN practice_question q ON q.course_id = sc.course_id AND q.status = 'on_shelf'"
                + " JOIN learning_route r ON r.id = sc.learning_route_id AND r.status = 'on_shelf'"
                + " GROUP BY sc.route_stage_id, sc.learning_route_id"
                + " HAVING COUNT(DISTINCT q.id) >= 10 AND SUM(q.question_type = 'short_answer') > 0"
                + " ORDER BY sc.route_stage_id LIMIT 1");
        routeId = ((Number) stage.get("route_id")).longValue();
        stageId = ((Number) stage.get("stage_id")).longValue();
    }

    /** 每个用例独立：考试场次、错题与次数流水不互相影响 */
    @AfterEach
    void resetExamData() {
        jdbc.update("DELETE FROM exam_session_question WHERE exam_session_id IN"
                + " (SELECT id FROM exam_session WHERE student_id = ?)", studentId);
        jdbc.update("DELETE FROM exam_session WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM wrong_question WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM ai_quota_ledger WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM ai_quota_daily WHERE student_id = ?", studentId);
    }

    @AfterAll
    void cleanUp() {
        jdbc.update("DELETE FROM exam_session_question WHERE exam_session_id IN"
                + " (SELECT id FROM exam_session WHERE student_id = ?)", studentId);
        jdbc.update("DELETE FROM exam_session WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM wrong_question WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM ai_quota_ledger WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM ai_quota_daily WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM student WHERE id = ?", studentId);
    }

    private String startBody() {
        return "{\"learning_route_id\":" + routeId + ",\"route_stage_id\":" + stageId + "}";
    }

    @Test
    void examLifecycle() throws Exception {
        MvcResult started = mvc.perform(post("/api/v1/student/exams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(startBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("in_progress"))
                .andExpect(jsonPath("$.data.has_short_answer").value(true))
                .andExpect(jsonPath("$.data.questions").isNotEmpty())
                .andExpect(jsonPath("$.data.questions[0].judge_status").doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].correct_answer").doesNotExist())
                .andExpect(jsonPath("$.data.server_time").isNotEmpty())
                .andReturn();
        String startJson = started.getResponse().getContentAsString();
        long examId = ((Number) JsonPath.read(startJson, "$.data.id")).longValue();
        int questionCount = ((Number) JsonPath.read(startJson, "$.data.questions.length()")).intValue();
        assertThat(questionCount).isBetween(1, 20);
        assertThat(jdbc.queryForObject("SELECT scoring_quota_status FROM exam_session WHERE id = ?",
                String.class, examId)).isEqualTo("reserved");

        // 已有进行中的考试：409 且 errors 带场次 ID
        mvc.perform(post("/api/v1/student/exams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(startBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_IN_PROGRESS"))
                .andExpect(jsonPath("$.errors[0]").value(examId));
        mvc.perform(get("/api/v1/student/exams/in-progress")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.exam.id").value(examId));

        // 设备接管：旧凭证失效
        MvcResult claimed = mvc.perform(post("/api/v1/student/exams/" + examId + "/device-claims")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();
        String claim = JsonPath.read(claimed.getResponse().getContentAsString(), "$.data.device_claim");

        int firstSeq = ((Number) JsonPath.read(startJson, "$.data.questions[0].seq_no")).intValue();
        mvc.perform(put("/api/v1/student/exams/" + examId + "/answers/" + firstSeq)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Device-Claim", "stale-claim")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"student_answer\":[\"Z\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEVICE_TAKEN_OVER"));
        mvc.perform(put("/api/v1/student/exams/" + examId + "/answers/" + firstSeq)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Device-Claim", claim)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"student_answer\":[\"Z\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answered_at").isNotEmpty());

        // 交卷判分：百分制总分、各题型得分、逐题结果
        MvcResult submitted = mvc.perform(post("/api/v1/student/exams/" + examId + "/submission")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Device-Claim", claim))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("graded"))
                .andExpect(jsonPath("$.data.score").isNumber())
                .andExpect(jsonPath("$.data.type_scores").isNotEmpty())
                .andExpect(jsonPath("$.data.questions[0].correct_answer").exists())
                .andReturn();
        double score = ((Number) JsonPath.read(submitted.getResponse().getContentAsString(),
                "$.data.score")).doubleValue();
        assertThat(score).isBetween(0.0, 100.0);
        assertThat(jdbc.queryForObject("SELECT scoring_quota_status FROM exam_session WHERE id = ?",
                String.class, examId)).isEqualTo("consumed");
        mvc.perform(get("/api/v1/student/exams/" + examId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.status").value("graded"));

        // 已出成绩不能放弃；重新评分仅 scoring_pending 可用
        mvc.perform(post("/api/v1/student/exams/" + examId + "/abandonment")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Device-Claim", claim))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_IN_PROGRESS"));
        mvc.perform(post("/api/v1/student/exams/" + examId + "/rescoring")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_STATE_INVALID"));
    }

    @Test
    void abandonReleasesReservedQuotaAndRescoringFinishesPendingExam() throws Exception {
        MvcResult started = mvc.perform(post("/api/v1/student/exams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(startBody()))
                .andExpect(status().isCreated()).andReturn();
        long examId = ((Number) JsonPath.read(started.getResponse().getContentAsString(),
                "$.data.id")).longValue();
        String claim = JsonPath.read(mvc.perform(post("/api/v1/student/exams/" + examId + "/device-claims")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                "$.data.device_claim");

        mvc.perform(post("/api/v1/student/exams/" + examId + "/abandonment")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Device-Claim", claim))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT status FROM exam_session WHERE id = ?", String.class, examId))
                .isEqualTo("abandoned");
        assertThat(jdbc.queryForObject("SELECT scoring_quota_status FROM exam_session WHERE id = ?",
                String.class, examId)).isEqualTo("released");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_quota_ledger WHERE business_type = 'exam_session'"
                + " AND business_id = ? AND action = 'reserve'", Integer.class, examId)).isZero();

        // 模拟评分失败后的重新评分
        jdbc.update("UPDATE exam_session SET status = 'scoring_pending' WHERE id = ?", examId);
        jdbc.update("UPDATE exam_session_question SET judge_status = 'failed' WHERE exam_session_id = ?"
                + " AND question_type = 'short_answer' LIMIT 1", examId);
        mvc.perform(post("/api/v1/student/exams/" + examId + "/rescoring")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("graded"))
                .andExpect(jsonPath("$.data.score").isNumber());
    }

    @Test
    void insufficientQuestionsAreRejected() throws Exception {
        Map<String, Object> small = jdbc.queryForMap("SELECT sc.learning_route_id AS route_id,"
                + " sc.route_stage_id AS stage_id FROM stage_course sc"
                + " JOIN practice_question q ON q.course_id = sc.course_id AND q.status = 'on_shelf'"
                + " JOIN learning_route r ON r.id = sc.learning_route_id AND r.status = 'on_shelf'"
                + " GROUP BY sc.route_stage_id, sc.learning_route_id"
                + " HAVING COUNT(DISTINCT q.id) < 10 ORDER BY sc.route_stage_id LIMIT 1");
        long smallRoute = ((Number) small.get("route_id")).longValue();
        long smallStage = ((Number) small.get("stage_id")).longValue();
        mvc.perform(post("/api/v1/student/exams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"learning_route_id\":" + smallRoute
                                + ",\"route_stage_id\":" + smallStage + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_QUESTIONS"));
    }
}
