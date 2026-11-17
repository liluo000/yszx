package com.yunshuzhixue.modules.interview.api;

import java.util.Map;

/** M12 通过公开接口把逐题点评与分阶段得分写回 M11 的表（架构 4.3） */
public interface InterviewScoreWriter {

    record QuestionScore(int score, String comment, String missingPoints) {
    }

    void write(long sessionId, Map<Integer, QuestionScore> questionScores, Map<Long, Double> stageScores);
}
