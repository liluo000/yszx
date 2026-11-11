package com.yunshuzhixue.modules.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yunshuzhixue.common.api.FieldViolation;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.web.ClientIps;
import com.yunshuzhixue.modules.audit.api.AuditModules;
import com.yunshuzhixue.modules.audit.api.OperationLogCommand;
import com.yunshuzhixue.modules.audit.api.OperationLogRecorder;
import com.yunshuzhixue.modules.content.api.CourseUsageProvider;
import com.yunshuzhixue.modules.content.entity.LearningRoute;
import com.yunshuzhixue.modules.content.entity.RouteStage;
import com.yunshuzhixue.modules.content.entity.StageCourse;
import com.yunshuzhixue.modules.content.mapper.LearningRouteMapper;
import com.yunshuzhixue.modules.content.mapper.RouteStageMapper;
import com.yunshuzhixue.modules.content.mapper.StageCourseMapper;
import com.yunshuzhixue.modules.content.service.CourseService.CourseItem;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 路线阶段与阶段内课程（需求 6.3，A-CNT-21~27）。
 * 同一路线内一门课程只能出现在一个阶段（stage_course 的唯一索引兜底）；阶段没有上下架。
 */
@Service
public class StageService {

    /** 阶段内引用的课程；course 为空表示课程已被删除（正常不会发生，删除课程前会检查引用） */
    public record StageCourseItem(StageCourse link, CourseItem course) {
    }

    public record StageItem(RouteStage stage, List<StageCourseItem> courses) {
    }

    public record SortItem(Long id, Integer sortNo) {
    }

    private final LearningRouteMapper routeMapper;
    private final RouteStageMapper stageMapper;
    private final StageCourseMapper stageCourseMapper;
    private final CourseService courseService;
    private final List<CourseUsageProvider> usageProviders;
    private final OperationLogRecorder recorder;

    public StageService(LearningRouteMapper routeMapper, RouteStageMapper stageMapper,
                        StageCourseMapper stageCourseMapper, CourseService courseService,
                        List<CourseUsageProvider> usageProviders, OperationLogRecorder recorder) {
        this.routeMapper = routeMapper;
        this.stageMapper = stageMapper;
        this.stageCourseMapper = stageCourseMapper;
        this.courseService = courseService;
        this.usageProviders = usageProviders;
        this.recorder = recorder;
    }

    /** 路线的全部阶段（按排序号）及其课程（按阶段内排序号），课程信息一次批量查询 */
    public List<StageItem> stagesOf(Long routeId) {
        List<RouteStage> stages = stageMapper.selectList(new LambdaQueryWrapper<RouteStage>()
                .eq(RouteStage::getLearningRouteId, routeId)
                .orderByAsc(RouteStage::getSortNo).orderByAsc(RouteStage::getId));
        List<StageCourse> links = stageCourseMapper.selectList(new LambdaQueryWrapper<StageCourse>()
                .eq(StageCourse::getLearningRouteId, routeId)
                .orderByAsc(StageCourse::getSortNo).orderByAsc(StageCourse::getId));
        Map<Long, CourseItem> courses = courseService.items(
                links.stream().map(StageCourse::getCourseId).collect(Collectors.toSet()));
        Map<Long, List<StageCourseItem>> byStage = links.stream().collect(Collectors.groupingBy(
                StageCourse::getRouteStageId,
                Collectors.mapping(l -> new StageCourseItem(l, courses.get(l.getCourseId())), Collectors.toList())));
        return stages.stream().map(s -> new StageItem(s, byStage.getOrDefault(s.getId(), List.of()))).toList();
    }

    public StageItem stage(Long stageId) {
        RouteStage stage = requireStage(stageId);
        return stagesOf(stage.getLearningRouteId()).stream()
                .filter(s -> s.stage().getId().equals(stageId)).findFirst().orElseThrow();
    }

    @Transactional
    public StageItem create(CurrentAdmin operator, Long routeId, String name, String intro, Integer sortNo) {
        LearningRoute route = requireRoute(routeId);
        RouteStage stage = new RouteStage();
        stage.setLearningRouteId(routeId);
        stage.setName(name.trim());
        stage.setIntro(blankToNull(intro));
        stage.setSortNo(sortNo);
        stageMapper.insert(stage);
        log(operator, "新增阶段", stage.getId(), route.getName() + " / " + stage.getName());
        return stage(stage.getId());
    }

