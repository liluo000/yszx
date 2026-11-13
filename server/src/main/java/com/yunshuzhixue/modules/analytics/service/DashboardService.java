package com.yunshuzhixue.modules.analytics.service;

import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.time.UtcTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 读取看板数据（A-DSH-01），只读 stat_* 汇总表。按周期合并时（需求 6.8 统计口径）：
 * 时点指标取周期最后一天；比率按分子、分母分别合计后相除；按周、月查看时日活跃为周期内日均值。
 */
@Service
public class DashboardService {

    public static final int MAX_DAYS = 366;

    public record Trend(LocalDate statDate, long newStudentCount, long cumulativeStudentCount) {
    }

    public record Activity(LocalDate statDate, long dailyActiveStudentCount, long monthlyActiveStudentCount,
                           BigDecimal nextDayRetentionRate, BigDecimal sevenDayRetentionRate) {
    }

    public record RouteMetric(Long learningRouteId, String routeName, long learnerCount, BigDecimal averageLearnedProgress,
                              BigDecimal averageMasteredProgress) {
    }

    public record Practice(long answerCount, BigDecimal correctRate) {
    }

    public record WrongQuestion(Long practiceQuestionId, String stemExcerpt, long answerCount, long wrongCount,
                                BigDecimal wrongRate) {
    }

    public record Interview(long interviewCount, BigDecimal averageScore) {
    }

    public record StageScore(Long routeStageId, String stageName, BigDecimal averageScore) {
    }

    public record AiUsage(LocalDate statDate, long aiInterviewCallCount, long aiScoringCallCount) {
    }

    public record Dashboard(List<Trend> acquisition, List<Activity> activity, List<RouteMetric> routes, Practice practice,
                            List<WrongQuestion> topWrongQuestions, Interview interview, List<StageScore> stageScores,
                            List<AiUsage> aiUsage, Instant refreshedAt) {
    }

    private final JdbcTemplate jdbc;

    public DashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Dashboard load(LocalDate start, LocalDate end, String granularity) {
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "结束日期不能早于开始日期");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "时间跨度不能超过 " + MAX_DAYS + " 天");
        }
        List<Map<String, Object>> days = jdbc.queryForList(
                "SELECT * FROM stat_daily_overview WHERE stat_date BETWEEN ? AND ? ORDER BY stat_date", start, end);
        Map<LocalDate, List<Map<String, Object>>> periods = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            periods.computeIfAbsent(periodStart(d, granularity, start), k -> new ArrayList<>());
        }
        for (Map<String, Object> day : days) {
            periods.get(periodStart(date(day.get("stat_date")), granularity, start)).add(day);
        }

        List<Trend> acquisition = new ArrayList<>();
        List<Activity> activity = new ArrayList<>();
        List<AiUsage> aiUsage = new ArrayList<>();
        periods.forEach((period, rows) -> {
            Map<String, Object> last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
            acquisition.add(new Trend(period, sum(rows, "new_student_count"),
                    last == null ? 0 : n(last.get("cumulative_student_count"))));
            long dauAvg = rows.isEmpty() ? 0 : Math.round((double) sum(rows, "daily_active_student_count") / rows.size());
            activity.add(new Activity(period, dauAvg, last == null ? 0 : n(last.get("monthly_active_student_count")),
                    retention(rows, "next_day_retained_count"), retention(rows, "seven_day_retained_count")));
            aiUsage.add(new AiUsage(period, sum(rows, "ai_interview_call_count"), sum(rows, "ai_scoring_call_count")));
        });

        long answers = sum(days, "answer_count");
        Practice practice = new Practice(answers, rate(sum(days, "correct_answer_count"), answers));
        long scored = sum(days, "interview_scored_count");
        BigDecimal scoreSum = days.stream().map(d -> (BigDecimal) d.get("interview_score_sum"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Interview interview = new Interview(sum(days, "interview_count"),
                scored == 0 ? null : scoreSum.divide(BigDecimal.valueOf(scored), 1, RoundingMode.HALF_UP));

        // 路线时点指标取范围内最后一个有汇总的日期
        List<RouteMetric> routes = jdbc.query("SELECT * FROM stat_route_daily WHERE stat_date = ("
                        + " SELECT MAX(stat_date) FROM stat_route_daily WHERE stat_date BETWEEN ? AND ?) ORDER BY learner_count DESC, learning_route_id",
                (rs, i) -> new RouteMetric(rs.getLong("learning_route_id"), rs.getString("route_name"), rs.getLong("learner_count"),
                        rs.getBigDecimal("average_learned_progress"), rs.getBigDecimal("average_mastered_progress")), start, end);

        List<WrongQuestion> wrong = jdbc.query("SELECT practice_question_id, MAX(stem_excerpt) AS stem, SUM(answer_count) AS n,"
                        + " SUM(wrong_count) AS w FROM stat_question_daily WHERE stat_date BETWEEN ? AND ?"
                        + " GROUP BY practice_question_id HAVING SUM(answer_count) >= 10"
                        + " ORDER BY SUM(wrong_count) / SUM(answer_count) DESC, SUM(answer_count) DESC LIMIT 20",
                (rs, i) -> new WrongQuestion(rs.getLong("practice_question_id"), rs.getString("stem"), rs.getLong("n"),
                        rs.getLong("w"), rate(rs.getLong("w"), rs.getLong("n"))), start, end);

        List<StageScore> stages = jdbc.query("SELECT route_stage_id, MAX(stage_name) AS name, SUM(stage_interview_count) AS n,"
                        + " SUM(stage_score_sum) AS total FROM stat_stage_interview_daily WHERE stat_date BETWEEN ? AND ?"
                        + " GROUP BY route_stage_id ORDER BY route_stage_id",
                (rs, i) -> new StageScore(rs.getLong("route_stage_id"), rs.getString("name"),
                        rs.getBigDecimal("total").divide(BigDecimal.valueOf(Math.max(1, rs.getLong("n"))), 1, RoundingMode.HALF_UP)),
                start, end);

        Timestamp refreshed = jdbc.queryForObject("SELECT MAX(refreshed_at) FROM stat_daily_overview", Timestamp.class);
        return new Dashboard(acquisition, activity, routes, practice, wrong, interview, stages, aiUsage,
                refreshed == null ? null : UtcTime.toInstant(refreshed.toLocalDateTime()));
    }

    /** 周期起点：按日为当天；按周为周一；按月为 1 日；首个周期不早于开始日期 */
    static LocalDate periodStart(LocalDate date, String granularity, LocalDate rangeStart) {
        LocalDate start = switch (granularity) {
            case "week" -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "month" -> date.withDayOfMonth(1);
            default -> date;
        };
        return start.isBefore(rangeStart) ? rangeStart : start;
    }

    /** 留存率：只合计已经到统计时间（留存人数不为空）的日期 */
    private static BigDecimal retention(List<Map<String, Object>> rows, String retainedColumn) {
        long retained = 0;
        long registered = 0;
        for (Map<String, Object> row : rows) {
            if (row.get(retainedColumn) != null) {
                retained += n(row.get(retainedColumn));
                registered += n(row.get("new_student_count"));
            }
        }
        return registered == 0 ? null : rate(retained, registered);
    }

    /** 百分比，保留 2 位小数；分母为 0 时为空 */
    static BigDecimal rate(long numerator, long denominator) {
        return denominator == 0 ? null
                : BigDecimal.valueOf(numerator * 100L).divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static long sum(List<Map<String, Object>> rows, String column) {
        return rows.stream().mapToLong(r -> n(r.get(column))).sum();
    }

    private static long n(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static LocalDate date(Object value) {
        return value instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) value;
    }
}
