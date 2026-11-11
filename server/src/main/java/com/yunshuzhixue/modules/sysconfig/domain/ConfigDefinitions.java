package com.yunshuzhixue.modules.sysconfig.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * 系统配置项及取值校验（需求第 7 章，键见 03.数据库设计.md 5.2）。默认值由 Flyway V2 写入。
 */
public final class ConfigDefinitions {

    /** 校验配置值，合法时返回 null，否则返回错误说明 */
    @FunctionalInterface
    public interface Validator {
        String validate(JsonNode value);
    }

    public record Definition(String key, String description, Validator validator) {
    }

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final List<String> QUESTION_TYPES =
            List.of("true_false", "single_choice", "multiple_choice", "short_answer");

    private static final Map<String, Definition> DEFINITIONS = new LinkedHashMap<>();

    static {
        add("ai.interview_daily_limit", "每人每日 AI 面试次数", integer(0, 100));
        add("ai.scoring_daily_limit", "每人每日简答题 AI 评分次数", integer(0, 1000));
        add("learning.mastery_threshold", "已掌握达标线（百分比）", integer(1, 100));
        add("learning.short_answer_pass_score", "简答题及格线（分）", integer(0, 100));
        add("practice.random_question_count", "随机抽题数量", integer(1, 100));
        add("practice.random_min_question_count", "随机抽题最低题量", integer(1, 100));
        add("exam.paper_rule", "模拟考试组卷规则", ConfigDefinitions::paperRule);
        add("exam.duration_minutes", "模拟考试时长（分钟）", integer(1, 300));
        add("exam.min_question_count", "模拟考试最低题量", integer(1, 200));
        add("interview.main_question_count", "面试主问题数量", integer(1, 20));
        add("interview.max_followup_count", "每题最多追问次数", integer(0, 10));
        add("interview.duration_minutes", "面试时长上限（分钟）", integer(5, 180));
        add("interview.min_question_count", "面试最低题量", integer(1, 20));
        add("interview.voice_end_pause_seconds", "语音说完判定停顿（秒）", integer(1, 10));
        add("interview.voice_concurrency_limit", "语音面试同时进行上限（场）", integer(1, 1000));
        add("interview.grade_thresholds", "面试等级分数线", ConfigDefinitions::gradeThresholds);
        add("help.contact", "客服联系方式", ConfigDefinitions::contact);
    }

    private ConfigDefinitions() {
    }

    public static List<Definition> all() {
        return List.copyOf(DEFINITIONS.values());
    }

    public static Optional<Definition> find(String key) {
        return Optional.ofNullable(DEFINITIONS.get(key));
    }

    private static void add(String key, String description, Validator validator) {
        DEFINITIONS.put(key, new Definition(key, description, validator));
    }

    private static Validator integer(int min, int max) {
        return value -> isIntIn(value, min, max) ? null : "请填写 " + min + "~" + max + " 之间的整数";
    }

    private static boolean isIntIn(JsonNode value, int min, int max) {
        return value != null && value.isIntegralNumber() && value.canConvertToInt()
                && value.intValue() >= min && value.intValue() <= max;
    }

    /** 各题型 {count: 0~100, score: 1~100}，至少有 1 道题 */
    private static String paperRule(JsonNode value) {
        if (value == null || !value.isObject() || value.size() != QUESTION_TYPES.size()) {
            return "组卷规则需包含判断、单选、多选、简答四种题型";
        }
        int total = 0;
        for (String type : QUESTION_TYPES) {
            JsonNode rule = value.get(type);
            if (rule == null || !rule.isObject() || rule.size() != 2
                    || !isIntIn(rule.get("count"), 0, 100) || !isIntIn(rule.get("score"), 1, 100)) {
                return "每种题型的数量为 0~100、单题分值为 1~100 的整数";
            }
            total += rule.get("count").intValue();
        }
        return total > 0 ? null : "试卷至少需要 1 道题";
    }

    /** {excellent, good}，0~100，优秀分数线必须高于良好 */
    private static String gradeThresholds(JsonNode value) {
        if (value == null || !value.isObject() || value.size() != 2
                || !isIntIn(value.get("excellent"), 0, 100) || !isIntIn(value.get("good"), 0, 100)) {
            return "分数线为 0~100 的整数";
        }
        return value.get("excellent").intValue() > value.get("good").intValue() ? null : "优秀分数线必须高于良好分数线";
    }

    /** {wechat_qrcode_url, email, phone}，均为选填 */
    private static String contact(JsonNode value) {
        if (value == null || !value.isObject() || value.size() != 3) {
            return "联系方式需包含微信二维码、邮箱、电话三项";
        }
        String error = optionalText(value.get("wechat_qrcode_url"), 500, null);
        if (error == null) {
            error = optionalText(value.get("email"), 100, EMAIL);
        }
        if (error == null) {
            error = optionalText(value.get("phone"), 30, null);
        }
        return error;
    }

    private static String optionalText(JsonNode node, int maxLength, Pattern pattern) {
        if (node == null) {
            return "联系方式缺少字段";
        }
        if (node.isNull()) {
            return null;
        }
        if (!node.isString() || node.asString().length() > maxLength) {
            return "联系方式最多 " + maxLength + " 个字符";
        }
        if (pattern != null && !pattern.matcher(node.asString()).matches()) {
            return "邮箱格式不正确";
        }
        return null;
    }
}
