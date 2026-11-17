package com.yunshuzhixue.infra.ai;

import com.yunshuzhixue.modules.ai.api.ShortAnswerScorer;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 基于阿里云百炼官方 SDK 的简答题评分（架构 T05）。
 *
 * <p>仅在凭证配置成功时由条件装配注册；提示词放 prompts/v1，模型输出为严格 JSON，
 * 解析失败或调用异常统一转成 {@link UnavailableException}，调用方按需求 8.6
 * 进入 scoring_pending 并允许重新评分。</p>
 */
public class DashScopeShortAnswerScorer implements ShortAnswerScorer {

    private static final String PROMPT = "short-answer-score.txt";
    private final DashScopeClient client;
    private final JsonMapper json;

    public DashScopeShortAnswerScorer(DashScopeProperties properties, JsonMapper json) {
        this.client = new DashScopeClient(properties);
        this.json = json;
    }

    @Override
    public Score score(String stem, String referenceAnswer, String analysis, String studentAnswer) {
        String text;
        try {
            text = client.generateOne(PROMPT, Map.of(
                    "stem", safe(stem),
                    "referenceAnswer", safe(referenceAnswer),
                    "analysis", safe(analysis),
                    "studentAnswer", safe(studentAnswer)));
        } catch (DashScopeException e) {
            throw new UnavailableException(e.getMessage());
        }
        try {
            JsonNode node = json.readTree(stripCodeFence(text));
            int score = Math.max(0, Math.min(100, node.path("score").asInt()));
            String comment = node.path("comment").asString("");
            return new Score(score, comment);
        } catch (RuntimeException e) {
            UnavailableException ex = new UnavailableException("评分结果解析失败");
            ex.initCause(e);
            throw ex;
        }
    }

    private static String stripCodeFence(String text) {
        String value = text.trim();
        if (value.startsWith("```")) {
            int firstLine = value.indexOf('\n');
            if (firstLine > 0) {
                value = value.substring(firstLine + 1);
            }
            int fence = value.lastIndexOf("```");
            if (fence >= 0) {
                value = value.substring(0, fence);
            }
        }
        return value.trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
