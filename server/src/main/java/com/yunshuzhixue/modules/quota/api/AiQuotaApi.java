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

    /** 模拟考试开考时预留 1 次简答题评分次数；次数不足返回 false（同一场次幂等） */
    boolean reserveScoring(long studentId, long examSessionId);

    /** 主动放弃或评分失败时释放预留次数（未预留过则无操作） */
    void releaseScoring(long studentId, long examSessionId);

    /** 交卷评分成功后把预留转为正式扣减；返回 false 表示次数不足 */
    boolean consumeReservedScoring(long studentId, long examSessionId);

    /**
     * 退还一次面试次数（AI 服务异常结束面试时使用，需求 8.6）。
     * 次数归属开始当天，因此要按当时的 quotaDate 回退；同一业务重复调用无副作用。
     */
    void refundInterview(long studentId, java.time.LocalDate quotaDate, long interviewSessionId);
}
