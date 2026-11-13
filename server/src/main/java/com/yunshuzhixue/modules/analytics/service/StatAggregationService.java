package com.yunshuzhixue.modules.analytics.service;

import com.yunshuzhixue.common.time.BeijingDates;
import com.yunshuzhixue.common.time.UtcTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 看板指标汇总（需求 6.8）。按 02.整体架构设计.md 4.3 的只读查询例外，跨模块只读统计，结果写入本模块的 stat_* 表。
 * 汇总一天是幂等的：重复执行会覆盖当天的结果。统计口径：
 * <ul>
 *   <li>活跃：student_daily_activity 中当天有记录的学员；月活跃为含当天在内近 30 天；</li>
 *   <li>留存：当天注册的学员中，第 1 / 第 7 天有学习行为的人数，那一天结束后才计算，之前为空；</li>
 *   <li>刷题量：顺序练习、随机抽题的作答（不含错题重做）加模拟考试中已判分的题目；</li>
 *   <li>面试：当天开始的面试，不含 AI 服务异常结束的场次；平均分取已生成报告的总分；</li>
 *   <li>AI 调用：按次数流水，面试为扣减减去退还，简答题评分为扣减加预留转扣减减去退还；</li>
 *   <li>路线（时点指标）：已上架路线中已上架课程的学习情况，取汇总时的状态。</li>
 * </ul>
 */
@Service
public class StatAggregationService {

    private final JdbcTemplate jdbc;

    public StatAggregationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 汇总某一天。withSnapshot 为 true 时同时记录路线学习人数和完成率这类时点指标（只对当天和刚结束的一天记录，
     * 避免补算历史日期时用现在的状态覆盖当时的快照）。
     */
    @Transactional
    public void aggregate(LocalDate date, boolean withSnapshot) {
        LocalDate today = LocalDate.now(BeijingDates.ZONE);
        LocalDateTime start = ts(BeijingDates.startOfDayUtc(date));
        LocalDateTime end = ts(BeijingDates.endOfDayUtc(date));
        LocalDateTime now = ts(UtcTime.now());

        long newStudents = count("SELECT COUNT(*) FROM student WHERE created_at >= ? AND created_at < ?", start, end);
        long cumulative = count("SELECT COUNT(*) FROM student WHERE created_at < ?", end);
        long dau = count("SELECT COUNT(*) FROM student_daily_activity WHERE activity_date = ?", date);
        long mau = count("SELECT COUNT(DISTINCT student_id) FROM student_daily_activity WHERE activity_date BETWEEN ? AND ?",
                date.minusDays(29), date);
        Long nextDay = date.plusDays(1).isBefore(today) ? retained(start, end, date.plusDays(1)) : null;
        Long sevenDay = date.plusDays(7).isBefore(today) ? retained(start, end, date.plusDays(7)) : null;

        Map<String, Object> practice = jdbc.queryForMap("SELECT COUNT(*) AS n, COALESCE(SUM(is_correct), 0) AS c FROM practice_answer"
                + " WHERE practice_mode IN ('sequential', 'random') AND answered_at >= ? AND answered_at < ?", start, end);
        Map<String, Object> exam = jdbc.queryForMap("SELECT COUNT(*) AS n, COALESCE(SUM(q.is_correct), 0) AS c"
                + " FROM exam_session_question q JOIN exam_session e ON e.id = q.exam_session_id"
                + " WHERE q.judge_status = 'judged' AND e.submitted_at >= ? AND e.submitted_at < ?", start, end);
        long answers = num(practice.get("n")) + num(exam.get("n"));
        long correct = num(practice.get("c")) + num(exam.get("c"));

        String validInterview = " FROM interview_session s WHERE s.quota_date = ? AND (s.end_reason IS NULL OR s.end_reason <> 'ai_failure')";
        long interviews = count("SELECT COUNT(*)" + validInterview, date);
        Map<String, Object> scores = jdbc.queryForMap("SELECT COUNT(r.total_score) AS n, COALESCE(SUM(r.total_score), 0) AS total"
                + " FROM interview_session s JOIN interview_report r ON r.interview_session_id = s.id AND r.status = 'generated'"
                + " WHERE s.quota_date = ? AND (s.end_reason IS NULL OR s.end_reason <> 'ai_failure')", date);
        long aiInterview = count("SELECT COALESCE(SUM(CASE action WHEN 'charge' THEN 1 WHEN 'refund' THEN -1 ELSE 0 END), 0)"
                + " FROM ai_quota_ledger WHERE quota_type = 'interview' AND quota_date = ?", date);
        long aiScoring = count("SELECT COALESCE(SUM(CASE action WHEN 'charge' THEN 1 WHEN 'consume' THEN 1 WHEN 'refund' THEN -1 ELSE 0 END), 0)"
                + " FROM ai_quota_ledger WHERE quota_type = 'scoring' AND quota_date = ?", date);

        jdbc.update("INSERT INTO stat_daily_overview (stat_date, new_student_count, cumulative_student_count,"
                        + " daily_active_student_count, monthly_active_student_count, next_day_retained_count, seven_day_retained_count,"
                        + " answer_count, correct_answer_count, interview_count, interview_scored_count, interview_score_sum,"
                        + " ai_interview_call_count, ai_scoring_call_count, refreshed_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                        + " ON DUPLICATE KEY UPDATE new_student_count = VALUES(new_student_count),"
                        + " cumulative_student_count = VALUES(cumulative_student_count),"
                        + " daily_active_student_count = VALUES(daily_active_student_count),"
                        + " monthly_active_student_count = VALUES(monthly_active_student_count),"
                        + " next_day_retained_count = VALUES(next_day_retained_count),"
                        + " seven_day_retained_count = VALUES(seven_day_retained_count), answer_count = VALUES(answer_count),"
                        + " correct_answer_count = VALUES(correct_answer_count), interview_count = VALUES(interview_count),"
                        + " interview_scored_count = VALUES(interview_scored_count), interview_score_sum = VALUES(interview_score_sum),"
                        + " ai_interview_call_count = VALUES(ai_interview_call_count),"
                        + " ai_scoring_call_count = VALUES(ai_scoring_call_count), refreshed_at = VALUES(refreshed_at)",
                date, newStudents, cumulative, dau, mau, nextDay, sevenDay, answers, correct, interviews,
                num(scores.get("n")), scores.get("total"), Math.max(aiInterview, 0), Math.max(aiScoring, 0), now);

        aggregateStages(date, now);
        aggregateQuestions(date, start, end, now);
        if (withSnapshot) {
            aggregateRoutes(date, now);
        }
    }

