package com.yunshuzhixue.modules.ai.service;

import com.yunshuzhixue.modules.ai.api.ShortAnswerScorer;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 本地开发用的简答题评分占位实现：按中文字符二元组与参考答案的重合度估分。
 * 只用于让模拟考试闭环可运行；真实 AI 评分接入后替换本实现即可（02.整体架构设计.md T05）。
 *
 * <p>由 infra.ai 的 AiAdapterConfiguration 按凭证有无条件注册，不直接标注 @Service。</p>
 */
public class DevShortAnswerScorer implements ShortAnswerScorer {

    @Override
    public Score score(String stem, String referenceAnswer, String analysis, String studentAnswer) {
        if (studentAnswer == null || studentAnswer.isBlank()) {
            return new Score(0, "未作答，未覆盖参考答案中的要点。");
        }
        Set<String> reference = grams(referenceAnswer == null ? "" : referenceAnswer);
        Set<String> answer = grams(studentAnswer);
        if (reference.isEmpty()) {
            return new Score(60, "已作答；参考答案未配置，按及格线计分。");
        }
        long hit = reference.stream().filter(answer::contains).count();
        double ratio = (double) hit / reference.size();
        int score = (int) Math.round(Math.min(100, ratio * 100));
        String comment = score >= 80
                ? "回答覆盖了参考答案的主要要点。"
                : "回答覆盖参考答案要点约 " + Math.round(ratio * 100) + "%，可对照参考答案补充缺失要点。";
        return new Score(score, comment);
    }

    /** 中文按相邻字符二元组、英文按单词，粗略衡量要点覆盖 */
    private static Set<String> grams(String text) {
        String normalized = text.toLowerCase().replaceAll("[\\s\\p{Punct}，。；：、（）\"'`]+", "");
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i + 1 < normalized.length(); i++) {
            String pair = normalized.substring(i, i + 2);
            if (!pair.isBlank()) {
                result.add(pair);
            }
        }
        if (result.isEmpty() && !normalized.isBlank()) {
            result.add(normalized);
        }
        return result;
    }
}
