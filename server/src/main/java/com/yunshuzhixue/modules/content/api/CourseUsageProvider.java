package com.yunshuzhixue.modules.content.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 课程使用情况的扩展接口（02.整体架构设计.md 4.3）：题库（M06）提供题目数，学习进度（M08）提供学习记录。
 * 内容编排不依赖这两个模块，由它们实现本接口；尚未开发时没有实现，题目数按 0 计、删除时不拦截。
 */
public interface CourseUsageProvider {

    /** 各课程上架的刷题题目数，没有的课程可以不返回 */
    default Map<Long, Long> onShelfPracticeCounts(Collection<Long> courseIds) {
        return Map.of();
    }

    /** 各课程上架的面试题目数 */
    default Map<Long, Long> onShelfInterviewCounts(Collection<Long> courseIds) {
        return Map.of();
    }

    /** "已配题"的课程：当前至少有 1 道上架刷题题目（需求 4.5），用于课程列表筛选 */
    default Set<Long> coursesWithOnShelfPractice() {
        return Set.of();
    }

    /**
     * 有学员学习记录（打开过、标记过已学或作答过题目）的课程，用于判断路线和阶段能否删除（需求 6.3）。
     * 学习进度模块（M08）实现；未实现时视为没有学习记录。
     */
    default Set<Long> coursesWithLearningRecords(Collection<Long> courseIds) {
        return Set.of();
    }

    /** 删除课程前检查（需求 8.7）：返回不能删除的原因，如"有刷题题目 3 道""已有学员学习记录"；可以删除时返回空 */
    default List<String> deletionBlockers(Long courseId) {
        return List.of();
    }
}
