package com.yunshuzhixue.modules.question.api;

import java.util.Collection;
import java.util.Map;

/** 刷题题目的只读查询，供纠错与反馈显示题干使用 */
public interface PracticeQuestionDirectory {

    record QuestionRef(Long id, Long courseId, String stem) {
    }

    /** 不存在（已删除）的题目不返回 */
    Map<Long, QuestionRef> find(Collection<Long> questionIds);
}
