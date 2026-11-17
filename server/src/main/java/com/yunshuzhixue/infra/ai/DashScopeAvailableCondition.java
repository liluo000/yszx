package com.yunshuzhixue.infra.ai;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 百炼凭证是否可用：属性值存在且非空白才启用 SDK 适配器。
 *
 * <p>不能直接用 @ConditionalOnProperty，因为 application.yml 里用 ${DASHSCOPE_API_KEY:}
 * 兜底时属性会存在但为空串，OnProperty 只判断存在会误启用；这里显式检查非空白。</p>
 */
public class DashScopeAvailableCondition implements Condition {

    static final String API_KEY_PROPERTY = "yunshu.ai.dashscope.api-key";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String key = context.getEnvironment().getProperty(API_KEY_PROPERTY);
        return key != null && !key.isBlank();
    }
}
