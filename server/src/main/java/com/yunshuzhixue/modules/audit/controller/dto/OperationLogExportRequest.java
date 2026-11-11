package com.yunshuzhixue.modules.audit.controller.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** 导出操作日志的筛选条件，与列表筛选一致（A-AUD-02） */
public record OperationLogExportRequest(
        @Positive(message = "操作人不正确") Long sysUserId,
        @Size(max = 50, message = "模块最多 50 个字符") String module,
        LocalDate startDate,
        LocalDate endDate) {
}
