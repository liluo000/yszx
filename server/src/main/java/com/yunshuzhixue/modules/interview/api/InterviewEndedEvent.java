package com.yunshuzhixue.modules.interview.api;

/** M11 发布、M12 订阅：面试结束后生成报告（架构 4.3） */
public record InterviewEndedEvent(long studentId, long interviewSessionId, String endReason, boolean quotaRefunded) {
}
