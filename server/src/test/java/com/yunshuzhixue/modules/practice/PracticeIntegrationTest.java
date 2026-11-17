package com.yunshuzhixue.modules.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
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

/** 顺序练习闭环（S-PRC-01~05）：判分、续答、重新练习、作答事件驱动错题收录与刷题记录 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PracticeIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private String phone;
    private Long studentId;
    private String token;
    private Long courseId;

    @BeforeAll
    void setUp() throws Exception {
        phone = "136" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
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

        // 至少两道已上架客观题的课程
        courseId = jdbc.queryForObject("SELECT course_id FROM practice_question"
                + " WHERE status = 'on_shelf' AND question_type IN ('single_choice', 'multiple_choice')"
                + " GROUP BY course_id HAVING COUNT(*) >= 2 ORDER BY course_id LIMIT 1", Long.class);
    }

    @AfterEach
    void resetSessions() {
        jdbc.update("DELETE FROM practice_answer WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM sequential_question_result WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM practice_session WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM wrong_question WHERE student_id = ?", studentId);
    }

    @AfterAll
    void cleanUp() {
        resetSessions();
        jdbc.update("DELETE FROM student WHERE id = ?", studentId);
    }

    @Test
    void correctAndWrongAnswersDriveWrongQuestionBook() throws Exception {
        MvcResult started = start(false);
        String body = started.getResponse().getContentAsString();
        long sessionId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        long firstId = ((Number) JsonPath.read(body, "$.data.next_question.id")).longValue();

        // 第一题答对：不进错题本，结果立即返回
        String correct = jdbc.queryForObject("SELECT correct_answer FROM practice_question WHERE id = ?",
                String.class, firstId);
        mvc.perform(post("/api/v1/student/practice-sessions/" + sessionId + "/answers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody(firstId, correct)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.result.is_correct").value(true))
                .andExpect(jsonPath("$.data.result.judge_method").value("auto"))
                .andExpect(jsonPath("$.data.result.correct_answer").isArray())
                .andExpect(jsonPath("$.data.next_question").isNotEmpty());
        assertThat(wrongCount(firstId)).isZero();

        // 第二题答错：作答事件自动收录错题
        MvcResult progressed = mvc.perform(get("/api/v1/student/practice-sessions/" + sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answered_count").value(1))
                .andReturn();
        long secondId = ((Number) JsonPath.read(progressed.getResponse().getContentAsString(),
                "$.data.next_question.id")).longValue();
        mvc.perform(post("/api/v1/student/practice-sessions/" + sessionId + "/answers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody(secondId, "[\"Z\"]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.result.is_correct").value(false));
        assertThat(wrongCount(secondId)).isEqualTo(1);
        mvc.perform(get("/api/v1/student/wrong-questions").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void restartStartsNewRoundAndKeepsPreviousAsUnfinished() throws Exception {
        MvcResult started = start(false);
        String body = started.getResponse().getContentAsString();
        long firstSession = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        long questionId = ((Number) JsonPath.read(body, "$.data.next_question.id")).longValue();
        mvc.perform(post("/api/v1/student/practice-sessions/" + firstSession + "/answers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody(questionId, "[\"Z\"]")))
                .andExpect(status().isCreated());

        MvcResult restarted = start(true);
        long secondSession = ((Number) JsonPath.read(restarted.getResponse().getContentAsString(),
                "$.data.id")).longValue();
        assertThat(secondSession).isNotEqualTo(firstSession);
        mvc.perform(get("/api/v1/student/practice-sessions/" + secondSession)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.answered_count").value(0));
        assertThat(jdbc.queryForObject("SELECT status FROM practice_session WHERE id = ?", String.class,
                firstSession)).isEqualTo("unfinished");
    }

    @Test
    void recordsAndAnswerDetailsAreAvailable() throws Exception {
        MvcResult started = start(false);
        long sessionId = ((Number) JsonPath.read(started.getResponse().getContentAsString(),
                "$.data.id")).longValue();
        long questionId = ((Number) JsonPath.read(started.getResponse().getContentAsString(),
                "$.data.next_question.id")).longValue();
        mvc.perform(post("/api/v1/student/practice-sessions/" + sessionId + "/answers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody(questionId, "[\"Z\"]")))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/student/practice-sessions/" + sessionId + "/answers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].is_correct").value(false))
                .andExpect(jsonPath("$.data.items[0].question_snapshot.stem").isNotEmpty());
        mvc.perform(get("/api/v1/student/practice-records").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].practice_mode").value("sequential"));
    }

    /** 需求 5.4.6：同一道题重复答错不重复收录，累计答错次数加 1 */
    @Test
    void repeatedWrongAnswersIncreaseWrongCount() throws Exception {
        MvcResult started = start(false);
        long questionId = ((Number) JsonPath.read(started.getResponse().getContentAsString(),
                "$.data.next_question.id")).longValue();
        long firstSession = ((Number) JsonPath.read(started.getResponse().getContentAsString(),
                "$.data.id")).longValue();
        submitWrong(firstSession, questionId);
        assertThat(wrongCount(questionId)).isEqualTo(1);

        MvcResult restarted = start(true);
        long secondSession = ((Number) JsonPath.read(restarted.getResponse().getContentAsString(),
                "$.data.id")).longValue();
        submitWrong(secondSession, questionId);
        assertThat(wrongCount(questionId)).isEqualTo(2);
        mvc.perform(get("/api/v1/student/wrong-questions").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/v1/student/practice-records")).andExpect(status().isUnauthorized());
    }

    /** S-PRC-01 random：按阶段抽题、写 planned_question_ids，重复发起把旧轮标记未完成 */
    @Test
    void randomPracticeDrawsQuestionsFromStage() throws Exception {
        Map<String, Object> stage = jdbc.queryForMap("SELECT rs.learning_route_id AS route_id, rs.id AS stage_id"
                + " FROM route_stage rs JOIN stage_course sc ON sc.route_stage_id = rs.id"
                + " JOIN practice_question q ON q.course_id = sc.course_id AND q.status = 'on_shelf'"
                + " JOIN learning_route r ON r.id = rs.learning_route_id AND r.status = 'on_shelf'"
                + " GROUP BY rs.id, rs.learning_route_id"
                + " HAVING COUNT(DISTINCT q.id) >= 5 ORDER BY rs.id LIMIT 1");
        long routeId = ((Number) stage.get("route_id")).longValue();
        long stageId = ((Number) stage.get("stage_id")).longValue();
        String body = "{\"practice_mode\":\"random\",\"learning_route_id\":" + routeId
                + ",\"route_stage_id\":" + stageId + "}";

        MvcResult first = mvc.perform(post("/api/v1/student/practice-sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.practice_mode").value("random"))
                .andExpect(jsonPath("$.data.next_question").isNotEmpty())
                .andReturn();
        String firstJson = first.getResponse().getContentAsString();
        long firstSession = ((Number) JsonPath.read(firstJson, "$.data.id")).longValue();
        int count = ((Number) JsonPath.read(firstJson, "$.data.question_count")).intValue();
        assertThat(count).isBetween(5, 10);
        assertThat(jdbc.queryForObject("SELECT planned_question_ids FROM practice_session WHERE id = ?",
                String.class, firstSession)).isNotBlank();

        // 进度接口同样按 planned ids 返回下一题
        long firstQuestion = ((Number) JsonPath.read(firstJson, "$.data.next_question.id")).longValue();
        mvc.perform(get("/api/v1/student/practice-sessions/" + firstSession)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.next_question.id").value(firstQuestion));
        mvc.perform(post("/api/v1/student/practice-sessions/" + firstSession + "/answers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody(firstQuestion, "[\"Z\"]")))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/student/practice-sessions/" + firstSession)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.answered_count").value(1));

        // 再次发起：旧轮 unfinished，新轮 in_progress
        MvcResult second = mvc.perform(post("/api/v1/student/practice-sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        long secondSession = ((Number) JsonPath.read(second.getResponse().getContentAsString(),
                "$.data.id")).longValue();
        assertThat(secondSession).isNotEqualTo(firstSession);
        assertThat(jdbc.queryForObject("SELECT status FROM practice_session WHERE id = ?", String.class,
                firstSession)).isEqualTo("unfinished");

        // 阶段不属于路线时 404
        mvc.perform(post("/api/v1/student/practice-sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"practice_mode\":\"random\",\"learning_route_id\":" + routeId
                                + ",\"route_stage_id\":99999999}"))
                .andExpect(status().isNotFound());
    }

    /** 需求 5.5 错题重做：按错题记录 ID 发起，答对后移出错题本，且不进入刷题记录 */
    @Test
    void wrongRedoRemovesQuestionWhenAnsweredCorrectly() throws Exception {
        // 先答错一题，产生错题记录
        MvcResult started = start(false);
        String body = started.getResponse().getContentAsString();
        long sessionId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        long questionId = ((Number) JsonPath.read(body, "$.data.next_question.id")).longValue();
        mvc.perform(post("/api/v1/student/practice-sessions/" + sessionId + "/answers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody(questionId, "[\"Z\"]")))
                .andExpect(status().isCreated());
        Long recordId = jdbc.queryForObject(
                "SELECT id FROM wrong_question WHERE student_id = ? AND practice_question_id = ?",
                Long.class, studentId, questionId);
        long sequentialTotal = jdbc.queryForObject(
                "SELECT COUNT(*) FROM practice_session WHERE student_id = ? AND practice_mode = 'sequential'",
                Long.class, studentId);

        // 用错题记录 ID 发起重做
        MvcResult redo = mvc.perform(post("/api/v1/student/practice-sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"practice_mode\":\"wrong_redo\",\"wrong_question_ids\":[" + recordId + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.practice_mode").value("wrong_redo"))
                .andExpect(jsonPath("$.data.question_count").value(1))
                .andExpect(jsonPath("$.data.next_question.id").value(questionId))
                .andReturn();

        long redoSession = ((Number) JsonPath.read(redo.getResponse().getContentAsString(),
                "$.data.id")).longValue();
        String correct = jdbc.queryForObject("SELECT correct_answer FROM practice_question WHERE id = ?",
                String.class, questionId);
        mvc.perform(post("/api/v1/student/practice-sessions/" + redoSession + "/answers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody(questionId, correct)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.result.is_correct").value(true))
                .andExpect(jsonPath("$.data.session_status").value("completed"))
                .andExpect(jsonPath("$.data.next_question").doesNotExist());

        // 答对后移出错题本；错题重做轮次不进入刷题记录
        assertThat(wrongCount(questionId)).isZero();
        mvc.perform(get("/api/v1/student/wrong-questions").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/student/practice-records").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(sequentialTotal));

        // 再次重做同一记录应 404（已移出）
        mvc.perform(post("/api/v1/student/practice-sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"practice_mode\":\"wrong_redo\",\"wrong_question_ids\":[" + recordId + "]}"))
                .andExpect(status().isNotFound());
    }

    private void submitWrong(long sessionId, long questionId) throws Exception {
        mvc.perform(post("/api/v1/student/practice-sessions/" + sessionId + "/answers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerBody(questionId, "[\"Z\"]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.result.is_correct").value(false));
    }

    private MvcResult start(boolean restart) throws Exception {
        return mvc.perform(post("/api/v1/student/practice-sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"practice_mode\":\"sequential\",\"course_id\":" + courseId
                                + ",\"restart\":" + restart + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.next_question").isNotEmpty())
                .andReturn();
    }

    private static String answerBody(long questionId, String answerJson) {
        return "{\"practice_question_id\":" + questionId + ",\"student_answer\":" + answerJson + "}";
    }

    private int wrongCount(long questionId) {
        List<Integer> rows = jdbc.queryForList("SELECT wrong_count FROM wrong_question"
                + " WHERE student_id = ? AND practice_question_id = ?", Integer.class, studentId, questionId);
        return rows.isEmpty() ? 0 : rows.get(0);
    }
}
