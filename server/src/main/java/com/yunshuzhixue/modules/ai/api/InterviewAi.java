package com.yunshuzhixue.modules.ai.api;

import java.util.List;

/**
 * AI 面试官能力（M14，架构 T05）：厂商未定，通过本适配层接入国内已备案服务。
 * 当前由本地占位实现提供可运行闭环，接入真实大模型时替换实现即可。
 */
public interface InterviewAi {

    /** 逐题评分结果 */
    record QuestionScore(int score, String comment, String missingPoints) {
    }

    /** AI 服务不可用时抛出；调用方按需求 8.6 重试、退还次数并结束面试 */
    class UnavailableException extends RuntimeException {
        public UnavailableException(String message) {
            super(message);
        }
    }

    /** 开场自我介绍（按句切分，便于 SSE 逐句下发） */
    List<String> opening(String routeName);

    /** 主问题的播报文案 */
    List<String> mainQuestion(String stem, int seqNo, int total);

    /** 是否需要动态追问（由调用方结合追问上限判断） */
    boolean needsFollowup(String studentAnswer, int followupCount, int maxFollowup);

    /** 追问文本 */
    List<String> followup(String stem, String studentAnswer, int followupCount);

    /** 题目之间的自然过渡 */
    List<String> transition(int nextSeqNo, int total);

    /** 结束语 */
    List<String> closing();

    /** 主问题连同追问整体打分（百分制），返回点评与缺失要点 */
    QuestionScore score(String stem, String referenceAnswer, String scoringPoints, String allAnswers);

    /** 针对薄弱阶段生成学习建议 */
    String advice(String stageName, double score);

    /** 语音识别占位：真实厂商接入后返回转写文字；当前返回空串表示未识别到内容 */
    String transcribe(byte[] audio);
}
