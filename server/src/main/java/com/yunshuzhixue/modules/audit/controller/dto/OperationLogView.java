package com.yunshuzhixue.modules.audit.controller.dto;

import com.yunshuzhixue.common.time.UtcTime;
import com.yunshuzhixue.modules.audit.entity.OperationLog;
import java.time.Instant;

public record OperationLogView(Long id, Long sysUserId, String sysUserName, String role, String module,
                               String action, String targetType, Long targetId, String targetLabel,
                               String ipAddress, Instant createdAt) {

    public static OperationLogView of(OperationLog l) {
        return new OperationLogView(l.getId(), l.getSysUserId(), l.getSysUserName(), l.getRole(), l.getModule(),
                l.getAction(), l.getTargetType(), l.getTargetId(), l.getTargetLabel(), l.getIpAddress(),
                UtcTime.toInstant(l.getCreatedAt()));
    }
}
