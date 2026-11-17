package com.yunshuzhixue.modules.learning.service;

import com.yunshuzhixue.modules.content.api.CourseStateProvider;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 课程学习状态读取（M08）：实现 M05 定义的扩展接口，供路线详情展示已学/已掌握 */
@Service
public class CourseStateService implements CourseStateProvider {

    private final JdbcTemplate jdbc;

    public CourseStateService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, State> ofStudent(long studentId, Collection<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(courseIds.size(), "?"));
        List<Object> args = new java.util.ArrayList<>();
        args.add(studentId);
        args.addAll(courseIds);
        Map<Long, State> states = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT course_id, is_learned, is_mastered"
                + " FROM student_course_state WHERE student_id = ? AND course_id IN (" + placeholders + ")",
                args.toArray())) {
            states.put(((Number) row.get("course_id")).longValue(),
                    new State(flag(row.get("is_learned")), flag(row.get("is_mastered"))));
        }
        return states;
    }

    /** TINYINT(1) 经 JDBC 返回 Boolean，兼容 Number */
    private static boolean flag(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value instanceof Number n && n.intValue() == 1;
    }
}
