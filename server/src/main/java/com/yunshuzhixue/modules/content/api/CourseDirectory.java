package com.yunshuzhixue.modules.content.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 课程的只读查询，供题库等模块关联课程使用（02.整体架构设计.md 4.3：只通过公开接口访问） */
public interface CourseDirectory {

    record CourseRef(Long id, String title, String status) {
    }

    /** 学科引用，用于错题本按学科筛选与卡片展示 */
    record SubjectRef(Long id, String name) {
    }

    /** 按 ID 批量查询，不存在的不返回 */
    Map<Long, CourseRef> find(Collection<Long> courseIds);

    /** 课程所属学科，按课程内排序号升序；没有学科的课程不返回 */
    Map<Long, List<SubjectRef>> subjectsOf(Collection<Long> courseIds);

    /** 属于该学科的课程 ID（课程可属于多个学科） */
    Set<Long> courseIdsOfSubject(Long subjectId);

    /** 全部课程，用于批量导入时按名称匹配 */
    List<CourseRef> all();
}
