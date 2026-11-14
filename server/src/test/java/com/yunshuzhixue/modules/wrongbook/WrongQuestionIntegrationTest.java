package com.yunshuzhixue.modules.wrongbook;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 错题本（S-WRG-01、02）：列表字段与筛选、下架题不可重做、手动移除与越权保护。
 * 账号通过注册接口建立（Sa-Token 上下文只在请求线程内可用），整个测试类共用一个学员。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WrongQuestionIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private String phone;
    private Long studentId;
    private String token;
    private Long courseId;
    private Long offShelfQuestionId;
    private Long onShelfWrongId;
    private Long offShelfWrongId;

    @BeforeAll
    void createStudent() throws Exception {
        phone = "139" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
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

        Map<String, Object> source = jdbc.queryForMap("SELECT course_id FROM practice_question"
                + " WHERE question_type = 'single_choice' AND status = 'on_shelf' LIMIT 1");
        courseId = ((Number) source.get("course_id")).longValue();
    }

    @AfterAll
    void dropStudent() {
        jdbc.update("DELETE FROM wrong_question WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM student WHERE id = ?", studentId);
    }

    @BeforeEach
    void prepareWrongQuestions() {
        jdbc.update("INSERT INTO practice_question (course_id, question_type, stem, options, correct_answer,"
                + " sort_no, status) VALUES (?, 'single_choice', '已下架题目：以下哪个是集合？',"
                + " '[{\"key\":\"A\",\"text\":\"List\"},{\"key\":\"B\",\"text\":\"Set\"}]', '[\"B\"]', 9999,"
                + " 'off_shelf')", courseId);
        offShelfQuestionId = jdbc.queryForObject("SELECT MAX(id) FROM practice_question", Long.class);

        Long onShelfQuestionId = jdbc.queryForObject("SELECT id FROM practice_question WHERE course_id = ?"
                + " AND status = 'on_shelf' AND question_type = 'single_choice' ORDER BY id LIMIT 1", Long.class,
                courseId);
        onShelfWrongId = insertWrong(onShelfQuestionId, 3);
        offShelfWrongId = insertWrong(offShelfQuestionId, 1);
    }

    @AfterEach
    void clearWrongQuestions() {
        jdbc.update("DELETE FROM wrong_question WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM practice_question WHERE id = ?", offShelfQuestionId);
    }

    @Test
    void listReturnsLatestQuestionContentAndRedoableFlag() throws Exception {
        mvc.perform(get("/api/v1/student/wrong-questions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(offShelfWrongId))
                .andExpect(jsonPath("$.data.items[0].question_type").value("single_choice"))
                .andExpect(jsonPath("$.data.items[0].stem").value("已下架题目：以下哪个是集合？"))
                .andExpect(jsonPath("$.data.items[0].is_redoable").value(false))
                .andExpect(jsonPath("$.data.items[1].is_redoable").value(true))
                .andExpect(jsonPath("$.data.items[1].wrong_count").value(3))
                .andExpect(jsonPath("$.data.items[1].last_student_answer[0]").value("A"))
                .andExpect(jsonPath("$.data.items[1].options[0].key").isNotEmpty())
                .andExpect(jsonPath("$.data.items[1].correct_answer[0]").isNotEmpty())
                .andExpect(jsonPath("$.data.filters.courses[0].id").value(courseId))
                .andExpect(jsonPath("$.data.filters.routes").isArray());
    }

    @Test
    void filterByCourseAndTypeAndEmptySubject() throws Exception {
        mvc.perform(get("/api/v1/student/wrong-questions")
                        .param("course_id", String.valueOf(courseId))
                        .param("question_type", "single_choice")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        // 不存在的学科：课程集合为空，直接返回空列表而不是报错
        mvc.perform(get("/api/v1/student/wrong-questions")
                        .param("subject_id", "99999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void removeThenSecondRemoveReturnsNotFound() throws Exception {
        mvc.perform(delete("/api/v1/student/wrong-questions/" + onShelfWrongId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/student/wrong-questions/" + onShelfWrongId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/student/wrong-questions").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/v1/student/wrong-questions")).andExpect(status().isUnauthorized());
    }

    /** 首页「待重做错题」只算可重做的：两条错题里有一条题目已下架，应返回 1 */
    @Test
    void homeCountsOnlyRedoableWrongQuestions() throws Exception {
        mvc.perform(get("/api/v1/student/home").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.is_guest").value(false))
                .andExpect(jsonPath("$.data.pending_wrong_question_count").value(1));
        // 游客不返回个性化数字
        mvc.perform(get("/api/v1/student/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending_wrong_question_count").value(0));
    }

    private Long insertWrong(Long questionId, int wrongCount) {
        jdbc.update("INSERT INTO wrong_question (student_id, practice_question_id, course_id, question_type,"
                        + " wrong_count, last_student_answer, last_wrong_at) VALUES (?, ?, ?, 'single_choice', ?,"
                        + " '[\"A\"]', NOW(3))", studentId, questionId, courseId, wrongCount);
        return jdbc.queryForObject("SELECT MAX(id) FROM wrong_question WHERE student_id = ?", Long.class, studentId);
    }
}
