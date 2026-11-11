package com.yunshuzhixue.modules.audit.service;

import com.yunshuzhixue.common.time.UtcTime;
import com.yunshuzhixue.modules.audit.api.OperationLogCommand;
import com.yunshuzhixue.modules.audit.api.OperationLogRecorder;
import com.yunshuzhixue.modules.audit.entity.OperationLog;
import com.yunshuzhixue.modules.audit.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;

@Service
class OperationLogService implements OperationLogRecorder {

    private final OperationLogMapper mapper;

    OperationLogService(OperationLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(OperationLogCommand c) {
        OperationLog log = new OperationLog();
        log.setSysUserId(c.sysUserId());
        log.setSysUserName(c.sysUserName());
        log.setRole(c.role());
        log.setModule(c.module());
        log.setAction(c.action());
        log.setTargetType(c.targetType());
        log.setTargetId(c.targetId());
        log.setTargetLabel(c.targetLabel());
        log.setIpAddress(c.ipAddress());
        log.setCreatedAt(UtcTime.now());
        mapper.insert(log);
    }
}
