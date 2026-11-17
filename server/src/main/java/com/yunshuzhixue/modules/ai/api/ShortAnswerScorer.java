package com.yunshuzhixue.modules.ai.api;

/**
 * 简答题评分（M14）：M09 模拟考试交卷时调用。
 * M14 的真实大模型适配尚未接入，当前由本模块提供本地可运行的占位实现。
 */
public interface ShortAnswerScorer {

    record Score(int score, String comment) {
    }

    /** AI 服务不可用时抛出，调用方按需求 8.6 进入 scoring_pending */
    class UnavailableException extends RuntimeException {
        public UnavailableException(String message) {
            super(message);
        }
    }

    /**
     * 对照参考答案与解析给学员答案打百分制分数并给出点评。
     *
     * @throws UnavailableException 评分服务失败或超时
     */
    Score score(String stem, String referenceAnswer, String analysis, String studentAnswer);
}
