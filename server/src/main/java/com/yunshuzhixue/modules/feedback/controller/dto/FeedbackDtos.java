package com.yunshuzhixue.modules.feedback.controller.dto;

import com.yunshuzhixue.common.time.UtcTime;
import com.yunshuzhixue.modules.feedback.entity.Feedback;
import com.yunshuzhixue.modules.feedback.service.FeedbackService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** 纠错与反馈的请求与响应（A-FDB-01、02），字段与 04.API接口设计.md FeedbackItem 一致 */
public final class FeedbackDtos {

    private FeedbackDtos() {
    }

    public record FeedbackItem(List<Long> feedbackIds, String feedbackType, Long courseId, String courseTitle,
                               Long practiceQuestionId, String stem, String errorType, String description,
                               String videoUrl, Long studentId, String studentNickname, int submitCount,
                               String status, String handlingNote, Instant handledAt, Instant createdAt) {
        public static FeedbackItem of(FeedbackService.Group group) {
            Feedback f = group.latest();
            return new FeedbackItem(group.rows().stream().map(Feedback::getId).toList(), f.getFeedbackType(),
                    f.getCourseId(), group.courseTitle(), f.getPracticeQuestionId(), group.stem(), f.getErrorType(),
                    f.getDescription(), f.getVideoUrl(), f.getStudentId(), group.studentNickname(), group.rows().size(),
                    f.getStatus(), f.getHandlingNote(), UtcTime.toInstant(f.getHandledAt()),
                    UtcTime.toInstant(f.getCreatedAt()));
        }
    }

    public record HandleRequest(
            @NotEmpty(message = "请选择要处理的反馈") @Size(max = 500, message = "一次最多处理 500 条")
            List<@NotNull(message = "反馈不能为空") Long> feedbackIds,
            @NotBlank(message = "请选择处理结果") @Pattern(regexp = "^(corrected|rejected)$", message = "处理结果不正确") String status,
            @NotBlank(message = "请填写处理说明") @Size(max = 1000, message = "处理说明最多 1000 个字符") String handlingNote) {
    }

    public record HandleResponse(int updatedCount) {
    }
}
