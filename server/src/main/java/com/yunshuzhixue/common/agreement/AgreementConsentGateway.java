package com.yunshuzhixue.common.agreement;

import java.util.List;

/**
 * 协议同意状态查询（02.整体架构设计.md 4.3 的扩展接口模式）。
 *
 * <p>同意记录表归 M17，但 M01 在返回本人信息时需要给出「尚未同意的协议」，M01 又不能依赖 M17
 * （M17 已依赖 M01）。因此接口放在 common，由 M17 实现、M01 注入使用。</p>
 */
public interface AgreementConsentGateway {

    /** 学员尚未同意的最新协议；后台未发布该类协议时不计入 */
    List<Pending> pending(long studentId);

    record Pending(String agreementType, int versionNo) {
    }
}
