package com.yunshuzhixue.modules.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 学员端首页（S-PTL-01）：游客返回路线入口，登录学员返回继续学习与我的路线进度 */
@SpringBootTest
@AutoConfigureMockMvc
class StudentHomeIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void guestGetsPublishedRoutesWithoutPersonalizedData() throws Exception {
        mvc.perform(get("/api/v1/student/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.is_guest").value(true))
                .andExpect(jsonPath("$.data.continue_learning").value(nullValue()))
                .andExpect(jsonPath("$.data.my_routes").isEmpty())
                .andExpect(jsonPath("$.data.pending_wrong_question_count").value(0))
                .andExpect(jsonPath("$.data.interview_remaining").value(0))
                .andExpect(jsonPath("$.data.routes").isArray());
    }

    @Test
    void learnedCourseShowsUpInMyRoutesWithProgress() throws Exception {
        String phone = "136" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
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
        String token = JsonPath.read(registered.getResponse().getContentAsString(), "$.data.token");
        Long studentId = jdbc.queryForObject("SELECT id FROM student WHERE phone = ?", Long.class, phone);

        Map<String, Object> link = jdbc.queryForMap("SELECT sc.learning_route_id, sc.route_stage_id, sc.course_id"
                + " FROM stage_course sc JOIN course c ON c.id = sc.course_id AND c.status = 'on_shelf'"
                + " JOIN learning_route r ON r.id = sc.learning_route_id AND r.status = 'on_shelf' LIMIT 1");
        long routeId = ((Number) link.get("learning_route_id")).longValue();
        long stageId = ((Number) link.get("route_stage_id")).longValue();
        long courseId = ((Number) link.get("course_id")).longValue();

        try {
            // 未学任何课程时，我的路线进度为空
            mvc.perform(get("/api/v1/student/home").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.my_routes").isEmpty());

            mvc.perform(post("/api/v1/student/courses/" + courseId + "/opens")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"learning_route_id\":" + routeId + ",\"route_stage_id\":" + stageId + "}"))
                    .andExpect(status().isCreated());
            mvc.perform(put("/api/v1/student/courses/" + courseId + "/learned")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            String body = mvc.perform(get("/api/v1/student/home").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.my_routes[0].id").value((int) routeId))
                    .andExpect(jsonPath("$.data.my_routes[0].progress.learned_course_count").value(1))
                    .andReturn().getResponse().getContentAsString();
            int total = JsonPath.read(body, "$.data.my_routes[0].progress.total_course_count");
            assertThat(total).isGreaterThanOrEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM course_open_log WHERE student_id = ?", studentId);
            jdbc.update("DELETE FROM student_learning_position WHERE student_id = ?", studentId);
            jdbc.update("DELETE FROM student_course_state WHERE student_id = ?", studentId);
            jdbc.update("DELETE FROM student_daily_activity WHERE student_id = ?", studentId);
            jdbc.update("DELETE FROM student WHERE id = ?", studentId);
        }
    }
}