    @Transactional
    public StageItem update(CurrentAdmin operator, Long stageId, String name, String intro, Integer sortNo) {
        RouteStage stage = requireStage(stageId);
        LambdaUpdateWrapper<RouteStage> update = new LambdaUpdateWrapper<RouteStage>().eq(RouteStage::getId, stageId)
                .set(name != null, RouteStage::getName, name == null ? null : name.trim())
                .set(intro != null, RouteStage::getIntro, blankToNull(intro))
                .set(sortNo != null, RouteStage::getSortNo, sortNo);
        if (StringUtils.hasText(update.getSqlSet())) {
            stageMapper.update(null, update);
        }
        log(operator, "编辑阶段", stageId, routeName(stage) + " / " + (name == null ? stage.getName() : name.trim()));
        return stage(stageId);
    }

    /** 阶段内课程有学习记录时不能删除，需要停用时下架整条路线（需求 6.3） */
    @Transactional
    public void delete(CurrentAdmin operator, Long stageId) {
        RouteStage stage = requireStage(stageId);
        List<Long> courseIds = courseIdsOfStage(stageId);
        if (hasLearningRecords(courseIds)) {
            throw new BusinessException(ErrorCode.CONTENT_IN_USE, "阶段「" + stage.getName() + "」已有学员学习记录，不能删除",
                    List.of(new FieldViolation("stage", "已有学员学习记录，需要停用时请下架整条路线")));
        }
        stageCourseMapper.delete(new LambdaQueryWrapper<StageCourse>().eq(StageCourse::getRouteStageId, stageId));
        stageMapper.deleteById(stageId);
        log(operator, "删除阶段", stageId, routeName(stage) + " / " + stage.getName());
    }

