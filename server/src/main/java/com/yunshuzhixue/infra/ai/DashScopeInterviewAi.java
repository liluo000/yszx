package com.yunshuzhixue.infra.ai;

import com.yunshuzhixue.modules.ai.api.InterviewAi;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 基于阿里云百炼官方 SDK 的 AI 面试官（架构 T05）。
 *
 * <p>仅在凭证配置成功时由条件装配注册；话术由 prompts/v1 下的版本化模板生成，
 * 输出按句切分以适配 SSE 逐句下发。任何模型/网络异常都转换为
 * {@link UnavailableException}，交由业务侧按需求 8.6 重试或退还次数。</p>
 */
public class DashScopeInterviewAi implements InterviewAi {

    private static final String SYSTEM = "interview-system.txt";
    private final DashScopeClient client;
    private final JsonMapper json;

    public DashScopeInterviewAi(DashScopeProperties properties, JsonMapper json) {
        this.client = new DashScopeClient(properties);
        this.json = json;
    }

    @Override
    public List<String> opening(String routeName) {
        return say("opening.txt", Map.of("routeName", safe(routeName)));
    }

    @Override
    public List<String> mainQuestion(String stem, int seqNo, int total) {
        return say("main-question.txt", Map.of("stem", safe(stem), "seqNo", seqNo, "total", total));
    }

    @Override
    public boolean needsFollowup(String studentAnswer, int followupCount, int maxFollowup) {
        // 编排规则留在本地：回答过短才追问，避免为一次布尔判断多调一次模型
        if (followupCount >= maxFollowup || studentAnswer == null) {
            return false;
        }
        return studentAnswer.trim().length() < 120;
    }

    @Override
    public List<String> followup(String stem, String studentAnswer, int followupCount) {
        return say("followup.txt", Map.of("stem", safe(stem), "studentAnswer", safe(studentAnswer)));
    }

    @Override
    public List<String> transition(int nextSeqNo, int total) {
        return say("transition.txt", Map.of("nextSeqNo", nextSeqNo, "total", total));
    }

    @Override
    public List<String> closing() {
        return say("closing.txt", Map.of());
    }

    @Override
    public QuestionScore score(String stem, String referenceAnswer, String scoringPoints, String allAnswers) {
        String text = guarded(() -> client.generateOne("interview-score.txt", Map.of(
                "stem", safe(stem),
                "referenceAnswer", safe(referenceAnswer),
                "scoringPoints", safe(scoringPoints),
                "allAnswers", safe(allAnswers))));
        try {
            JsonNode node = json.readTree(stripCodeFence(text));
            int score = node.path("score").asInt();
            String comment = node.path("comment").asString("");
            String missing = node.path("missingPoints").asString("");
            return new QuestionScore(Math.max(0, Math.min(100, score)), comment, missing);
        } catch (RuntimeException e) {
            throw unavailable("评分结果解析失败", e);
        }
    }

    @Override
    public String advice(String stageName, double score) {
        return guarded(() -> client.generateOne("advice.txt", Map.of(
                "stageName", safe(stageName),
                "score", String.format("%.1f", score))));
    }

    @Override
    public String transcribe(byte[] audio) {
        // 本轮只接文本大模型；ASR（架构 T05）尚未选型，保持空串与占位实现一致，
        // 语音面试仍由 dev_transcript 文本帧承载转写结果。
        return "";
    }

    private List<String> say(String promptFile, Map<String, Object> variables) {
        String text = guarded(() -> client.generate(SYSTEM, promptFile, variables));
        List<String> sentences = client.toSentences(text);
        if (sentences.isEmpty()) {
            throw unavailable("模型未返回可用话术", null);
        }
        return sentences;
    }

    /** 把 infra 的调用异常统一转成面试契约的不可用异常，交给需求 8.6 处理 */
    private String guarded(java.util.function.Supplier<String> action) {
        try {
            return action.get();
        } catch (DashScopeException e) {
            throw new UnavailableException(e.getMessage());
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

    private static UnavailableException unavailable(String message, Throwable cause) {
        UnavailableException ex = new UnavailableException(message);
        if (cause != null) {
            ex.initCause(cause);
        }
        return ex;
    }
}
