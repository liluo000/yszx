package com.yunshuzhixue.modules.studentops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunshuzhixue.common.time.BeijingDates;
import com.yunshuzhixue.modules.analytics.service.StatAggregationService;
import com.yunshuzhixue.modules.audit.entity.OperationLog;
import com.yunshuzhixue.modules.audit.mapper.OperationLogMapper;
import com.yunshuzhixue.modules.iam.entity.SysUser;
import com.yunshuzhixue.modules.iam.mapper.SysUserMapper;
import com.yunshuzhixue.support.AdminTestAccounts;
import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 学员管理（A-STU-01~06）与数据看板（A-DSH-01、02）。学员端尚未开发，测试直接写入学员和学习、刷题、面试数据，
 * 使用 2000 年的日期，避免与真实数据混在一起，结束后全部删除。
 */
@SpringBootTest
@AutoConfigureMockMvc
class StudentAndDashboardIntegrationTest {

    /** 测试数据所在的日期（北京时间） */
    private static final LocalDate DAY0 = LocalDate.of(2000, 3, 1);

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
    @Autowired
    private StatAggregationService aggregation;

    private AdminTestAccounts accounts;
    private SysUser operator;
    private String token;
    private final List<Long> students = new ArrayList<>();
    private String last4;
    private Long routeId;
    private Long courseId;

    @BeforeEach
    void setUp() throws Exception {
        accounts = new AdminTestAccounts(mvc, sysUserMapper, operationLogMapper, passwordEncoder, redis);
        operator = accounts.create("student_operator");
        token = accounts.login(operator);
        // 借用一条已有的上架路线及其中一门已上架课程
        var pair = jdbc.queryForMap("SELECT sc.learning_route_id AS r, sc.course_id AS c FROM stage_course sc"
                + " JOIN course c ON c.id = sc.course_id AND c.status = 'on_shelf'"
                + " JOIN learning_route lr ON lr.id = sc.learning_route_id ORDER BY sc.id LIMIT 1");
        routeId = ((Number) pair.get("r")).longValue();
        courseId = ((Number) pair.get("c")).longValue();
        last4 = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        students.add(student("甲学员", "1990000" + last4, "bachelor", DAY0.atTime(10, 0), "normal"));
        students.add(student("乙学员", "1980000" + String.format("%04d", ThreadLocalRandom.current().nextInt(10000)), null,
                DAY0.atTime(11, 0), "normal"));
        students.add(student("丙学员", "1970000" + String.format("%04d", ThreadLocalRandom.current().nextInt(10000)), null,
                DAY0.plusDays(1).atTime(9, 0), "cancelling"));
    }

    @AfterEach
    void cleanUp() {
        for (String table : List.of("student_daily_activity", "student_course_state", "practice_answer", "interview_session")) {
            students.forEach(id -> jdbc.update("DELETE FROM " + table + " WHERE student_id = ?", id));
        }
        jdbc.update("DELETE FROM student_export_log WHERE sys_user_id = ?", operator.getId());
        students.forEach(id -> jdbc.update("DELETE FROM student WHERE id = ?", id));
        for (String table : List.of("stat_daily_overview", "stat_route_daily", "stat_stage_interview_daily", "stat_question_daily")) {
            jdbc.update("DELETE FROM " + table + " WHERE stat_date BETWEEN ? AND ?", DAY0.minusDays(1), DAY0.plusDays(10));
        }
        accounts.cleanUp();
    }

    @Test
    void studentListFiltersBanAndExport() throws Exception {
        Long first = students.get(0);
        jdbc.update("INSERT INTO student_course_state (student_id, course_id, first_opened_at, is_learned) VALUES (?, ?, ?, 1)",
                first, courseId, ts(DAY0.atTime(12, 0)));
        interview(first, DAY0, "completed");

        mvc.perform(get("/api/v1/admin/students").header("Authorization", token).param("keyword", last4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id==" + first + ")].phone_masked").value("199****" + last4))
                .andExpect(jsonPath("$.data.items[?(@.id==" + first + ")].learned_route_count").value(1))
                .andExpect(jsonPath("$.data.items[?(@.id==" + first + ")].interview_count").value(1))
                .andExpect(jsonPath("$.data.items[?(@.id==" + first + ")].education").value("bachelor"));
        mvc.perform(get("/api/v1/admin/students").header("Authorization", token)
                        .param("learned_route_id", routeId.toString()).param("has_interview", "true")
                        .param("registered_start_date", DAY0.toString()).param("registered_end_date", DAY0.toString()))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(first));
        mvc.perform(get("/api/v1/admin/students").header("Authorization", token)
                        .param("registered_start_date", DAY0.toString()).param("registered_end_date", DAY0.plusDays(1).toString())
                        .param("has_interview", "false"))
                .andExpect(jsonPath("$.data.total").value(2));

