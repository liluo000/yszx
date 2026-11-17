package com.yunshuzhixue.infra.ai;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** 百炼凭证不可用（本地开发默认），启用 M14 的本地占位实现 */
public class DashScopeMissingCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String key = context.getEnvironment().getProperty(DashScopeAvailableCondition.API_KEY_PROPERTY);
        return key == null || key.isBlank();
    }
}
