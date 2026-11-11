package com.yunshuzhixue.modules.audit.api;

/**
 * 记录后台操作日志，供其他模块调用（需求 6.11）。
 */
public interface OperationLogRecorder {

    void record(OperationLogCommand command);
}
