package com.yunshuzhixue.modules.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.yunshuzhixue.modules.audit.mapper.OperationLogMapper;
import com.yunshuzhixue.modules.iam.mapper.SysUserMapper;
import com.yunshuzhixue.support.AdminTestAccounts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 工作台（A-DSH-03）：按角色返回字段、待处理反馈合并计数、未配题课程统计 */
@SpringBootTest
@AutoConfigureMockMvc
class WorkbenchIntegrationTest {

    /** 测试数据的标记，结束后按标记清理 */
    private static final String MARK = "IT_WORKBENCH_TEST";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private OperationLogMapper operationLogMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JdbcTemplate jdbc;

    private AdminTestAccounts accounts;
    private String superToken;
    private String contentToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new AdminTestAccounts(mvc, sysUserMapper, operationLogMapper, passwordEncoder, redis);
        superToken = accounts.login(accounts.create("super_admin"));
        contentToken = accounts.login(accounts.create("content_operator"));
        studentToken = accounts.login(accounts.create("student_operator"));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM feedback WHERE description = ?", MARK);
        jdbc.update("DELETE FROM course WHERE title = ?", MARK);
        accounts.cleanUp();
    }

    @Test
    void returnsOnlyFieldsAllowedForRole() throws Exception {
        mvc.perform(get("/api/v1/admin/workbench").header("Authorization", superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending_question_correction_count").isNumber())
                .andExpect(jsonPath("$.data.pending_video_invalid_count").isNumber())
                .andExpect(jsonPath("$.data.unconfigured_course_count").isNumber())
                .andExpect(jsonPath("$.data.insufficient_stage_count").isNumber())
                .andExpect(jsonPath("$.data.today_new_student_count").isNumber())
                .andExpect(jsonPath("$.data.today_active_student_count").isNumber())
                .andExpect(jsonPath("$.data.today_interview_count").isNumber());

        mvc.perform(get("/api/v1/admin/workbench").header("Authorization", contentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending_question_correction_count").isNumber())
                .andExpect(jsonPath("$.data.pending_video_invalid_count").isNumber())
                .andExpect(jsonPath("$.data.unconfigured_course_count").isNumber())
                .andExpect(jsonPath("$.data.insufficient_stage_count").isNumber())
                .andExpect(jsonPath("$.data.today_new_student_count").value(nullValue()))
                .andExpect(jsonPath("$.data.today_active_student_count").value(nullValue()))
                .andExpect(jsonPath("$.data.today_interview_count").value(nullValue()));

        mvc.perform(get("/api/v1/admin/workbench").header("Authorization", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending_question_correction_count").value(nullValue()))
                .andExpect(jsonPath("$.data.pending_video_invalid_count").value(nullValue()))
                .andExpect(jsonPath("$.data.unconfigured_course_count").value(nullValue()))
                .andExpect(jsonPath("$.data.insufficient_stage_count").value(nullValue()))
                .andExpect(jsonPath("$.data.today_new_student_count").isNumber())
                .andExpect(jsonPath("$.data.today_active_student_count").isNumber())
                .andExpect(jsonPath("$.data.today_interview_count").isNumber());
    }

    @Test
    void pendingFeedbacksAreCountedByGroup() throws Exception {
        var row = jdbc.queryForMap("SELECT pq.id AS qid, pq.course_id AS cid FROM practice_question pq"
                + " WHERE pq.status = 'on_shelf' ORDER BY pq.id LIMIT 1");
        long questionId = ((Number) row.get("qid")).longValue();
        long courseId = ((Number) row.get("cid")).longValue();
        long before = countOf("pending_question_correction_count");
        for (int i = 0; i < 2; i++) {
            jdbc.update("INSERT INTO feedback (feedback_type, student_id, course_id, practice_question_id,"
                    + " error_type, description, status)"
                    + " VALUES ('question_correction', 0, ?, ?, 'stem_error', ?, 'pending')",
                    courseId, questionId, MARK);
        }
        assertThat(countOf("pending_question_correction_count") - before).isEqualTo(1);
    }

    @Test
    void unconfiguredCoursesAreCounted() throws Exception {
        long before = countOf("unconfigured_course_count");
        jdbc.update("INSERT INTO course (title, video_platform_id, video_url, sort_no, status)"
                + " SELECT ?, MIN(id), 'https://www.bilibili.com/video/it', 99999, 'on_shelf' FROM video_platform", MARK);
        assertThat(countOf("unconfigured_course_count") - before).isEqualTo(1);
    }

    private long countOf(String field) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/admin/workbench").header("Authorization", superToken))
                .andExpect(status().isOk()).andReturn();
        Object value = JsonPath.read(result.getResponse().getContentAsString(), "$.data." + field);
        return ((Number) value).longValue();
    }
}
