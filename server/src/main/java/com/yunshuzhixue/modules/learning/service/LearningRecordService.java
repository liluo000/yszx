package com.yunshuzhixue.modules.learning.service;

import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.time.BeijingDates;
import com.yunshuzhixue.modules.content.api.RouteDirectory;
import com.yunshuzhixue.modules.content.api.RouteDirectory.CourseItem;
import com.yunshuzhixue.modules.content.api.RouteDirectory.RouteCard;
import com.yunshuzhixue.modules.content.api.RouteDirectory.RouteDetail;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 学习记录（M08，S-LRN-04、05）：把课程打开、标记已学、达成已掌握三类事件汇总成轨迹，
 * 并按当前编排给出各路线已学/已掌握进度。
 */
@Service
public class LearningRecordService {

    private static final String TRACK_SOURCE = " FROM ("
            + " SELECT 'opened' AS record_type, l.course_id, c.title AS course_title, l.opened_at AS occurred_at"
            + " FROM course_open_log l JOIN course c ON c.id = l.course_id WHERE l.student_id = ?"
            + " UNION ALL"
            + " SELECT 'learned', s.course_id, c.title, s.learned_at"
            + " FROM student_course_state s JOIN course c ON c.id = s.course_id"
            + " WHERE s.student_id = ? AND s.learned_at IS NOT NULL"
            + " UNION ALL"
            + " SELECT 'mastered', s.course_id, c.title, s.mastered_at"
            + " FROM student_course_state s JOIN course c ON c.id = s.course_id"
            + " WHERE s.student_id = ? AND s.mastered_at IS NOT NULL"
            + " ) t WHERE 1 = 1";

    private final JdbcTemplate jdbc;
    private final RouteDirectory routes;

    public LearningRecordService(JdbcTemplate jdbc, RouteDirectory routes) {
        this.jdbc = jdbc;
        this.routes = routes;
    }

    /** S-LRN-04：轨迹分页，按发生时间倒序；日期按北京时间换算成 UTC 查询 */
    public PageResult<TrackItem> tracks(long studentId, PageQuery page, String recordType, Long routeId,
                                        LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "结束日期不能早于开始日期");
        }
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (recordType != null && !recordType.isBlank()) {
            if (!List.of("opened", "learned", "mastered").contains(recordType)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "学习轨迹类型不正确");
            }
            where.append(" AND t.record_type = ?");
            args.add(recordType);
        }
        if (routeId != null) {
            where.append(" AND EXISTS (SELECT 1 FROM stage_course sc WHERE sc.course_id = t.course_id"
                    + " AND sc.learning_route_id = ?)");
            args.add(routeId);
        }
        if (startDate != null) {
            where.append(" AND t.occurred_at >= ?");
            args.add(LocalDateTime.from(BeijingDates.startOfDayUtc(startDate)));
        }
        if (endDate != null) {
            where.append(" AND t.occurred_at < ?");
            args.add(LocalDateTime.from(BeijingDates.endOfDayUtc(endDate)));
        }
        // 每个子查询各有一个 student_id 参数
        List<Object> countArgs = new ArrayList<>(List.of(studentId, studentId, studentId));
        countArgs.addAll(args);
        long total = jdbc.queryForObject("SELECT COUNT(*)" + TRACK_SOURCE + where, Long.class,
                countArgs.toArray());

        List<Object> rowsArgs = new ArrayList<>(countArgs);
        rowsArgs.add(page.pageSize());
        rowsArgs.add(page.offset());
        List<TrackItem> items = jdbc.queryForList(
                "SELECT t.record_type, t.course_id, t.course_title, t.occurred_at" + TRACK_SOURCE
                        + where + " ORDER BY t.occurred_at DESC, t.course_id DESC LIMIT ? OFFSET ?",
                rowsArgs.toArray()).stream()
                .map(row -> new TrackItem(String.valueOf(row.get("record_type")),
                        row.get("course_id") == null ? null : ((Number) row.get("course_id")).longValue(),
                        String.valueOf(row.get("course_title")), toInstant(row.get("occurred_at"))))
                .toList();
        return new PageResult<>(items, total, page.page(), page.pageSize());
    }

    /** S-LRN-05：学过的路线及进度；指定路线时即使没学过也返回该路线 */
    public List<RouteProgressItem> routeProgress(Long routeId) {
        List<RouteProgressItem> result = new ArrayList<>();
        for (RouteCard card : routes.publishedRoutes()) {
            if (routeId != null && !routeId.equals(card.id())) {
                continue;
            }
            RouteDetail detail = routes.routeDetail(card.id());
            if (detail == null) {
                continue;
            }
            List<CourseItem> all = detail.stages().stream().flatMap(stage -> stage.courses().stream()).toList();
            int learned = (int) all.stream().filter(CourseItem::isLearned).count();
            if (learned == 0 && routeId == null) {
                continue;
            }
            int mastered = (int) all.stream().filter(CourseItem::isMastered).count();
            int configured = (int) all.stream().filter(course -> course.practiceQuestionCount() > 0).count();
            result.add(new RouteProgressItem(card.id(), card.name(), card.intro(), card.stages(),
                    new RouteProgress(learned, all.size(), mastered, configured)));
        }
        return result;
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime().toInstant(ZoneOffset.UTC);
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.toInstant(ZoneOffset.UTC);
        }
        return null;
    }

    public record TrackItem(String recordType, Long courseId, String courseTitle, Instant occurredAt) {
    }

    public record RouteProgress(int learnedCourseCount, int totalCourseCount,
                                int masteredCourseCount, int configuredCourseCount) {
    }

    public record RouteProgressItem(Long id, String name, String intro,
                                    List<RouteDirectory.StageBrief> stages, RouteProgress progress) {
    }
}
