package com.yunshuzhixue.modules.analytics.service;

import com.yunshuzhixue.common.time.BeijingDates;
import com.yunshuzhixue.modules.sysconfig.api.SysConfigReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 工作台（需求 6.12，A-DSH-03）：按角色汇总待办与当日数据。
 * 只读跨模块查询，依据 02.整体架构设计.md 4.3 中 M19 的只读查询例外。
 */
@Service
public class WorkbenchService {

    /** 七项指标；当前角色无权查看的字段为 null（A-DSH-03「其余为空」） */
    public record WorkbenchView(
            Long pendingQuestionCorrectionCount,
            Long pendingVideoInvalidCount,
            Long unconfiguredCourseCount,
            Long insufficientStageCount,
            Long todayNewStudentCount,
            Long todayActiveStudentCount,
            Long todayInterviewCount) {
    }

    private static final String SUPER_ADMIN = "super_admin";
    private static final String CONTENT_OPERATOR = "content_operator";
    private static final String STUDENT_OPERATOR = "student_operator";

    private final JdbcTemplate jdbc;
    private final SysConfigReader configs;

    public WorkbenchService(JdbcTemplate jdbc, SysConfigReader configs) {
        this.jdbc = jdbc;
        this.configs = configs;
    }

    public WorkbenchView load(String role) {
        boolean content = SUPER_ADMIN.equals(role) || CONTENT_OPERATOR.equals(role);
        boolean student = SUPER_ADMIN.equals(role) || STUDENT_OPERATOR.equals(role);

        Long question = null;
        Long video = null;
        Long unconfigured = null;
        Long insufficient = null;
        Long newStudents = null;
        Long active = null;
        Long interviews = null;

        if (content) {
            question = pendingCount("question_correction");
            video = pendingCount("video_invalid");
            unconfigured = unconfiguredCourses();
            insufficient = insufficientStages();
        }
        if (student) {
            // 与 StatAggregationService 的看板口径一致：按北京时间今天，AI 异常结束的面试不计入场次
            LocalDate today = LocalDate.now(BeijingDates.ZONE);
            LocalDateTime start = BeijingDates.startOfDayUtc(today);
            LocalDateTime end = BeijingDates.endOfDayUtc(today);
            newStudents = count("SELECT COUNT(*) FROM student WHERE created_at >= ? AND created_at < ?", start, end);
            active = count("SELECT COUNT(*) FROM student_daily_activity WHERE activity_date = ?", today);
            interviews = count("SELECT COUNT(*) FROM interview_session WHERE quota_date = ?"
                    + " AND (end_reason IS NULL OR end_reason <> 'ai_failure')", today);
        }
        return new WorkbenchView(question, video, unconfigured, insufficient, newStudents, active, interviews);
    }

    /** 待处理反馈按 A-FDB-01 的合并口径计组数：同一道题或同一门课的多次提交算一项 */
    private long pendingCount(String feedbackType) {
        return count("SELECT COUNT(DISTINCT CONCAT(feedback_type, ':', COALESCE(practice_question_id, course_id)))"
                + " FROM feedback WHERE status = 'pending' AND feedback_type = ?", feedbackType);
    }

    /** 未配题：已上架课程里没有已上架刷题题（对应 CourseItem.practice_question_count = 0） */
    private long unconfiguredCourses() {
        return count("SELECT COUNT(*) FROM course c WHERE c.status = 'on_shelf'"
                + " AND NOT EXISTS (SELECT 1 FROM practice_question pq"
                + " WHERE pq.course_id = c.id AND pq.status = 'on_shelf')");
    }

    /**
     * 题量不足的阶段（需求 6.12）：已上架路线的阶段，刷题题量低于随机抽题或模拟考试最低题量，
     * 或面试题量低于面试最低题量时，至少一个入口会置灰。只统计已上架课程中已上架的题目，
     * 与看板的路线统计口径一致；阈值取系统配置的当前值。
     */
    private long insufficientStages() {
        int practiceMin = Math.max(configs.getInt("practice.random_min_question_count"),
                configs.getInt("exam.min_question_count"));
        int interviewMin = configs.getInt("interview.min_question_count");
        return count("SELECT COUNT(*) FROM route_stage st"
                + " JOIN learning_route lr ON lr.id = st.learning_route_id AND lr.status = 'on_shelf'"
                + " WHERE (SELECT COUNT(*) FROM stage_course sc"
                + "   JOIN course c ON c.id = sc.course_id AND c.status = 'on_shelf'"
                + "   JOIN practice_question pq ON pq.course_id = c.id AND pq.status = 'on_shelf'"
                + "   WHERE sc.route_stage_id = st.id) < ?"
                + " OR (SELECT COUNT(*) FROM stage_course sc"
                + "   JOIN course c ON c.id = sc.course_id AND c.status = 'on_shelf'"
                + "   JOIN interview_question iq ON iq.course_id = c.id AND iq.status = 'on_shelf'"
                + "   WHERE sc.route_stage_id = st.id) < ?", practiceMin, interviewMin);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