        // 查看完整手机号记日志，日志中不出现手机号
        mvc.perform(post("/api/v1/admin/students/" + first + "/phone-reveals").header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.phone").value("1990000" + last4));

        mvc.perform(put("/api/v1/admin/students/" + first + "/ban").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ban_reason\":\"发布广告\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("banned"));
        mvc.perform(get("/api/v1/admin/students/" + first).header("Authorization", token))
                .andExpect(jsonPath("$.data.status").value("banned"))
                .andExpect(jsonPath("$.data.ban_reason").value("发布广告"));
        mvc.perform(put("/api/v1/admin/students/" + first + "/ban").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ban_reason\":\"再次\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDENT_STATE_INVALID"));
        mvc.perform(put("/api/v1/admin/students/" + students.get(2) + "/ban").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ban_reason\":\"x\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/admin/students/" + first + "/ban").header("Authorization", token))
                .andExpect(jsonPath("$.data.status").value("normal"));

        // 导出：注销中的账号不导出，含完整手机号，留痕
        MvcResult export = mvc.perform(post("/api/v1/admin/student-exports").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registered_start_date\":\"" + DAY0 + "\",\"registered_end_date\":\"" + DAY0.plusDays(1) + "\"}"))
                .andExpect(status().isOk()).andReturn();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(export.getResponse().getContentAsByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("手机号");
            assertThat(sheet.getRow(2).getCell(2).getStringCellValue()).isEqualTo("1990000" + last4);
            assertThat(sheet.getRow(2).getCell(5).getStringCellValue()).isEqualTo("本科");
        }
        assertThat(jdbc.queryForObject("SELECT exported_count FROM student_export_log WHERE sys_user_id = ?", Integer.class,
                operator.getId())).isEqualTo(2);

        List<OperationLog> logs = operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                .eq(OperationLog::getSysUserId, operator.getId()).eq(OperationLog::getModule, "学员管理"));
        assertThat(logs).extracting(OperationLog::getAction).containsExactly("查看完整手机号", "封禁学员", "解封学员", "导出");
        assertThat(logs).allSatisfy(l -> assertThat(l.getTargetLabel()).doesNotContain("1990000" + last4));

        String contentOps = accounts.login(accounts.create("content_operator"));
        mvc.perform(get("/api/v1/admin/students").header("Authorization", contentOps)).andExpect(status().isForbidden());
    }

    @Test
    void dashboardAggregatesDailyMetrics() throws Exception {
        Long a = students.get(0);
        Long b = students.get(1);
        Long c = students.get(2);
        activity(a, DAY0);
        activity(b, DAY0);
        activity(a, DAY0.plusDays(1));
        activity(c, DAY0.plusDays(1));
        activity(a, DAY0.plusDays(7));
        Long question = jdbc.queryForObject("SELECT MIN(id) FROM practice_question WHERE status = 'on_shelf'", Long.class);
        for (int i = 0; i < 12; i++) {
            answer(i % 2 == 0 ? a : b, question, i, i < 9 ? 0 : 1, DAY0.atTime(13, i));
        }
        interview(a, DAY0, "completed");
        interview(b, DAY0, "ai_failure");

        for (int i = 0; i <= 8; i++) {
            aggregation.aggregate(DAY0.plusDays(i), true);
        }

        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", token)
                        .param("start_date", DAY0.toString()).param("end_date", DAY0.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acquisition[0].new_student_count").value(2))
                .andExpect(jsonPath("$.data.acquisition[1].cumulative_student_count").value(
                        jdbc.queryForObject("SELECT COUNT(*) FROM student WHERE created_at < ?", Long.class,
                                ts(BeijingDates.endOfDayUtc(DAY0.plusDays(1))))))
                .andExpect(jsonPath("$.data.activity[0].daily_active_student_count").value(2))
                .andExpect(jsonPath("$.data.activity[1].monthly_active_student_count").value(3))
                .andExpect(jsonPath("$.data.activity[0].next_day_retention_rate").value(50.0))
                .andExpect(jsonPath("$.data.activity[0].seven_day_retention_rate").value(50.0))
                .andExpect(jsonPath("$.data.practice.answer_count").value(12))
                .andExpect(jsonPath("$.data.practice.correct_rate").value(25.0))
                .andExpect(jsonPath("$.data.top_wrong_questions[0].practice_question_id").value(question))
                .andExpect(jsonPath("$.data.top_wrong_questions[0].wrong_rate").value(75.0))
                .andExpect(jsonPath("$.data.interview.interview_count").value(1))
                .andExpect(jsonPath("$.data.refreshed_at").isNotEmpty());

        // 按周：日活跃为周期内日均值，留存按分子分母合计
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", token)
                        .param("start_date", DAY0.toString()).param("end_date", DAY0.plusDays(1).toString())
                        .param("granularity", "week"))
                .andExpect(jsonPath("$.data.activity.length()").value(1))
                .andExpect(jsonPath("$.data.activity[0].daily_active_student_count").value(2))
                .andExpect(jsonPath("$.data.acquisition[0].new_student_count").value(3));

        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", token)
                        .param("start_date", DAY0.toString()).param("end_date", DAY0.plusDays(400).toString()))
                .andExpect(status().isBadRequest());

        MvcResult export = mvc.perform(post("/api/v1/admin/dashboard-exports").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start_date\":\"" + DAY0 + "\",\"end_date\":\"" + DAY0.plusDays(1) + "\"}"))
                .andExpect(status().isOk()).andReturn();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(export.getResponse().getContentAsByteArray()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(6);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue()).isEqualTo("2");
        }
        assertThat(operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                        .eq(OperationLog::getSysUserId, operator.getId()).eq(OperationLog::getModule, "数据看板")))
                .extracting(OperationLog::getAction).containsExactly("导出");
    }

    /* ---------------- 测试数据 ---------------- */

    private Long student(String nickname, String phone, String education, LocalDateTime beijingTime, String status) {
        LocalDateTime utc = beijingTime.minusHours(8);
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO student (phone, nickname, education, status, cancellation_effective_at,"
                    + " created_at, last_active_at) VALUES (?, ?, ?, ?, ?, ?, ?)", new String[] {"id"});
            ps.setString(1, phone);
            ps.setString(2, nickname);
            ps.setString(3, education);
            ps.setString(4, status);
            ps.setTimestamp(5, "cancelling".equals(status) ? ts(utc.plusDays(7)) : null);
            ps.setTimestamp(6, ts(utc));
            ps.setTimestamp(7, ts(utc));
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    private void activity(Long studentId, LocalDate date) {
        jdbc.update("INSERT INTO student_daily_activity (student_id, activity_date) VALUES (?, ?)", studentId, date);
    }

    private void answer(Long studentId, Long questionId, int session, int correct, LocalDateTime beijingTime) {
        jdbc.update("INSERT INTO practice_answer (practice_session_id, student_id, practice_question_id, course_id, practice_mode,"
                        + " question_type, question_snapshot, student_answer, judge_method, is_correct, answered_at)"
                        + " VALUES (?, ?, ?, ?, 'random', 'true_false', '{}', 'true', 'auto', ?, ?)",
                900_000_000L + session, studentId, questionId, courseId, correct, ts(beijingTime.minusHours(8)));
    }

    private void interview(Long studentId, LocalDate date, String endReason) {
        Timestamp start = ts(date.atTime(6, 0));
        jdbc.update("INSERT INTO interview_session (student_id, learning_route_id, route_name, interview_mode, scope_type, status,"
                        + " end_reason, config_snapshot, main_question_count, duration_minutes, quota_date, started_at, deadline_at, ended_at)"
                        + " VALUES (?, ?, '测试路线', 'text', 'whole_route', 'ended', ?, '{}', 5, 30, ?, ?, ?, ?)",
                studentId, routeId, endReason, date, start, start, start);
    }

    private static Timestamp ts(LocalDateTime utc) {
        return Timestamp.valueOf(utc);
    }
}
