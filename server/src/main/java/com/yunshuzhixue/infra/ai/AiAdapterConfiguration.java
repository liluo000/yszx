package com.yunshuzhixue.infra.ai;

import com.yunshuzhixue.modules.ai.api.InterviewAi;
import com.yunshuzhixue.modules.ai.api.ShortAnswerScorer;
import com.yunshuzhixue.modules.ai.service.DevInterviewAi;
import com.yunshuzhixue.modules.ai.service.DevShortAnswerScorer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 能力厂商适配装配（架构 T05）：有百炼凭证时用官方 SDK 实现，
 * 凭证为空（本地默认）时回退 M14 的本地占位实现，保证离线可运行。
 *
 * <p>两个互斥 Condition 保证每种能力恰好注册一个实现，不依赖 bean 处理顺序。
 * 业务模块只依赖 modules.ai.api 接口，不感知这里选了哪家厂商。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DashScopeProperties.class)
public class AiAdapterConfiguration {

    @Bean
    @Conditional(DashScopeAvailableCondition.class)
    InterviewAi dashScopeInterviewAi(DashScopeProperties properties, JsonMapper json) {
        return new DashScopeInterviewAi(properties, json);
    }

    @Bean
    @Conditional(DashScopeMissingCondition.class)
    InterviewAi devInterviewAi() {
        return new DevInterviewAi();
    }

    @Bean
    @Conditional(DashScopeAvailableCondition.class)
    ShortAnswerScorer dashScopeShortAnswerScorer(DashScopeProperties properties, JsonMapper json) {
        return new DashScopeShortAnswerScorer(properties, json);
    }

    @Bean
    @Conditional(DashScopeMissingCondition.class)
    ShortAnswerScorer devShortAnswerScorer() {
        return new DevShortAnswerScorer();
    }
}
