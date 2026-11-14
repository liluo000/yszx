package com.yunshuzhixue.modules.quota.service;

import com.yunshuzhixue.common.time.BeijingDates;
import com.yunshuzhixue.modules.quota.api.AiQuotaApi;
import com.yunshuzhixue.modules.sysconfig.api.SysConfigReader;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** AI 次数管理（需求 7.1）：按北京时间日期计数，流水唯一键保证幂等 */
@Service
public class AiQuotaService implements AiQuotaApi {

    private final JdbcTemplate jdbc;
    private final SysConfigReader configs;

    public AiQuotaService(JdbcTemplate jdbc, SysConfigReader configs) {
        this.jdbc = jdbc;
        this.configs = configs;
    }

    @Override
    public QuotaView current(long studentId) {
        LocalDate today = LocalDate.now(BeijingDates.ZONE);
        ensureRow(studentId, today);
        Map<String, Object> row = jdbc.queryForMap("SELECT interview_used, scoring_used, scoring_reserved"
                + " FROM ai_quota_daily WHERE student_id = ? AND quota_date = ?", studentId, today);
        int interviewLimit = configs.getInt("ai.interview_daily_limit");
        int scoringLimit = configs.getInt("ai.scoring_daily_limit");
        int interviewUsed = number(row.get("interview_used"));
        int scoringUsed = number(row.get("scoring_used"));
        int scoringReserved = number(row.get("scoring_reserved"));
        return new QuotaView(today, interviewLimit, Math.max(0, interviewLimit - interviewUsed),
                scoringLimit, Math.max(0, scoringLimit - scoringUsed - scoringReserved));
    }

    @Override
    @Transactional
    public boolean charge(long studentId, String quotaType, String businessType, long businessId) {
        boolean scoring = "scoring".equals(quotaType);
        if (!scoring && !"interview".equals(quotaType)) {
            throw new IllegalArgumentException("次数类型不正确：" + quotaType);
        }
        LocalDate today = LocalDate.now(BeijingDates.ZONE);
        ensureRow(studentId, today);
        int limit = configs.getInt(scoring ? "ai.scoring_daily_limit" : "ai.interview_daily_limit");

        // 同一业务重复提交时唯一键冲突，INSERT IGNORE 直接视为已扣过
        int inserted = jdbc.update("INSERT IGNORE INTO ai_quota_ledger"
                + " (student_id, quota_date, quota_type, action, business_type, business_id)"
                + " VALUES (?, ?, ?, 'charge', ?, ?)", studentId, today, quotaType, businessType, businessId);
        if (inserted == 0) {
            return true;
        }

        String column = scoring ? "scoring_used" : "interview_used";
        String reserved = scoring ? "scoring_reserved" : "0";
        int updated = jdbc.update("UPDATE ai_quota_daily SET " + column + " = " + column + " + 1"
                        + " WHERE student_id = ? AND quota_date = ? AND " + column + " + " + reserved + " < ?",
                studentId, today, limit);
        if (updated == 0) {
            // 次数不足：撤销刚写入的流水，避免重复提交时误判为已扣过
            jdbc.update("DELETE FROM ai_quota_ledger WHERE business_type = ? AND business_id = ?"
                    + " AND quota_type = ? AND action = 'charge'", businessType, businessId, quotaType);
            return false;
        }
        return true;
    }

    private void ensureRow(long studentId, LocalDate date) {
        jdbc.update("INSERT IGNORE INTO ai_quota_daily (student_id, quota_date) VALUES (?, ?)", studentId, date);
    }

    private static int number(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }
}
