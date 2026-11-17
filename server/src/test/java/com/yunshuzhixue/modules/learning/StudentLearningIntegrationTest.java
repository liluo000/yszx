package com.yunshuzhixue.modules.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

/** 课程打开与学习状态（S-LRN-01~03）、视频失效反馈（S-FDB-01） */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StudentLearningIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private String phone;
    private Long studentId;
    private String token;
    private Long courseId;
    private Long routeId;
    private Long stageId;

    @BeforeAll
    void setUp() throws Exception {
        phone = "135" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
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

        Map<String, Object> link = jdbc.queryForMap("SELECT sc.learning_route_id, sc.route_stage_id, sc.course_id"
                + " FROM stage_course sc JOIN course c ON c.id = sc.course_id AND c.status = 'on_shelf'"
                + " JOIN learning_route r ON r.id = sc.learning_route_id AND r.status = 'on_shelf' LIMIT 1");
        routeId = ((Number) link.get("learning_route_id")).longValue();
        stageId = ((Number) link.get("route_stage_id")).longValue();
        courseId = ((Number) link.get("course_id")).longValue();
    }

    @AfterEach
    void resetLearningData() {
        jdbc.update("DELETE FROM course_open_log WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM student_learning_position WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM student_course_state WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM student_daily_activity WHERE student_id = ?", studentId);
        jdbc.update("DELETE FROM feedback WHERE student_id = ?", studentId);
    }

    @AfterAll
    void cleanUp() {
        resetLearningData();
        jdbc.update("DELETE FROM student WHERE id = ?", studentId);
    }

    @Test
    void openCourseReturnsVideoUrlAndRecordsLearningBehavior() throws Exception {
        mvc.perform(post("/api/v1/student/courses/" + courseId + "/opens")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"learning_route_id\":" + routeId + ",\"route_stage_id\":" + stageId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.video_url").isNotEmpty())
                .andExpect(jsonPath("$.data.platform_name").isNotEmpty());

        assertThat(count("SELECT COUNT(*) FROM course_open_log WHERE student_id = ?", studentId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM student_course_state WHERE student_id = ?", studentId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM student_learning_position WHERE student_id = ?", studentId))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM student_daily_activity WHERE student_id = ?", studentId))
                .isEqualTo(1);
    }

    @Test
    void learnedStateIsReflectedInRouteDetail() throws Exception {
        mvc.perform(put("/api/v1/student/courses/" + courseId + "/learned")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.is_learned").value(true));
        String body = mvc.perform(get("/api/v1/student/routes/" + routeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String matched = JsonPath.read(body, "$.data.stages[*].courses[?(@.id == " + courseId
                + ")].is_learned").toString();
        assertThat(matched).contains("true");

        mvc.perform(delete("/api/v1/student/courses/" + courseId + "/learned")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(count("SELECT COUNT(*) FROM student_course_state"
                + " WHERE student_id = ? AND course_id = ? AND is_learned = 0", studentId, courseId)).isEqualTo(1);
    }

    /** S-LRN-04 / 05：三类学习行为形成轨迹，路线进度按当前编排汇总 */
    @Test
    void learningTracksAndRouteProgress() throws Exception {
        mvc.perform(post("/api/v1/student/courses/" + courseId + "/opens")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"learning_route_id\":" + routeId + ",\"route_stage_id\":" + stageId + "}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/v1/student/courses/" + courseId + "/learned")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        jdbc.update("UPDATE student_course_state SET is_mastered = 1, mastered_at = NOW(3)"
                + " WHERE student_id = ? AND course_id = ?", studentId, courseId);

        mvc.perform(get("/api/v1/student/learning-records/tracks")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "1").param("page_size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].course_title").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].occurred_at").isNotEmpty());

        mvc.perform(get("/api/v1/student/learning-records/tracks")
                        .header("Authorization", "Bearer " + token)
                        .param("record_type", "learned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].record_type").value("learned"))
                .andExpect(jsonPath("$.data.items[0].course_id").value(courseId));

        mvc.perform(get("/api/v1/student/learning-records/tracks")
                        .header("Authorization", "Bearer " + token)
                        .param("learning_route_id", String.valueOf(routeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));

        mvc.perform(get("/api/v1/student/learning-records/tracks")
                        .header("Authorization", "Bearer " + token)
                        .param("record_type", "unknown"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/student/learning-records/route-progress")
                        .header("Authorization", "Bearer " + token)
                        .param("learning_route_id", String.valueOf(routeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(routeId))
                .andExpect(jsonPath("$.data.items[0].progress.learned_course_count").value(1))
                .andExpect(jsonPath("$.data.items[0].progress.mastered_course_count").value(1));
    }

    @Test
    void masteredCourseCannotCancelLearned() throws Exception {
        jdbc.update("INSERT INTO student_course_state (student_id, course_id, is_learned, is_mastered)"
                + " VALUES (?, ?, 1, 1) ON DUPLICATE KEY UPDATE is_learned = 1, is_mastered = 1",
                studentId, courseId);
        mvc.perform(delete("/api/v1/student/courses/" + courseId + "/learned")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_ALREADY_MASTERED"));
    }

    @Test
    void videoInvalidFeedbackStoresCurrentVideoUrl() throws Exception {
        mvc.perform(post("/api/v1/student/feedbacks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedback_type\":\"video_invalid\",\"course_id\":" + courseId
                                + ",\"description\":\"视频打不开\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber());
        String stored = jdbc.queryForObject("SELECT video_url FROM feedback WHERE student_id = ? ORDER BY id DESC"
                + " LIMIT 1", String.class, studentId);
        String actual = jdbc.queryForObject("SELECT video_url FROM course WHERE id = ?", String.class, courseId);
        assertThat(stored).isEqualTo(actual);
    }

    /** 需求 5.2：打开课程后首页「继续学习」回到上次位置，并带路线进度与本阶段课程清单 */
    @Test
    void homeShowsContinueLearningAfterOpeningCourse() throws Exception {
        mvc.perform(post("/api/v1/student/courses/" + courseId + "/opens")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"learning_route_id\":" + routeId + ",\"route_stage_id\":" + stageId + "}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/student/home").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.continue_learning.learning_route_id").value(routeId))
                .andExpect(jsonPath("$.data.continue_learning.route_stage_id").value(stageId))
                .andExpect(jsonPath("$.data.continue_learning.course_id").value(courseId))
                .andExpect(jsonPath("$.data.continue_learning.course_title").isNotEmpty())
                .andExpect(jsonPath("$.data.continue_learning.progress.total_course_count").isNumber())
                .andExpect(jsonPath("$.data.continue_learning.stage_courses").isArray());

        mvc.perform(get("/api/v1/student/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.is_guest").value(true))
                .andExpect(jsonPath("$.data.continue_learning").value(nullValue()));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(post("/api/v1/student/courses/" + courseId + "/opens"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/student/ai-quota")).andExpect(status().isUnauthorized());
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
