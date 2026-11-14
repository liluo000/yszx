package com.yunshuzhixue.modules.question.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 刷题题目的只读查询，供纠错与反馈显示题干、错题本展示题目最新内容使用 */
public interface PracticeQuestionDirectory {

    record QuestionRef(Long id, Long courseId, String stem) {
    }

    /**
     * 题目最新内容：错题本按最新版本展示题干、选项、答案与解析（需求 5.5）。
     * options、correctAnswer 为原始 JSON 结构，由调用方直接回给前端。
     */
    record QuestionDetail(Long id, Long courseId, String questionType, String stem, Object options,
                          Object correctAnswer, String referenceAnswer, String analysis, String status) {
    }

    /** 不存在（已删除）的题目不返回 */
    Map<Long, QuestionRef> find(Collection<Long> questionIds);

    /** 批量取题目最新内容，不存在（已删除）的题目不返回 */
    Map<Long, QuestionDetail> details(Collection<Long> questionIds);

    /** 课程下的已上架题目 ID，按 sort_no、id 升序；顺序练习按此顺序作答 */
    List<Long> questionIdsOfCourse(Long courseId);
}
