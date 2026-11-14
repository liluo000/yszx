package com.yunshuzhixue.modules.content.service;

import com.yunshuzhixue.modules.content.api.CourseUsageProvider;
import com.yunshuzhixue.modules.content.api.RouteDirectory;
import com.yunshuzhixue.modules.sysconfig.api.SysConfigReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 已上架路线与课程的只读目录：路线卡片、路线详情（含题量可用性）、搜索 */
@Service
public class RouteDirectoryService implements RouteDirectory {

    private static final String ON_SHELF = "on_shelf";

    private final JdbcTemplate jdbc;
    private final CourseUsageProvider usage;
    private final SysConfigReader configs;

    public RouteDirectoryService(JdbcTemplate jdbc, CourseUsageProvider usage, SysConfigReader configs) {
        this.jdbc = jdbc;
        this.usage = usage;
        this.configs = configs;
    }

    @Override
    public List<RouteCard> publishedRoutes() {
        Map<Long, String[]> routes = new LinkedHashMap<>();
        Map<Long, List<StageBrief>> stages = new LinkedHashMap<>();
        jdbc.query("SELECT r.id AS route_id, r.name AS route_name, r.intro,"
                + " st.id AS stage_id, st.name AS stage_name, COUNT(c.id) AS course_count"
                + " FROM learning_route r"
                + " LEFT JOIN route_stage st ON st.learning_route_id = r.id"
                + " LEFT JOIN stage_course sc ON sc.route_stage_id = st.id"
                + " LEFT JOIN course c ON c.id = sc.course_id AND c.status = 'on_shelf'"
                + " WHERE r.status = 'on_shelf'"
                + " GROUP BY r.id, r.name, r.intro, r.sort_no, st.id, st.name, st.sort_no"
                + " ORDER BY r.sort_no, r.id, st.sort_no, st.id", rs -> {
                    long routeId = rs.getLong("route_id");
                    String routeName = rs.getString("route_name");
                    String intro = rs.getString("intro");
                    routes.computeIfAbsent(routeId, key -> new String[] {blankToNull(routeName), blankToNull(intro)});
                    long stageId = rs.getLong("stage_id");
                    if (!rs.wasNull()) {
                        stages.computeIfAbsent(routeId, key -> new ArrayList<>())
                                .add(new StageBrief(stageId, rs.getString("stage_name"), rs.getLong("course_count")));
                    }
                });
        List<RouteCard> cards = new ArrayList<>(routes.size());
        routes.forEach((id, meta) -> cards.add(new RouteCard(id, meta[0], meta[1], stages.getOrDefault(id, List.of()))));
        return cards;
    }

    @Override
    public RouteDetail routeDetail(Long routeId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, name, intro FROM learning_route WHERE id = ? AND status = 'on_shelf'", routeId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> route = rows.get(0);
        List<Map<String, Object>> stageRows = jdbc.queryForList(
                "SELECT st.id AS stage_id, st.name AS stage_name, st.intro AS stage_intro,"
                        + " c.id AS course_id, c.title, c.intro AS course_intro, c.duration_minutes,"
                        + " vp.name AS platform_name"
                        + " FROM route_stage st"
                        + " LEFT JOIN stage_course sc ON sc.route_stage_id = st.id"
                        + " LEFT JOIN course c ON c.id = sc.course_id AND c.status = 'on_shelf'"
                        + " LEFT JOIN video_platform vp ON vp.id = c.video_platform_id"
                        + " WHERE st.learning_route_id = ?"
                        + " ORDER BY st.sort_no, st.id, sc.sort_no, c.id", routeId);

        Set<Long> courseIds = new LinkedHashSet<>();
        for (Map<String, Object> row : stageRows) {
            Long courseId = number(row.get("course_id"));
            if (courseId != null) {
                courseIds.add(courseId);
            }
        }
        Map<Long, Long> practiceCounts = courseIds.isEmpty() ? Map.of() : usage.onShelfPracticeCounts(courseIds);
        Map<Long, Long> interviewCounts = courseIds.isEmpty() ? Map.of() : usage.onShelfInterviewCounts(courseIds);
        Map<Long, List<String>> subjects = subjectNames(courseIds);
        int randomMin = configs.getInt("practice.random_min_question_count");
        int examMin = configs.getInt("exam.min_question_count");

        Map<Long, StageBuilder> stages = new LinkedHashMap<>();
        for (Map<String, Object> row : stageRows) {
            Long stageId = number(row.get("stage_id"));
            if (stageId == null) {
                continue;
            }
            StageBuilder stage = stages.computeIfAbsent(stageId,
                    key -> new StageBuilder(stageId, string(row.get("stage_name")), blankToNull(string(row.get("stage_intro")))));
            Long courseId = number(row.get("course_id"));
            if (courseId == null) {
                continue;
            }
            long practice = practiceCounts.getOrDefault(courseId, 0L);
            stage.practiceCount += practice;
            stage.interviewCount += interviewCounts.getOrDefault(courseId, 0L);
            stage.courses.add(new CourseItem(courseId, string(row.get("title")),
                    blankToNull(string(row.get("course_intro"))), integer(row.get("duration_minutes")),
                    blankToNull(string(row.get("platform_name"))), subjects.getOrDefault(courseId, List.of()), practice,
                    false, false));
        }
        List<StageDetail> details = stages.values().stream()
                .map(s -> new StageDetail(s.id, s.name, s.intro, s.practiceCount >= randomMin, s.practiceCount >= examMin,
                        s.interviewCount > 0, s.courses))
                .toList();
        return new RouteDetail(number(route.get("id")), string(route.get("name")),
                blankToNull(string(route.get("intro"))), details);
    }

