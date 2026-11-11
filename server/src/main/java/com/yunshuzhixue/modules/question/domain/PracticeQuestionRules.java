package com.yunshuzhixue.modules.question.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 刷题题目的题型规则（需求 6.4）：
 * <ul>
 *   <li>判断题：正确答案为 true / false；</li>
 *   <li>单选题：2~8 个选项（key 依次为 A~H），正确答案 1 个；</li>
 *   <li>多选题：2~8 个选项，正确答案至少 2 个；</li>
 *   <li>简答题：参考答案必填，评分要点选填。</li>
 * </ul>
 * 不符合题型的字段会被清空（如判断题的选项），保证存储与题型一致。
 */
public final class PracticeQuestionRules {

    public static final String TRUE_FALSE = "true_false";
    public static final String SINGLE_CHOICE = "single_choice";
    public static final String MULTIPLE_CHOICE = "multiple_choice";
    public static final String SHORT_ANSWER = "short_answer";
    public static final List<String> TYPES = List.of(TRUE_FALSE, SINGLE_CHOICE, MULTIPLE_CHOICE, SHORT_ANSWER);
    public static final Map<String, String> TYPE_LABELS = Map.of(TRUE_FALSE, "判断题", SINGLE_CHOICE, "单选题",
            MULTIPLE_CHOICE, "多选题", SHORT_ANSWER, "简答题");
    public static final String KEYS = "ABCDEFGH";

    public static final int MAX_TEXT = 10_000;
    public static final int MAX_OPTION_TEXT = 1_000;

    public record Option(String key, String text) {
    }

    /** 题目内容；correctAnswer 为 Boolean（判断题）或 List&lt;String&gt;（选择题），简答题为 null */
    public record Content(String type, String stem, List<Option> options, Object correctAnswer,
                          String referenceAnswer, String scoringPoints, String analysis) {
    }

    /** 校验结果：errors 为空时 content 为规范化后的内容 */
    public record Result(Content content, List<Violation> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    public record Violation(String field, String reason) {
    }

    private PracticeQuestionRules() {
    }

    public static Result check(Content input) {
        List<Violation> errors = new ArrayList<>();
        String type = input.type();
        if (!TYPES.contains(type)) {
            errors.add(new Violation("question_type", "题型不正确"));
            return new Result(null, errors);
        }
        String stem = trim(input.stem());
        if (stem == null) {
            errors.add(new Violation("stem", "请填写题干"));
        } else if (stem.length() > MAX_TEXT) {
            errors.add(new Violation("stem", "题干最多 " + MAX_TEXT + " 个字符"));
        }
        String analysis = trim(input.analysis());
        if (analysis != null && analysis.length() > MAX_TEXT) {
            errors.add(new Violation("analysis", "解析最多 " + MAX_TEXT + " 个字符"));
        }
        List<Option> options = null;
        Object answer = null;
        String reference = null;
        String scoring = null;
        switch (type) {
            case TRUE_FALSE -> {
                if (input.correctAnswer() instanceof Boolean b) {
                    answer = b;
                } else {
                    errors.add(new Violation("correct_answer", "请选择正确答案（对或错）"));
                }
            }
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> {
                options = checkOptions(input.options(), errors);
                answer = checkChoices(type, input.correctAnswer(), options, errors);
            }
            default -> {
                reference = trim(input.referenceAnswer());
                scoring = trim(input.scoringPoints());
                if (reference == null) {
                    errors.add(new Violation("reference_answer", "请填写参考答案"));
                } else if (reference.length() > MAX_TEXT) {
                    errors.add(new Violation("reference_answer", "参考答案最多 " + MAX_TEXT + " 个字符"));
                }
                if (scoring != null && scoring.length() > MAX_TEXT) {
                    errors.add(new Violation("scoring_points", "评分要点最多 " + MAX_TEXT + " 个字符"));
                }
            }
        }
        if (!errors.isEmpty()) {
            return new Result(null, errors);
        }
        return new Result(new Content(type, stem, options, answer, reference, scoring, analysis), errors);
    }

    private static List<Option> checkOptions(List<Option> input, List<Violation> errors) {
        if (input == null || input.size() < 2 || input.size() > 8) {
            errors.add(new Violation("options", "选项为 2~8 个"));
            return null;
        }
        List<Option> result = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            String key = String.valueOf(KEYS.charAt(i));
            Option option = input.get(i);
            String text = option == null ? null : trim(option.text());
            if (option == null || !key.equals(option.key())) {
                errors.add(new Violation("options", "选项须依次为 A~H"));
                return null;
            }
            if (text == null) {
                errors.add(new Violation("options", "选项 " + key + " 内容不能为空"));
                return null;
            }
            if (text.length() > MAX_OPTION_TEXT) {
                errors.add(new Violation("options", "选项 " + key + " 最多 " + MAX_OPTION_TEXT + " 个字符"));
                return null;
            }
            result.add(new Option(key, text));
        }
        return result;
    }

    private static List<String> checkChoices(String type, Object answer, List<Option> options, List<Violation> errors) {
        if (options == null) {
            return null;
        }
        if (!(answer instanceof List<?> list) || list.stream().anyMatch(k -> !(k instanceof String))) {
            errors.add(new Violation("correct_answer", "请选择正确选项"));
            return null;
        }
        Set<String> valid = new LinkedHashSet<>();
        options.forEach(o -> valid.add(o.key()));
        List<String> keys = list.stream().map(Object::toString).distinct().sorted().toList();
        if (!valid.containsAll(keys)) {
            errors.add(new Violation("correct_answer", "正确选项须在选项范围内"));
            return null;
        }
        if (SINGLE_CHOICE.equals(type) && keys.size() != 1) {
            errors.add(new Violation("correct_answer", "单选题只能有 1 个正确选项"));
            return null;
        }
        if (MULTIPLE_CHOICE.equals(type) && keys.size() < 2) {
            errors.add(new Violation("correct_answer", "多选题至少需要 2 个正确选项"));
            return null;
        }
        return keys;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
