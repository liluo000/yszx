package com.yunshuzhixue.modules.question.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 面试题库只读目录（M06 提供，M11 抽题使用；架构 4.3 的扩展接口模式） */
public interface InterviewQuestionDirectory {

    record Question(Long id, Long courseId, String stem, String referenceAnswer,
                    String scoringPoints, String followupDirection) {
    }

    /** 给定课程下的全部已上架面试题，按题目 ID 升序 */
    List<Question> questionsOfCourses(Collection<Long> courseIds);

    /** 各课程已上架面试题数量，用于阶段可用性判断 */
    Map<Long, Long> countsOfCourses(Collection<Long> courseIds);
}
