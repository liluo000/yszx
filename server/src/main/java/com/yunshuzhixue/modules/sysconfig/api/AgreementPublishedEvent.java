package com.yunshuzhixue.modules.sysconfig.api;

/**
 * 协议发布了新版本。学员需重新同意，并由帮助与协议模块给状态正常的学员发站内信（需求 5.1.2、5.10）。
 * 学员端开发前暂无订阅方。
 */
public record AgreementPublishedEvent(String agreementType, int versionNo) {
}
