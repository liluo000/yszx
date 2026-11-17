package com.yunshuzhixue.modules.learning.api;

/** 上次学习位置（M08）：供首页「继续学习」读取，表 student_learning_position 归 M08 */
public interface LearningPositionApi {

    record Position(long learningRouteId, long routeStageId, long courseId) {
    }

    /** 没有学习记录时返回 null */
    Position current(long studentId);
}
