package com.yunshuzhixue.modules.audit.api;

/** 学员名单导出留痕（需求 6.7.4）：操作人、时间、筛选条件、导出条数 */
public interface StudentExportLogRecorder {

    /** filterConditionsJson 为筛选条件的 JSON 文本 */
    void record(Long sysUserId, String sysUserName, String filterConditionsJson, int exportedCount);
}
