package com.yunshuzhixue.modules.question.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 批量导入时把 Excel 中的文字转成题型和答案，兼容常见写法 */
public final class QuestionImportParsers {

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("判断", PracticeQuestionRules.TRUE_FALSE), Map.entry("判断题", PracticeQuestionRules.TRUE_FALSE),
            Map.entry("true_false", PracticeQuestionRules.TRUE_FALSE),
            Map.entry("单选", PracticeQuestionRules.SINGLE_CHOICE), Map.entry("单选题", PracticeQuestionRules.SINGLE_CHOICE),
            Map.entry("single_choice", PracticeQuestionRules.SINGLE_CHOICE),
            Map.entry("多选", PracticeQuestionRules.MULTIPLE_CHOICE), Map.entry("多选题", PracticeQuestionRules.MULTIPLE_CHOICE),
            Map.entry("multiple_choice", PracticeQuestionRules.MULTIPLE_CHOICE),
            Map.entry("简答", PracticeQuestionRules.SHORT_ANSWER), Map.entry("简答题", PracticeQuestionRules.SHORT_ANSWER),
            Map.entry("short_answer", PracticeQuestionRules.SHORT_ANSWER));

    private QuestionImportParsers() {
    }

    /** 题型：判断/单选/多选/简答（可带"题"字），无法识别时返回 null */
    public static String type(String text) {
        return TYPES.get(text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT));
    }

    /** 判断题答案：对/错、正确/错误、√/×、T/F、true/false、是/否 */
    public static Boolean trueFalse(String text) {
        String t = text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return switch (t) {
            case "对", "正确", "√", "✓", "t", "true", "是", "y", "yes" -> Boolean.TRUE;
            case "错", "错误", "×", "✗", "x", "f", "false", "否", "n", "no" -> Boolean.FALSE;
            default -> null;
        };
    }

    /** 选择题答案：A、"A,C"、"A，C"、"A、C"、"AC" 都可以，返回大写字母列表 */
    public static List<String> choices(String text) {
        List<String> keys = new ArrayList<>();
        for (char c : text.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c >= 'A' && c <= 'H') {
                keys.add(String.valueOf(c));
            } else if (!(Character.isWhitespace(c) || ",，、;；/".indexOf(c) >= 0)) {
                return null;
            }
        }
        return keys;
    }
}
