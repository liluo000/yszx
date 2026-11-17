package com.yunshuzhixue.modules.practice.api;

import java.util.Collection;
import java.util.Map;

/**
 * 错题记录的只读目录：M09 错题重做（S-PRC-01）需要把错题记录 ID 解析成题目 ID。
 * M10 错题本依赖 M09 的事件，因此接口定义在 M09，由 M10 实现（与 CourseStateProvider 同一模式）。
 */
public interface WrongQuestionDirectory {

    /** 错题记录 ID → 题目 ID；只返回属于该学员的记录，不存在的记录不出现在结果里 */
    Map<Long, Long> questionIdsOf(long studentId, Collection<Long> recordIds);
}
