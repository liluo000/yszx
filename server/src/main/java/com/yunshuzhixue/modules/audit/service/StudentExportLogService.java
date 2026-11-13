package com.yunshuzhixue.modules.audit.service;

import com.yunshuzhixue.modules.audit.api.StudentExportLogRecorder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class StudentExportLogService implements StudentExportLogRecorder {

    private final JdbcTemplate jdbc;

    public StudentExportLogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(Long sysUserId, String sysUserName, String filterConditionsJson, int exportedCount) {
        // filter_conditions 为 JSON 列，按文本写入后由数据库转换
        jdbc.update("INSERT INTO student_export_log (sys_user_id, sys_user_name, filter_conditions, exported_count)"
                + " VALUES (?, ?, CAST(? AS JSON), ?)", sysUserId, sysUserName, filterConditionsJson, exportedCount);
    }
}
