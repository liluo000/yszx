package com.yunshuzhixue.modules.content.api;

import java.util.Collection;
import java.util.Map;

/**
 * 课程学习状态扩展接口（02.整体架构设计.md 4.3 的扩展接口模式）。
 * 状态归 M08，M05 只定义接口并由 M08 实现，避免 content 与 learning 形成循环依赖。
 * 未接入实现时视为全是未学、未掌握。
 */
public interface CourseStateProvider {

    record State(boolean learned, boolean mastered) {
    }

    /** 当前学员在这些课程上的状态；没有记录的课程不返回 */
    Map<Long, State> ofStudent(long studentId, Collection<Long> courseIds);
}
