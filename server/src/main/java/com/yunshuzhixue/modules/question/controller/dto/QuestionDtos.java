package com.yunshuzhixue.modules.question.controller.dto;

import com.yunshuzhixue.modules.question.domain.PracticeQuestionRules.Option;
import com.yunshuzhixue.modules.question.service.InterviewQuestionService;
import com.yunshuzhixue.modules.question.service.PracticeQuestionService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** 题库的请求与响应（A-QST-01~14），字段与 04.API接口设计.md 一致 */
public final class QuestionDtos {

    private QuestionDtos() {
    }

    private static final String TYPES = "^(true_false|single_choice|multiple_choice|short_answer)$";
    private static final String NOT_BLANK = "(?s).*\\S.*";

    public record PracticeQuestionView(Long id, Long courseId, String courseTitle, String questionType, String stem,
                                       List<Option> options, JsonNode correctAnswer, String referenceAnswer,
                                       String scoringPoints, String analysis, Integer sortNo, String status,
                                       boolean isAnswered) {
        public static PracticeQuestionView of(PracticeQuestionService.Item item) {
            var q = item.question();
            return new PracticeQuestionView(q.getId(), q.getCourseId(), item.courseTitle(), q.getQuestionType(),
                    q.getStem(), item.options(), item.correctAnswer(), q.getReferenceAnswer(), q.getScoringPoints(),
                    q.getAnalysis(), q.getSortNo(), q.getStatus(), item.answered());
        }
    }

    public record InterviewQuestionView(Long id, Long courseId, String courseTitle, String stem, String referenceAnswer,
                                        String scoringPoints, String followupDirection, String status,
                                        boolean isDrawn) {
        public static InterviewQuestionView of(InterviewQuestionService.Item item) {
            var q = item.question();
            return new InterviewQuestionView(q.getId(), q.getCourseId(), item.courseTitle(), q.getStem(),
                    q.getReferenceAnswer(), q.getScoringPoints(), q.getFollowupDirection(), q.getStatus(), item.drawn());
        }
    }

    public record OptionInput(String key, String text) {
        Option toOption() {
            return new Option(key, text);
        }
    }

    /** 判断题答案为布尔值，选择题为选项字母数组 */
    static Object answerOf(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isArray()) {
            List<Object> keys = new java.util.ArrayList<>();
            node.forEach(n -> keys.add(n.isString() ? n.asString() : n));
            return keys;
        }
        return node;
    }

    static List<Option> optionsOf(List<OptionInput> options) {
        return options == null ? null : options.stream().map(o -> o == null ? null : o.toOption()).toList();
    }

    public record CreatePracticeRequest(
            @NotNull(message = "请选择所属课程") Long courseId,
            @NotBlank(message = "请选择题型") @Pattern(regexp = TYPES, message = "题型不正确") String questionType,
            @NotBlank(message = "请填写题干") @Size(max = 10000, message = "题干最多 10000 个字符") String stem,
            @Size(max = 8, message = "选项最多 8 个") List<OptionInput> options,
            JsonNode correctAnswer,
            @Size(max = 10000, message = "参考答案最多 10000 个字符") String referenceAnswer,
            @Size(max = 10000, message = "评分要点最多 10000 个字符") String scoringPoints,
            @Size(max = 10000, message = "解析最多 10000 个字符") String analysis,
            @NotNull(message = "请填写排序号") Integer sortNo) {
        public PracticeQuestionService.Fields toFields() {
            return new PracticeQuestionService.Fields(courseId, questionType, stem, optionsOf(options),
                    answerOf(correctAnswer), referenceAnswer, scoringPoints, analysis, sortNo);
        }
    }

    public record UpdatePracticeRequest(
            Long courseId,
            @Pattern(regexp = TYPES, message = "题型不正确") String questionType,
            @Size(max = 10000, message = "题干最多 10000 个字符") @Pattern(regexp = NOT_BLANK, message = "题干不能为空") String stem,
            @Size(max = 8, message = "选项最多 8 个") List<OptionInput> options,
            JsonNode correctAnswer,
            @Size(max = 10000, message = "参考答案最多 10000 个字符") String referenceAnswer,
            @Size(max = 10000, message = "评分要点最多 10000 个字符") String scoringPoints,
            @Size(max = 10000, message = "解析最多 10000 个字符") String analysis,
            Integer sortNo) {
        public PracticeQuestionService.Fields toFields() {
            return new PracticeQuestionService.Fields(courseId, questionType, stem, optionsOf(options),
                    answerOf(correctAnswer), referenceAnswer, scoringPoints, analysis, sortNo);
        }
    }

    public record CreateInterviewRequest(
            @NotNull(message = "请选择所属课程") Long courseId,
            @NotBlank(message = "请填写题干") @Size(max = 10000, message = "题干最多 10000 个字符") String stem,
            @NotBlank(message = "请填写参考答案") @Size(max = 10000, message = "参考答案最多 10000 个字符") String referenceAnswer,
            @Size(max = 10000, message = "评分要点最多 10000 个字符") String scoringPoints,
            @Size(max = 10000, message = "追问方向最多 10000 个字符") String followupDirection) {
        public InterviewQuestionService.Fields toFields() {
            return new InterviewQuestionService.Fields(courseId, stem, referenceAnswer, scoringPoints, followupDirection);
        }
    }

    public record UpdateInterviewRequest(
            Long courseId,
            @Size(max = 10000, message = "题干最多 10000 个字符") @Pattern(regexp = NOT_BLANK, message = "题干不能为空") String stem,
            @Size(max = 10000, message = "参考答案最多 10000 个字符") @Pattern(regexp = NOT_BLANK, message = "参考答案不能为空") String referenceAnswer,
            @Size(max = 10000, message = "评分要点最多 10000 个字符") String scoringPoints,
            @Size(max = 10000, message = "追问方向最多 10000 个字符") String followupDirection) {
        public InterviewQuestionService.Fields toFields() {
            return new InterviewQuestionService.Fields(courseId, stem, referenceAnswer, scoringPoints, followupDirection);
        }
    }

    public record StatusRequest(
            @NotBlank(message = "请选择状态") @Pattern(regexp = "^(on_shelf|off_shelf)$", message = "状态不正确") String status) {
    }
}