    @Override
    public List<RouteCard> searchRoutes(String keyword) {
        String kw = keyword.toLowerCase(Locale.ROOT);
        return publishedRoutes().stream()
                .filter(r -> contains(r.name(), kw) || contains(r.intro(), kw))
                .toList();
    }

    @Override
    public List<CourseHit> searchCourses(String keyword) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, title, intro FROM course WHERE status = 'on_shelf'"
                        + " AND (LOCATE(?, title) > 0 OR LOCATE(?, intro) > 0) ORDER BY sort_no, id",
                keyword, keyword);
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> courseIds = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            courseIds.add(number(row.get("id")));
        }
        Map<Long, List<RouteRef>> refs = routeRefs(courseIds);
        List<CourseHit> hits = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Long id = number(row.get("id"));
            hits.add(new CourseHit(id, string(row.get("title")), blankToNull(string(row.get("intro"))),
                    refs.getOrDefault(id, List.of())));
        }
        return hits;
    }

    @Override
    public Set<Long> courseIdsOfRoute(Long learningRouteId) {
        return new LinkedHashSet<>(jdbc.queryForList("SELECT DISTINCT sc.course_id FROM stage_course sc"
                + " JOIN course c ON c.id = sc.course_id AND c.status = 'on_shelf'"
                + " WHERE sc.learning_route_id = ?", Long.class, learningRouteId));
    }

    private Map<Long, List<String>> subjectNames(Collection<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> names = new LinkedHashMap<>();
        String placeholders = String.join(",", java.util.Collections.nCopies(courseIds.size(), "?"));
        for (Map<String, Object> row : jdbc.queryForList("SELECT cs.course_id, s.name FROM course_subject cs"
                + " JOIN subject s ON s.id = cs.subject_id WHERE cs.course_id IN (" + placeholders + ")"
                + " ORDER BY cs.course_id, cs.sort_no", courseIds.toArray())) {
            long courseId = ((Number) row.get("course_id")).longValue();
            names.computeIfAbsent(courseId, key -> new ArrayList<>()).add(String.valueOf(row.get("name")));
        }
        return names;
    }

    private Map<Long, List<RouteRef>> routeRefs(Collection<Long> courseIds) {
        Map<Long, List<RouteRef>> refs = new LinkedHashMap<>();
        String placeholders = String.join(",", java.util.Collections.nCopies(courseIds.size(), "?"));
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT sc.course_id, r.id AS route_id, r.name AS route_name, sc.route_stage_id"
                        + " FROM stage_course sc"
                        + " JOIN learning_route r ON r.id = sc.learning_route_id AND r.status = 'on_shelf'"
                        + " WHERE sc.course_id IN (" + placeholders + ") ORDER BY r.sort_no, r.id",
                courseIds.toArray())) {
            long courseId = ((Number) row.get("course_id")).longValue();
            refs.computeIfAbsent(courseId, key -> new ArrayList<>())
                    .add(new RouteRef(((Number) row.get("route_id")).longValue(),
                            String.valueOf(row.get("route_name")), ((Number) row.get("route_stage_id")).longValue()));
        }
        return refs;
    }

    private static boolean contains(String value, String lowerKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static Long number(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Integer integer(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    /** 组装阶段时的可变容器 */
    private static final class StageBuilder {
        private final Long id;
        private final String name;
        private final String intro;
        private final List<CourseItem> courses = new ArrayList<>();
        private long practiceCount;
        private long interviewCount;

        private StageBuilder(Long id, String name, String intro) {
            this.id = id;
            this.name = name;
            this.intro = intro;
        }
    }
}