    @Transactional
    public void sortStages(CurrentAdmin operator, Long routeId, List<SortItem> items) {
        LearningRoute route = requireRoute(routeId);
        Set<Long> ids = distinctIds(items);
        long owned = stageMapper.selectCount(new LambdaQueryWrapper<RouteStage>()
                .eq(RouteStage::getLearningRouteId, routeId).in(RouteStage::getId, ids));
        if (owned != ids.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "阶段不属于该路线");
        }
        for (SortItem item : items) {
            stageMapper.update(null, new LambdaUpdateWrapper<RouteStage>()
                    .eq(RouteStage::getId, item.id()).set(RouteStage::getSortNo, item.sortNo()));
        }
        log(operator, "调整阶段排序", routeId, route.getName());
    }

    /** 引用课程：已在本阶段的忽略；已被同一路线其他阶段引用的拒绝；新课程排在阶段末尾 */
    @Transactional
    public StageItem addCourses(CurrentAdmin operator, Long stageId, List<Long> courseIds) {
        RouteStage stage = requireStage(stageId);
        Set<Long> wanted = new LinkedHashSet<>(courseIds);
        Map<Long, CourseItem> courses = courseService.items(wanted);
        if (courses.size() != wanted.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不存在",
                    List.of(new FieldViolation("course_ids", "所选课程不存在，请刷新后重试")));
        }
        List<StageCourse> existing = stageCourseMapper.selectList(new LambdaQueryWrapper<StageCourse>()
                .eq(StageCourse::getLearningRouteId, stage.getLearningRouteId()).in(StageCourse::getCourseId, wanted));
        Map<Long, String> stageNames = stageMapper.selectList(new LambdaQueryWrapper<RouteStage>()
                        .eq(RouteStage::getLearningRouteId, stage.getLearningRouteId()))
                .stream().collect(Collectors.toMap(RouteStage::getId, RouteStage::getName));
        List<String> conflicts = new ArrayList<>();
        Set<Long> already = new HashSet<>();
        for (StageCourse link : existing) {
            if (link.getRouteStageId().equals(stageId)) {
                already.add(link.getCourseId());
            } else {
                conflicts.add("「" + courses.get(link.getCourseId()).course().getTitle() + "」已在阶段「"
                        + stageNames.getOrDefault(link.getRouteStageId(), "") + "」");
            }
        }
        if (!conflicts.isEmpty()) {
            throw new BusinessException(ErrorCode.COURSE_ALREADY_IN_ROUTE, String.join("；", conflicts),
                    conflicts.stream().map(c -> (Object) new FieldViolation("course_ids", c)).toList());
        }
        int sortNo = maxCourseSort(stageId);
        int added = 0;
        for (Long courseId : wanted) {
            if (already.contains(courseId)) {
                continue;
            }
            StageCourse link = new StageCourse();
            link.setLearningRouteId(stage.getLearningRouteId());
            link.setRouteStageId(stageId);
            link.setCourseId(courseId);
            link.setSortNo(++sortNo);
            try {
                stageCourseMapper.insert(link);
            } catch (DuplicateKeyException e) {
                throw new BusinessException(ErrorCode.COURSE_ALREADY_IN_ROUTE);
            }
            added++;
        }
        if (added > 0) {
            log(operator, "阶段引用课程", stageId, routeName(stage) + " / " + stage.getName() + "：" + added + " 门");
        }
        return stage(stageId);
    }

    /** 移出课程：课程自身的学习状态保留，进度按当前编排计算（需求 6.3） */
    @Transactional
    public void removeCourse(CurrentAdmin operator, Long stageId, Long courseId) {
        RouteStage stage = requireStage(stageId);
        int deleted = stageCourseMapper.delete(new LambdaQueryWrapper<StageCourse>()
                .eq(StageCourse::getRouteStageId, stageId).eq(StageCourse::getCourseId, courseId));
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        CourseItem course = courseService.items(List.of(courseId)).get(courseId);
        log(operator, "移出课程", stageId, routeName(stage) + " / " + stage.getName() + "："
                + (course == null ? "课程 " + courseId : course.course().getTitle()));
    }

    /** 调整阶段内课程顺序，items 中的 id 为课程 ID */
    @Transactional
    public void sortCourses(CurrentAdmin operator, Long stageId, List<SortItem> items) {
        RouteStage stage = requireStage(stageId);
        Set<Long> ids = distinctIds(items);
        long owned = stageCourseMapper.selectCount(new LambdaQueryWrapper<StageCourse>()
                .eq(StageCourse::getRouteStageId, stageId).in(StageCourse::getCourseId, ids));
        if (owned != ids.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "课程不在该阶段中");
        }
        for (SortItem item : items) {
            stageCourseMapper.update(null, new LambdaUpdateWrapper<StageCourse>()
                    .eq(StageCourse::getRouteStageId, stageId).eq(StageCourse::getCourseId, item.id())
                    .set(StageCourse::getSortNo, item.sortNo()));
        }
        log(operator, "调整课程排序", stageId, routeName(stage) + " / " + stage.getName());
    }

    /* ---------------- 供路线使用 ---------------- */

    boolean hasLearningRecords(Collection<Long> courseIds) {
        if (courseIds.isEmpty()) {
            return false;
        }
        return usageProviders.stream().anyMatch(p -> !p.coursesWithLearningRecords(courseIds).isEmpty());
    }

    void log(CurrentAdmin operator, String action, Long targetId, String label) {
        recorder.record(new OperationLogCommand(operator.id(), operator.name(), operator.role(), AuditModules.ROUTE,
                action, action.contains("阶段") || action.contains("课程") ? "route_stage" : "learning_route",
                targetId, label, ClientIps.current()));
    }

    LearningRoute requireRoute(Long routeId) {
        LearningRoute route = routeMapper.selectById(routeId);
        if (route == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return route;
    }

    /* ---------------- 内部 ---------------- */

    private RouteStage requireStage(Long stageId) {
        RouteStage stage = stageMapper.selectById(stageId);
        if (stage == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return stage;
    }

    private String routeName(RouteStage stage) {
        LearningRoute route = routeMapper.selectById(stage.getLearningRouteId());
        return route == null ? "" : route.getName();
    }

    private List<Long> courseIdsOfStage(Long stageId) {
        return stageCourseMapper.selectList(new LambdaQueryWrapper<StageCourse>().eq(StageCourse::getRouteStageId, stageId))
                .stream().map(StageCourse::getCourseId).toList();
    }

    private int maxCourseSort(Long stageId) {
        return stageCourseMapper.selectList(new LambdaQueryWrapper<StageCourse>().eq(StageCourse::getRouteStageId, stageId))
                .stream().mapToInt(StageCourse::getSortNo).max().orElse(0);
    }

    private static Set<Long> distinctIds(List<SortItem> items) {
        Set<Long> ids = new HashSet<>();
        for (SortItem item : items) {
            if (!ids.add(item.id())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "排序中有重复项");
            }
        }
        return ids;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
