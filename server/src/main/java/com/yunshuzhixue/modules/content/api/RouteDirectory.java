package com.yunshuzhixue.modules.content.api;

import java.util.List;

/**
 * 已上架路线与课程的只读目录，供学习门户展示（02.整体架构设计.md 4.3：只通过公开接口访问）。
 */
public interface RouteDirectory {

    /** 阶段摘要：name 与已上架课程数 */
    record StageBrief(Long id, String name, long courseCount) {
    }

    /** 路线卡片所需的内容，不含个人进度 */
    record RouteCard(Long id, String name, String intro, List<StageBrief> stages) {
    }

    /** 路线中的课程；practiceQuestionCount 为 0 表示未配题 */
    record CourseItem(Long id, String title, String intro, Integer durationMinutes, String platformName,
                      List<String> subjectNames, long practiceQuestionCount, boolean isLearned, boolean isMastered) {
    }

    /**
     * 路线详情中的阶段：可用性由题量与系统配置的最低题量决定。
     * 组件名以 is 开头，序列化后与接口契约的 is_random_available 等字段一致。
     */
    record StageDetail(Long id, String name, String intro, boolean isRandomAvailable, boolean isExamAvailable,
                       boolean isInterviewAvailable, List<CourseItem> courses) {
    }

    record RouteDetail(Long id, String name, String intro, List<StageDetail> stages) {
    }

    /** 搜索命中的课程及其所在的已上架路线 */
    record RouteRef(Long learningRouteId, String routeName, Long routeStageId) {
    }

    record CourseHit(Long id, String title, String intro, List<RouteRef> routes) {
    }

    /** 全部已上架路线，按排序号升序；阶段同样按排序号升序 */
    List<RouteCard> publishedRoutes();

    /** 路线详情；路线不存在或已下架时返回 null */
    RouteDetail routeDetail(Long routeId);

    /** 按名称和简介搜索已上架路线 */
    List<RouteCard> searchRoutes(String keyword);

    /** 按名称和简介搜索已上架课程，并带出引用该课程的已上架路线 */
    List<CourseHit> searchCourses(String keyword);

    /** 路线当前编排下的已上架课程 ID，用于按路线筛选错题（需求 5.5） */
    java.util.Set<Long> courseIdsOfRoute(Long learningRouteId);
}
