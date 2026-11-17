package com.yunshuzhixue.modules.interview.api;

import java.time.Instant;
import java.util.List;

/** M11 提供给 M12 的只读目录：报告与记录不直接访问 M11 的表（架构 4.3） */
public interface InterviewDirectory {

    record RecordItem(long id, Instant startedAt, String routeName, List<String> stageNames,
                      String interviewMode, String status, String endReason, Instant deadlineAt,
                      boolean isRouteOffShelf) {
    }

    record StageItem(long routeStageId, String stageName, int questionCount, Double score) {
    }

    record QuestionItem(long rowId, int seqNo, long routeStageId, long courseId, String stem,
                        String referenceAnswer, String scoringPoints, String answerStatus,
                        int followupCount, List<String> answers, Integer score, String aiComment,
                        String missingPoints) {
    }

    record MessageItem(int seqNo, long questionRowId, String speaker, String messageType,
                       String content, Instant createdAt) {
    }

    record ReportData(long sessionId, long studentId, String routeName, String interviewMode,
                      String endReason, Instant startedAt, int mainQuestionCount,
                      List<StageItem> stages, List<QuestionItem> questions, List<MessageItem> messages) {
    }

    /** 面试记录列表（按开始时间倒序） */
    List<RecordItem> records(long studentId, long offset, int limit);

    long countRecords(long studentId);

    /** 生成报告所需的场次、题目快照与全部学员回答 */
    ReportData reportData(long sessionId);
}
