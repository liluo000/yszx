package com.yunshuzhixue.modules.learning.service;

import com.yunshuzhixue.modules.learning.api.LearningPositionApi;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 上次学习位置（S-PTL-01 的 continue_learning 数据源） */
@Service
public class LearningPositionService implements LearningPositionApi {

    private final JdbcTemplate jdbc;

    public LearningPositionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Position current(long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT learning_route_id, route_stage_id, course_id"
                + " FROM student_learning_position WHERE student_id = ?", studentId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        return new Position(((Number) row.get("learning_route_id")).longValue(),
                ((Number) row.get("route_stage_id")).longValue(),
                ((Number) row.get("course_id")).longValue());
    }
}
