package com.yunshuzhixue.modules.safety.api;

/** M14 内容安全：敏感词匹配、替换与命中日志（需求 7.6） */
public interface SensitiveTextFilter {

    String SCENE_TEXT_INTERVIEW = "text_interview";
    String SCENE_VOICE_INTERVIEW = "voice_interview";
    String SCENE_SHORT_ANSWER_SCORING = "short_answer_scoring";

    /**
     * 学员输入检查：命中返回 true（调用方拦截，不消耗 AI 次数），并写命中日志。
     */
    boolean studentInputHits(long studentId, String scene, Long interviewSessionId, String text);

    /**
     * AI 输出过滤：命中词替换为等长 *，流程不中断，并写命中日志。
     */
    String maskAiOutput(long studentId, String scene, Long interviewSessionId, String text);
}
