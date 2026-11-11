package com.yunshuzhixue.modules.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yunshuzhixue.common.api.FieldViolation;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.content.entity.LearningRoute;
import com.yunshuzhixue.modules.content.entity.RouteStage;
import com.yunshuzhixue.modules.content.entity.StageCourse;
import com.yunshuzhixue.modules.content.mapper.LearningRouteMapper;
import com.yunshuzhixue.modules.content.mapper.RouteStageMapper;
import com.yunshuzhixue.modules.content.mapper.StageCourseMapper;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 学习路线（需求 6.3，A-CNT-14~20、28）。新建默认下架，编排完成后再上架 */
@Service
public class RouteService {

    /** 路线及阶段数、课程数（课程数为路线内引用的课程总数） */
    public record RouteItem(LearningRoute route, long stageCount, long courseCount) {
    }

    private final LearningRouteMapper routeMapper;
    private final RouteStageMapper stageMapper;
    private final StageCourseMapper stageCourseMapper;
    private final StageService stageService;

    public RouteService(LearningRouteMapper routeMapper, RouteStageMapper stageMapper,
                        StageCourseMapper stageCourseMapper, StageService stageService) {
        this.routeMapper = routeMapper;
        this.stageMapper = stageMapper;
        this.stageCourseMapper = stageCourseMapper;
        this.stageService = stageService;
    }

    public PageResult<RouteItem> list(PageQuery page, String status) {
        LambdaQueryWrapper<LearningRoute> query = new LambdaQueryWrapper<LearningRoute>()
                .eq(StringUtils.hasText(status), LearningRoute::getStatus, status);
        long total = routeMapper.selectCount(query);
        query.orderByAsc(LearningRoute::getSortNo).orderByAsc(LearningRoute::getId).last(page.limitClause());
        return new PageResult<>(enrich(routeMapper.selectList(query)), total, page.page(), page.pageSize());
    }

    /** 下拉选项（A-CNT-28）：全部路线，不分页 */
    public List<LearningRoute> options() {
        return routeMapper.selectList(new LambdaQueryWrapper<LearningRoute>()
                .orderByAsc(LearningRoute::getSortNo).orderByAsc(LearningRoute::getId));
    }

    public RouteItem get(Long id) {
        return enrich(List.of(stageService.requireRoute(id))).get(0);
    }

    @Transactional
    public RouteItem create(CurrentAdmin operator, String name, String intro, Integer sortNo) {
        LearningRoute route = new LearningRoute();
        route.setName(name.trim());
        route.setIntro(blankToNull(intro));
        route.setSortNo(sortNo);
        route.setStatus(CourseService.OFF_SHELF);
        routeMapper.insert(route);
        stageService.log(operator, "新增路线", route.getId(), route.getName());
        return get(route.getId());
    }

    @Transactional
    public RouteItem update(CurrentAdmin operator, Long id, String name, String intro, Integer sortNo) {
        LearningRoute route = stageService.requireRoute(id);
        LambdaUpdateWrapper<LearningRoute> update = new LambdaUpdateWrapper<LearningRoute>().eq(LearningRoute::getId, id)
                .set(name != null, LearningRoute::getName, name == null ? null : name.trim())
                .set(intro != null, LearningRoute::getIntro, blankToNull(intro))
                .set(sortNo != null, LearningRoute::getSortNo, sortNo);
        if (StringUtils.hasText(update.getSqlSet())) {
            routeMapper.update(null, update);
        }
        stageService.log(operator, "编辑路线", id, name == null ? route.getName() : name.trim());
        return get(id);
    }

    /** 上架须至少 1 个阶段，且至少引用 1 门已上架课程；不校验题量（需求 6.3） */
    @Transactional
    public RouteItem changeStatus(CurrentAdmin operator, Long id, String status) {
        LearningRoute route = stageService.requireRoute(id);
        if (CourseService.ON_SHELF.equals(status)) {
            List<StageService.StageItem> stages = stageService.stagesOf(id);
            List<String> reasons = new ArrayList<>();
            if (stages.isEmpty()) {
                reasons.add("还没有阶段，请至少新增 1 个阶段");
            }
            boolean hasOnShelfCourse = stages.stream().flatMap(s -> s.courses().stream())
                    .anyMatch(c -> c.course() != null && CourseService.ON_SHELF.equals(c.course().course().getStatus()));
            if (!hasOnShelfCourse) {
                reasons.add("还没有引用已上架的课程，请至少引用 1 门已上架课程");
            }
            if (!reasons.isEmpty()) {
                throw new BusinessException(ErrorCode.ROUTE_PUBLISH_INVALID, "路线不满足上架条件：" + String.join("；", reasons),
                        reasons.stream().map(r -> (Object) new FieldViolation("route", r)).toList());
            }
        }
        if (!status.equals(route.getStatus())) {
            routeMapper.update(null, new LambdaUpdateWrapper<LearningRoute>()
                    .eq(LearningRoute::getId, id).set(LearningRoute::getStatus, status));
            stageService.log(operator, CourseService.ON_SHELF.equals(status) ? "上架路线" : "下架路线", id, route.getName());
        }
        return get(id);
    }

    /** 有学习记录时不能删除，只能下架；否则连同阶段和课程引用一起删除 */
    @Transactional
    public void delete(CurrentAdmin operator, Long id) {
        LearningRoute route = stageService.requireRoute(id);
        List<Long> courseIds = stageCourseMapper.selectList(new LambdaQueryWrapper<StageCourse>()
                .eq(StageCourse::getLearningRouteId, id)).stream().map(StageCourse::getCourseId).toList();
        if (stageService.hasLearningRecords(courseIds)) {
            throw new BusinessException(ErrorCode.CONTENT_IN_USE, "路线「" + route.getName() + "」已有学员学习记录，不能删除，只能下架",
                    List.of(new FieldViolation("route", "已有学员学习记录（打开过课程、标记已学或作答过题目）")));
        }
        stageCourseMapper.delete(new LambdaQueryWrapper<StageCourse>().eq(StageCourse::getLearningRouteId, id));
        stageMapper.delete(new LambdaQueryWrapper<RouteStage>().eq(RouteStage::getLearningRouteId, id));
        routeMapper.deleteById(id);
        stageService.log(operator, "删除路线", id, route.getName());
    }

    private List<RouteItem> enrich(List<LearningRoute> routes) {
        if (routes.isEmpty()) {
            return List.of();
        }
        List<Long> ids = routes.stream().map(LearningRoute::getId).toList();
        Map<Long, Long> stages = countBy(stageMapper.selectMaps(new QueryWrapper<RouteStage>()
                .select("learning_route_id", "COUNT(*) AS n").in("learning_route_id", ids).groupBy("learning_route_id")));
        Map<Long, Long> courses = countBy(stageCourseMapper.selectMaps(new QueryWrapper<StageCourse>()
                .select("learning_route_id", "COUNT(*) AS n").in("learning_route_id", ids).groupBy("learning_route_id")));
        return routes.stream().map(r -> new RouteItem(r, stages.getOrDefault(r.getId(), 0L),
                courses.getOrDefault(r.getId(), 0L))).toList();
    }

    private static Map<Long, Long> countBy(Collection<Map<String, Object>> rows) {
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(((Number) row.get("learning_route_id")).longValue(), ((Number) row.get("n")).longValue());
        }
        return result;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
