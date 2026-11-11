package com.yunshuzhixue.modules.content.controller.dto;

import com.yunshuzhixue.common.time.UtcTime;
import com.yunshuzhixue.modules.content.service.CourseService;
import com.yunshuzhixue.modules.content.service.CourseService.CourseFields;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** 课程库的请求与响应（A-CNT-06~12），字段与 04.API接口设计.md CourseAdminView 一致 */
public final class CourseDtos {

    private CourseDtos() {
    }

    public record SubjectRef(Long id, String name) {
    }

    public record CourseAdminView(Long id, List<SubjectRef> subjects, String title, Long videoPlatformId,
                                  String platformName, String videoUrl, String intro, Integer durationMinutes,
                                  Integer sortNo, String status, long practiceQuestionCount,
                                  long interviewQuestionCount, long routeReferenceCount, Instant createdAt) {
        public static CourseAdminView of(CourseService.CourseItem item) {
            var c = item.course();
            return new CourseAdminView(c.getId(),
                    item.subjects().stream().map(s -> new SubjectRef(s.id(), s.name())).toList(), c.getTitle(),
                    c.getVideoPlatformId(), item.platformName(), c.getVideoUrl(), c.getIntro(), c.getDurationMinutes(),
                    c.getSortNo(), c.getStatus(), item.practiceQuestionCount(), item.interviewQuestionCount(),
                    item.routeReferenceCount(), UtcTime.toInstant(c.getCreatedAt()));
        }
    }

    public record CreateCourseRequest(
            @NotEmpty(message = "请选择所属学科") @Size(max = 20, message = "最多选择 20 个学科")
            List<@NotNull(message = "学科不能为空") Long> subjectIds,
            @NotBlank(message = "请填写课程名称") @Size(max = 100, message = "课程名称最多 100 个字符") String title,
            @NotNull(message = "请选择视频平台") Long videoPlatformId,
            @NotBlank(message = "请填写视频链接") @Size(max = 1000, message = "视频链接最多 1000 个字符") String videoUrl,
            @Size(max = 1000, message = "简介最多 1000 个字符") String intro,
            @Min(value = 0, message = "时长为 0~100000 分钟") @Max(value = 100000, message = "时长为 0~100000 分钟") Integer durationMinutes,
            @NotNull(message = "请填写排序号") Integer sortNo) {
        public CourseFields toFields() {
            return new CourseFields(subjectIds, title, videoPlatformId, videoUrl, intro, durationMinutes, sortNo);
        }
    }

    public record UpdateCourseRequest(
            @Size(min = 1, max = 20, message = "学科为 1~20 个") List<@NotNull(message = "学科不能为空") Long> subjectIds,
            @Size(max = 100, message = "课程名称最多 100 个字符") @Pattern(regexp = "(?s).*\\S.*", message = "课程名称不能为空") String title,
            Long videoPlatformId,
            @Size(max = 1000, message = "视频链接最多 1000 个字符") @Pattern(regexp = "(?s).*\\S.*", message = "视频链接不能为空") String videoUrl,
            @Size(max = 1000, message = "简介最多 1000 个字符") String intro,
            @Min(value = 0, message = "时长为 0~100000 分钟") @Max(value = 100000, message = "时长为 0~100000 分钟") Integer durationMinutes,
            Integer sortNo) {
        public CourseFields toFields() {
            return new CourseFields(subjectIds, title, videoPlatformId, videoUrl, intro, durationMinutes, sortNo);
        }
    }

    public record CourseStatusRequest(
            @NotBlank(message = "请选择状态") @Pattern(regexp = "^(on_shelf|off_shelf)$", message = "状态不正确") String status) {
    }
}
