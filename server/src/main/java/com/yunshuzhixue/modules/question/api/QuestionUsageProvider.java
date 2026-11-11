package com.yunshuzhixue.modules.question.api;

import java.util.Collection;
import java.util.Set;

/**
 * 题目使用情况的扩展接口：刷题与考试（M09）提供"已被作答"，AI 面试（M11）提供"已被面试抽取"。
 * 题库不依赖这两个模块，由它们实现；未实现时视为没有使用，题目可以删除（需求 6.4、6.5、8.7）。
 */
public interface QuestionUsageProvider {

    default Set<Long> answeredPracticeQuestions(Collection<Long> questionIds) {
        return Set.of();
    }

    default Set<Long> drawnInterviewQuestions(Collection<Long> questionIds) {
        return Set.of();
    }
}