    /** 已汇总过的最早和最晚日期，用于补算缺失的日期 */
    public List<LocalDate> aggregatedDates(LocalDate from, LocalDate to) {
        return jdbc.queryForList("SELECT stat_date FROM stat_daily_overview WHERE stat_date BETWEEN ? AND ?", LocalDate.class,
                from, to);
    }

    /** 最早的注册日期（北京时间），没有学员时为空 */
    public LocalDate firstRegistrationDate() {
        LocalDateTime first = jdbc.queryForObject("SELECT MIN(created_at) FROM student", LocalDateTime.class);
        return first == null ? null
                : first.atOffset(java.time.ZoneOffset.UTC).atZoneSameInstant(BeijingDates.ZONE).toLocalDate();
    }

    /* ---------------- 内部 ---------------- */

    private Long retained(LocalDateTime start, LocalDateTime end, LocalDate activeDate) {
        return count("SELECT COUNT(*) FROM student s WHERE s.created_at >= ? AND s.created_at < ?"
                + " AND EXISTS (SELECT 1 FROM student_daily_activity a WHERE a.student_id = s.id AND a.activity_date = ?)",
                start, end, activeDate);
    }

    private void aggregateStages(LocalDate date, LocalDateTime now) {
        jdbc.update("DELETE FROM stat_stage_interview_daily WHERE stat_date = ?", date);
        jdbc.update("INSERT INTO stat_stage_interview_daily (stat_date, route_stage_id, stage_name, stage_interview_count,"
                + " stage_score_sum, refreshed_at)"
                + " SELECT ?, st.route_stage_id, MAX(st.stage_name), COUNT(*), SUM(st.score), ?"
                + " FROM interview_session_stage st JOIN interview_session s ON s.id = st.interview_session_id"
                + " WHERE s.quota_date = ? AND (s.end_reason IS NULL OR s.end_reason <> 'ai_failure') AND st.score IS NOT NULL"
                + " GROUP BY st.route_stage_id", date, now, date);
    }

