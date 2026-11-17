package com.yunshuzhixue.infra.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云百炼（DashScope）厂商配置（架构 T05）。
 *
 * <p>凭证来自环境变量 DASHSCOPE_API_KEY，本地开发未配置时 {@link #enabled()} 为 false，
 * 由条件装配回退到 M14 的本地占位实现，保证离线可运行。model、temperature 等参数
 * 集中在这里，便于 M3 实测多家厂商后调参。</p>
 */
@ConfigurationProperties(prefix = "yunshu.ai.dashscope")
public record DashScopeProperties(
        String apiKey,
        String model,
        Float temperature,
        Integer maxTokens,
        Integer timeoutSeconds) {

    /** 凭证缺失视为未启用，自动回退本地占位 */
    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String model() {
        return model == null || model.isBlank() ? "qwen-plus" : model;
    }

    public Float temperature() {
        return temperature == null ? 0.7f : temperature;
    }

    public Integer maxTokens() {
        return maxTokens == null ? 1024 : maxTokens;
    }

    public Integer timeoutSeconds() {
        return timeoutSeconds == null ? 30 : timeoutSeconds;
    }
}
