package com.yunshuzhixue.modules.sysconfig.service;

import com.yunshuzhixue.common.web.ClientIps;
import com.yunshuzhixue.modules.audit.api.AuditModules;
import com.yunshuzhixue.modules.audit.api.OperationLogCommand;
import com.yunshuzhixue.modules.audit.api.OperationLogRecorder;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import org.springframework.stereotype.Component;

/** 系统配置的变更都记入操作日志（需求 6.11） */
@Component
class ConfigAudit {

    private final OperationLogRecorder recorder;

    ConfigAudit(OperationLogRecorder recorder) {
        this.recorder = recorder;
    }

    void record(CurrentAdmin operator, String action, String targetType, Long targetId, String targetLabel) {
        String label = targetLabel != null && targetLabel.length() > 200 ? targetLabel.substring(0, 200) : targetLabel;
        recorder.record(new OperationLogCommand(operator.id(), operator.name(), operator.role(),
                AuditModules.SYS_CONFIG, action, targetType, targetId, label, ClientIps.current()));
    }
}
