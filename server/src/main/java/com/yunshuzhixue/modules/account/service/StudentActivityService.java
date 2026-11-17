package com.yunshuzhixue.modules.account.service;

import com.yunshuzhixue.common.time.BeijingDates;
import com.yunshuzhixue.modules.account.api.StudentActivityRecorder;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 学员当日活跃与最近活跃时间（表名归 M01） */
@Service
public class StudentActivityService implements StudentActivityRecorder {

    private final JdbcTemplate jdbc;

    public StudentActivityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(long studentId) {
        LocalDate today = LocalDate.now(BeijingDates.ZONE);
        jdbc.update("INSERT IGNORE INTO student_daily_activity (student_id, activity_date) VALUES (?, ?)",
                studentId, today);
        jdbc.update("UPDATE student SET last_active_at = NOW(3) WHERE id = ?", studentId);
    }
}
