package com.yunshuzhixue.modules.practice.api;

/**
 * 作答完成事件（02.整体架构设计.md 4.3）：M09 在同一事务内发布，M10 订阅收录/移出错题，
 * M08 后续订阅判定「已掌握」。事件放在 api 包，订阅方只依赖 M09 的公开接口。
 */
public record AnswerCompletedEvent(long studentId, long practiceQuestionId, long courseId,
                                   String questionType, Object studentAnswer, boolean correct,
                                   String practiceMode) {
}
