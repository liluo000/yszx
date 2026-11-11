package com.yunshuzhixue.modules.audit.api;

/**
 * 一条操作日志。学员类操作对象只填写 ID，不得在 targetLabel 中写入昵称和手机号（03.数据库设计.md 7.2）。
 */
public record OperationLogCommand(
        Long sysUserId,
        String sysUserName,
        String role,
        String module,
        String action,
        String targetType,
        Long targetId,
        String targetLabel,
        String ipAddress) {
}
