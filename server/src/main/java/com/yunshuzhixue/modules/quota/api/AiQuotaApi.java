package com.yunshuzhixue.modules.quota.api;

import java.time.LocalDate;

/**
 * AI 次数（M13）：供 M09 刷题、M11 面试查询与扣减（02.整体架构设计.md 4.1）。
 * 次数归属日期按北京时间，扣减通过 ai_quota_ledger 唯一键保证同一业务只扣一次。
 */
public interface AiQuotaApi {

    record QuotaView(LocalDate quotaDate, int interviewLimit, int interviewRemaining,
                     int scoringLimit, int scoringRemaining) {
    }

    /** 当日次数视图，不存在则初始化一行 */
    QuotaView current(long studentId);

    /**
     * 扣减一次。quotaType 为 interview 或 scoring；businessType/businessId 用于幂等。
     * 次数不足时返回 false 且不产生流水；同一业务重复调用返回 true（幂等）。
     */
    boolean charge(long studentId, String quotaType, String businessType, long businessId);
}
