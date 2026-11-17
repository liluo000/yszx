package com.yunshuzhixue.modules.interview.service;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 超过时长上限自动结束面试并按已作答部分生成报告（需求 5.6.4） */
@Component
public class InterviewTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(InterviewTimeoutScheduler.class);
    private final JdbcTemplate jdbc;
    private final InterviewService interviews;

    public InterviewTimeoutScheduler(JdbcTemplate jdbc, InterviewService interviews) {
        this.jdbc = jdbc;
        this.interviews = interviews;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void autoEndExpired() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, student_id FROM interview_session"
                + " WHERE status = 'in_progress' AND deadline_at <= NOW(3) LIMIT 50");
        for (Map<String, Object> row : rows) {
            try {
                interviews.endByTimeout(((Number) row.get("student_id")).longValue(),
                        ((Number) row.get("id")).longValue());
            } catch (RuntimeException e) {
                log.warn("面试超时自动结束失败 sessionId={}: {}", row.get("id"), e.getMessage());
            }
        }
    }
}
