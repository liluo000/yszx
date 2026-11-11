package com.yunshuzhixue.modules.content.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 课程的只读查询，供题库等模块关联课程使用（02.整体架构设计.md 4.3：只通过公开接口访问） */
public interface CourseDirectory {

    record CourseRef(Long id, String title, String status) {
    }

    /** 按 ID 批量查询，不存在的不返回 */
    Map<Long, CourseRef> find(Collection<Long> courseIds);

    /** 属于该学科的课程 ID（课程可属于多个学科） */
    Set<Long> courseIdsOfSubject(Long subjectId);

    /** 全部课程，用于批量导入时按名称匹配 */
    List<CourseRef> all();
}
