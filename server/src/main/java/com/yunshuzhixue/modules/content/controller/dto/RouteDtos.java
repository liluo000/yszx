package com.yunshuzhixue.modules.content.controller.dto;

import com.yunshuzhixue.common.time.UtcTime;
import com.yunshuzhixue.modules.content.entity.LearningRoute;
import com.yunshuzhixue.modules.content.service.CourseService;
import com.yunshuzhixue.modules.content.service.CourseService.CourseItem;
import com.yunshuzhixue.modules.content.service.RouteService;
import com.yunshuzhixue.modules.content.service.StageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 学习路线、阶段的请求与响应（A-CNT-14~28），字段与 04.API接口设计.md 一致 */
public final class RouteDtos {

    private RouteDtos() {
    }

    private static final String NOT_BLANK = "(?s).*\\S.*";

    public record RouteAdminView(Long id, String name, String intro, Integer sortNo, String status, long stageCount,
                                 long courseCount, Instant createdAt) {
        public static RouteAdminView of(RouteService.RouteItem item) {
            LearningRoute r = item.route();
            return new RouteAdminView(r.getId(), r.getName(), r.getIntro(), r.getSortNo(), r.getStatus(),
                    item.stageCount(), item.courseCount(), UtcTime.toInstant(r.getCreatedAt()));
        }
    }

    /** 阶段内课程；subject_names、practice_question_count 便于编排时识别课程和"未配题" */
    public record StageCourseView(Long courseId, String title, String status, Integer sortNo,
                                  List<String> subjectNames, long practiceQuestionCount) {
    }

    public record StageAdminView(Long id, String name, String intro, Integer sortNo, List<StageCourseView> courses) {
        public static StageAdminView of(StageService.StageItem item) {
            return new StageAdminView(item.stage().getId(), item.stage().getName(), item.stage().getIntro(),
                    item.stage().getSortNo(), item.courses().stream().filter(c -> c.course() != null)
                    .map(c -> new StageCourseView(c.link().getCourseId(), c.course().course().getTitle(),
                            c.course().course().getStatus(), c.link().getSortNo(),
                            c.course().subjects().stream().map(CourseService.SubjectRef::name).toList(),
                            c.course().practiceQuestionCount()))
                    .toList());
        }
    }

    public record RouteDetailView(RouteAdminView route, List<StageAdminView> stages) {
    }

    public record RouteOption(Long id, String name, String status) {
    }

    /* ---------------- 预览：与学员端路线详情结构一致，进度为空 ---------------- */

    public record CourseItemView(Long id, String title, String intro, Integer durationMinutes, String platformName,
                                 List<String> subjectNames, long practiceQuestionCount, boolean isLearned,
                                 boolean isMastered) {
        static CourseItemView of(CourseItem item) {
            var c = item.course();
            return new CourseItemView(c.getId(), c.getTitle(), c.getIntro(), c.getDurationMinutes(), item.platformName(),
                    item.subjects().stream().map(CourseService.SubjectRef::name).toList(),
                    item.practiceQuestionCount(), false, false);
        }
    }

    /** 题量相关入口在题库建成后由题库模块提供数据，目前按不可用处理 */
    public record StageDetailView(Long id, String name, String intro, Object progress, boolean isRandomAvailable,
                                  boolean isExamAvailable, boolean isInterviewAvailable, List<CourseItemView> courses) {
        public static StageDetailView of(StageService.StageItem item) {
            List<CourseItemView> courses = item.courses().stream().map(StageService.StageCourseItem::course)
                    .filter(Objects::nonNull)
                    .filter(c -> CourseService.ON_SHELF.equals(c.course().getStatus()))
                    .map(CourseItemView::of).toList();
            return new StageDetailView(item.stage().getId(), item.stage().getName(), item.stage().getIntro(), null,
                    false, false, false, courses);
        }
    }

    public record RoutePreviewView(Long id, String name, String intro, List<StageDetailView> stages) {
    }

    /* ---------------- 请求 ---------------- */

    public record CreateRouteRequest(
            @NotBlank(message = "请填写路线名称") @Size(max = 100, message = "路线名称最多 100 个字符") String name,
            @Size(max = 1000, message = "简介最多 1000 个字符") String intro,
            @NotNull(message = "请填写排序号") Integer sortNo) {
    }

    public record UpdateRouteRequest(
            @Size(max = 100, message = "路线名称最多 100 个字符") @Pattern(regexp = NOT_BLANK, message = "路线名称不能为空") String name,
            @Size(max = 1000, message = "简介最多 1000 个字符") String intro,
            Integer sortNo) {
    }

    public record RouteStatusRequest(
            @NotBlank(message = "请选择状态") @Pattern(regexp = "^(on_shelf|off_shelf)$", message = "状态不正确") String status) {
    }

    public record CreateStageRequest(
            @NotBlank(message = "请填写阶段名称") @Size(max = 100, message = "阶段名称最多 100 个字符") String name,
            @Size(max = 1000, message = "简介最多 1000 个字符") String intro,
            @NotNull(message = "请填写排序号") Integer sortNo) {
    }

    public record UpdateStageRequest(
            @Size(max = 100, message = "阶段名称最多 100 个字符") @Pattern(regexp = NOT_BLANK, message = "阶段名称不能为空") String name,
            @Size(max = 1000, message = "简介最多 1000 个字符") String intro,
            Integer sortNo) {
    }

    public record AddCoursesRequest(
            @NotEmpty(message = "请选择课程") @Size(max = 100, message = "一次最多引用 100 门课程")
            List<@NotNull(message = "课程不能为空") Long> courseIds) {
    }

    public record SortRequest(
            @NotEmpty(message = "请提供排序") @Size(max = 500, message = "最多 500 项") List<@Valid SortEntry> items) {
        public List<StageService.SortItem> toItems() {
            return items.stream().map(e -> new StageService.SortItem(e.id(), e.sortNo())).toList();
        }
    }

    public record SortEntry(@NotNull(message = "缺少 id") Long id, @NotNull(message = "缺少排序号") Integer sortNo) {
    }
}
