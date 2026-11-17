package com.yunshuzhixue.modules.account.api;

import java.util.List;

/**
 * 学员已注册（含验证码登录时的自动注册）。
 *
 * <p>M01 发布本事件，M17 订阅后在同一事务内写入协议同意记录（02.整体架构设计.md 4.3），
 * 这样 M01 不需要依赖 M17。</p>
 */
public record StudentRegisteredEvent(Long studentId, List<AgreedAgreement> agreements) {

    /** 注册时勾选并提交的协议版本 */
    public record AgreedAgreement(String agreementType, int versionNo) {
    }
}
