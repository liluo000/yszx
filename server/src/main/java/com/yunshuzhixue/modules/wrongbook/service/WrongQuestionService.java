package com.yunshuzhixue.modules.wrongbook.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.time.UtcTime;
import com.yunshuzhixue.modules.account.api.StudentStp;
import com.yunshuzhixue.modules.content.api.CourseDirectory;
import com.yunshuzhixue.modules.content.api.CourseDirectory.CourseRef;
import com.yunshuzhixue.modules.content.api.CourseDirectory.SubjectRef;
import com.yunshuzhixue.modules.content.api.RouteDirectory;
import com.yunshuzhixue.modules.content.api.RouteDirectory.RouteCard;
import com.yunshuzhixue.modules.question.api.PracticeQuestionDirectory;
import com.yunshuzhixue.modules.question.api.PracticeQuestionDirectory.QuestionDetail;
import com.yunshuzhixue.modules.wrongbook.entity.WrongQuestion;
import com.yunshuzhixue.modules.wrongbook.mapper.WrongQuestionMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 错题本（需求 5.5，S-WRG-01、02）：列表按最新题目内容展示，支持课程、学科、题型、路线筛选。
 * 收录由 M09 的"作答完成"事件负责，本模块只读错题表并提供手动移除。
 */
@Service
public class WrongQuestionService {

    private static final String ON_SHELF = "on_shelf";

    private final WrongQuestionMapper mapper;
    private final PracticeQuestionDirectory questions;
    private final CourseDirectory courses;
    private final RouteDirectory routes;
    private final JsonMapper json;
    public WrongQuestionService(WrongQuestionMapper mapper, PracticeQuestionDirectory questions,
                                CourseDirectory courses, RouteDirectory routes, JsonMapper json) {
        this.mapper = mapper;
        this.questions = questions;
        this.courses = courses;
        this.routes = routes;
        this.json = json;
    }

    public WrongQuestionList list(PageQuery page, Long courseId, Long subjectId, String questionType,
                                  Long routeId) {
        long studentId = StudentStp.LOGIC.getLoginIdAsLong();
        LambdaQueryWrapper<WrongQuestion> query = new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, studentId)
                .eq(courseId != null, WrongQuestion::getCourseId, courseId)
                .eq(StringUtils.hasText(questionType), WrongQuestion::getQuestionType, questionType);
        if (subjectId != null) {
            Set<Long> ids = courses.courseIdsOfSubject(subjectId);
            if (ids.isEmpty()) {
                return new WrongQuestionList(List.of(), 0, page.page(), page.pageSize(), filters(studentId));
            }
            query.in(WrongQuestion::getCourseId, ids);
        }
        if (routeId != null) {
            Set<Long> ids = routes.courseIdsOfRoute(routeId);
            if (ids.isEmpty()) {
                return new WrongQuestionList(List.of(), 0, page.page(), page.pageSize(), filters(studentId));
            }
            query.in(WrongQuestion::getCourseId, ids);
        }

        long total = mapper.selectCount(query);
        query.orderByDesc(WrongQuestion::getLastWrongAt).orderByDesc(WrongQuestion::getId)
                .last(page.limitClause());
        List<WrongQuestion> rows = mapper.selectList(query);
        return new WrongQuestionList(items(rows), total, page.page(), page.pageSize(), filters(studentId));
    }

    @Transactional
    public void remove(Long id) {
        long studentId = StudentStp.LOGIC.getLoginIdAsLong();
        int removed = mapper.delete(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getId, id)
                .eq(WrongQuestion::getStudentId, studentId));
        if (removed == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    /** 题目内容取最新版本；题目已被删除的错题不再展示，已下架的只读不可重做 */
    private List<WrongItem> items(List<WrongQuestion> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> questionIds = rows.stream().map(WrongQuestion::getPracticeQuestionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, QuestionDetail> details = questions.details(questionIds);
        Set<Long> courseIds = rows.stream().map(WrongQuestion::getCourseId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, CourseRef> courseRefs = courses.find(courseIds);
        Map<Long, List<SubjectRef>> subjectRefs = courses.subjectsOf(courseIds);

        List<WrongItem> items = new ArrayList<>(rows.size());
        for (WrongQuestion row : rows) {
            QuestionDetail detail = details.get(row.getPracticeQuestionId());
            if (detail == null) {
                continue;
            }
            CourseRef course = courseRefs.get(row.getCourseId());
            List<String> subjectNames = subjectRefs.getOrDefault(row.getCourseId(), List.of()).stream()
                    .map(SubjectRef::name).toList();
            items.add(new WrongItem(row.getId(), row.getPracticeQuestionId(), row.getCourseId(),
                    course == null ? null : course.title(), detail.questionType(), detail.stem(),
                    detail.options(), detail.correctAnswer(), detail.referenceAnswer(), detail.analysis(),
                    parse(row.getLastStudentAnswer()), row.getWrongCount(), UtcTime.toInstant(row.getLastWrongAt()),
                    ON_SHELF.equals(detail.status()), subjectNames));
        }
        return items;
    }

    /** 筛选选项只列该学员错题覆盖的课程与学科，避免下拉出现大量无关项 */
    private FilterOptions filters(long studentId) {
        List<WrongQuestion> all = mapper.selectList(new LambdaQueryWrapper<WrongQuestion>()
                .select(WrongQuestion::getCourseId)
                .eq(WrongQuestion::getStudentId, studentId));
        Set<Long> courseIds = all.stream().map(WrongQuestion::getCourseId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, CourseRef> courseRefs = courses.find(courseIds);
        Map<Long, List<SubjectRef>> subjectRefs = courses.subjectsOf(courseIds);

        List<CourseOption> courseOptions = courseIds.stream()
                .filter(courseRefs::containsKey)
                .map(id -> new CourseOption(id, courseRefs.get(id).title()))
                .toList();
        Map<Long, String> subjectNames = new java.util.LinkedHashMap<>();
        subjectRefs.values().forEach(list -> list.forEach(s -> subjectNames.putIfAbsent(s.id(), s.name())));
        List<SubjectOption> subjectOptions = subjectNames.entrySet().stream()
                .map(entry -> new SubjectOption(entry.getKey(), entry.getValue()))
                .toList();
        List<RouteOption> routeOptions = routes.publishedRoutes().stream()
                .map((RouteCard r) -> new RouteOption(r.id(), r.name()))
                .toList();
        return new FilterOptions(courseOptions, subjectOptions, routeOptions);
    }

    private JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return json.readTree(raw);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public record WrongItem(Long id, Long practiceQuestionId, Long courseId, String courseTitle,
                            String questionType, String stem, Object options, Object correctAnswer,
                            String referenceAnswer, String analysis, Object lastStudentAnswer, int wrongCount,
                            Instant lastWrongAt, boolean isRedoable, List<String> subjectNames) {
    }

    public record CourseOption(Long id, String title) {
    }

    public record SubjectOption(Long id, String name) {
    }

    public record RouteOption(Long id, String name) {
    }

    public record FilterOptions(List<CourseOption> courses, List<SubjectOption> subjects,
                                List<RouteOption> routes) {
    }

    public record WrongQuestionList(List<WrongItem> items, long total, int page, int pageSize,
                                    FilterOptions filters) {
    }
}
