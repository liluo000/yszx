package com.yunshuzhixue.modules.portal.service;

import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.content.api.RouteDirectory;
import com.yunshuzhixue.modules.content.api.RouteDirectory.CourseHit;
import com.yunshuzhixue.modules.content.api.RouteDirectory.RouteCard;
import com.yunshuzhixue.modules.content.api.RouteDirectory.RouteDetail;
import com.yunshuzhixue.modules.content.api.RouteDirectory.StageBrief;
import com.yunshuzhixue.modules.content.api.RouteDirectory.StageDetail;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 学员端学习路线（需求 5.3，S-PTL-02~04）：列表、详情与搜索。
 *
 * <p>个人进度（RouteProgress）依赖学习进度模块 M08，尚未开发，游客与已登录都先返回空。</p>
 */
@Service
public class StudentRouteService {

    public record RouteProgress(int learnedCourseCount, int totalCourseCount, int masteredCourseCount,
                                int configuredCourseCount) {
    }

    public record RouteCardView(Long id, String name, String intro, List<StageBrief> stages, RouteProgress progress) {
    }

    public record RouteDetailView(Long id, String name, String intro, RouteProgress progress,
                                  List<StageDetail> stages) {
    }

    public record SearchResult(List<RouteCardView> routes, List<CourseHit> courses) {
    }

    private final RouteDirectory directory;

    public StudentRouteService(RouteDirectory directory) {
        this.directory = directory;
    }

    public PageResult<RouteCardView> routes(PageQuery page) {
        List<RouteCard> all = directory.publishedRoutes();
        int total = all.size();
        int from = Math.min((page.page() - 1) * page.pageSize(), total);
        int to = Math.min(from + page.pageSize(), total);
        List<RouteCardView> items = all.subList(from, to).stream().map(StudentRouteService::card).toList();
        return new PageResult<>(items, total, page.page(), page.pageSize());
    }

    public RouteDetailView detail(Long routeId) {
        RouteDetail detail = directory.routeDetail(routeId);
        if (detail == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学习路线不存在或已下架");
        }
        return new RouteDetailView(detail.id(), detail.name(), detail.intro(), null, detail.stages());
    }

    public SearchResult search(String keyword) {
        return new SearchResult(directory.searchRoutes(keyword).stream().map(StudentRouteService::card).toList(),
                directory.searchCourses(keyword));
    }

    private static RouteCardView card(RouteCard card) {
        return new RouteCardView(card.id(), card.name(), card.intro(), card.stages(), null);
    }
}