    private void aggregateQuestions(LocalDate date, LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        jdbc.update("DELETE FROM stat_question_daily WHERE stat_date = ?", date);
        jdbc.update("INSERT INTO stat_question_daily (stat_date, practice_question_id, stem_excerpt, answer_count, wrong_count, refreshed_at)"
                + " SELECT ?, a.qid, LEFT(COALESCE(MAX(q.stem), ''), 200), SUM(a.n), SUM(a.w), ? FROM ("
                + "   SELECT practice_question_id AS qid, COUNT(*) AS n, SUM(1 - is_correct) AS w FROM practice_answer"
                + "   WHERE practice_mode IN ('sequential', 'random') AND answered_at >= ? AND answered_at < ? GROUP BY practice_question_id"
                + "   UNION ALL"
                + "   SELECT q.practice_question_id, COUNT(*), SUM(1 - q.is_correct) FROM exam_session_question q"
                + "   JOIN exam_session e ON e.id = q.exam_session_id"
                + "   WHERE q.judge_status = 'judged' AND e.submitted_at >= ? AND e.submitted_at < ? GROUP BY q.practice_question_id"
                + " ) a LEFT JOIN practice_question q ON q.id = a.qid GROUP BY a.qid", date, now, start, end, start, end);
    }

    /**
     * 路线时点指标：学过该路线 = 路线内任一门已上架课程有学习状态记录。
     * 已学进度 = 已学课程数 / 已上架课程数；已掌握进度 = 已掌握课程数 / 已配题课程数（有上架刷题题目，需求 4.5）。
     */
    private void aggregateRoutes(LocalDate date, LocalDateTime now) {
        jdbc.update("DELETE FROM stat_route_daily WHERE stat_date = ?", date);
        jdbc.update("INSERT INTO stat_route_daily (stat_date, learning_route_id, route_name, learner_count,"
                + " average_learned_progress, average_mastered_progress, refreshed_at)"
                + " SELECT ?, r.id, r.name, COUNT(p.student_id), COALESCE(ROUND(AVG(p.learned_rate), 2), 0),"
                + " COALESCE(ROUND(AVG(p.mastered_rate), 2), 0), ?"
                + " FROM learning_route r LEFT JOIN ("
                + "   SELECT rc.learning_route_id, st.student_id,"
                + "     100 * SUM(st.is_learned) / MAX(rc.total) AS learned_rate,"
                + "     IF(MAX(rc.configured) = 0, 0, 100 * SUM(st.is_mastered AND rc.is_configured) / MAX(rc.configured)) AS mastered_rate"
                + "   FROM (SELECT sc.learning_route_id, sc.course_id,"
                + "           EXISTS (SELECT 1 FROM practice_question pq WHERE pq.course_id = sc.course_id AND pq.status = 'on_shelf') AS is_configured,"
                + "           COUNT(*) OVER (PARTITION BY sc.learning_route_id) AS total,"
                + "           SUM(EXISTS (SELECT 1 FROM practice_question pq WHERE pq.course_id = sc.course_id AND pq.status = 'on_shelf'))"
                + "             OVER (PARTITION BY sc.learning_route_id) AS configured"
                + "         FROM stage_course sc JOIN course c ON c.id = sc.course_id AND c.status = 'on_shelf') rc"
                + "   JOIN student_course_state st ON st.course_id = rc.course_id"
                + "   GROUP BY rc.learning_route_id, st.student_id"
                + " ) p ON p.learning_route_id = r.id"
                + " WHERE r.status = 'on_shelf' GROUP BY r.id, r.name", date, now);
    }

    private long count(String sql, Object... args) {
        Number n = jdbc.queryForObject(sql, Number.class, args);
        return n == null ? 0 : n.longValue();
    }

    private static long num(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    /** 直接传 LocalDateTime：驱动不做时区换算（连接已按 UTC），避免 Timestamp 按 JVM 时区偏移 */
    private static LocalDateTime ts(LocalDateTime utc) {
        return utc;
    }
}
