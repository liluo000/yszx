package com.yunshuzhixue.modules.practice.service;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 到时自动交卷（需求 5.4.4）：每分钟扫描已过截止时间、仍作答中的场次 */
@Component
public class ExamAutoSubmitScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExamAutoSubmitScheduler.class);

    private final JdbcTemplate jdbc;
    private final ExamService exams;

    public ExamAutoSubmitScheduler(JdbcTemplate jdbc, ExamService exams) {
        this.jdbc = jdbc;
        this.exams = exams;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void autoSubmitExpired() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, student_id FROM exam_session"
                + " WHERE status = 'in_progress' AND deadline_at <= NOW(3) LIMIT 50");
        for (Map<String, Object> row : rows) {
            try {
                exams.submitByTimeout(((Number) row.get("student_id")).longValue(),
                        ((Number) row.get("id")).longValue());
            } catch (RuntimeException e) {
                log.warn("到时自动交卷失败 examId={}: {}", row.get("id"), e.getMessage());
            }
        }
    }
}
