package com.yunshuzhixue.modules.question.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.infra.excel.ExcelSheets;
import com.yunshuzhixue.modules.audit.api.AuditModules;
import com.yunshuzhixue.modules.content.api.CourseDirectory.CourseRef;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import com.yunshuzhixue.modules.question.domain.PracticeQuestionRules;
import com.yunshuzhixue.modules.question.domain.PracticeQuestionRules.Content;
import com.yunshuzhixue.modules.question.domain.PracticeQuestionRules.Option;
import com.yunshuzhixue.modules.question.domain.QuestionImportParsers;
import com.yunshuzhixue.modules.question.entity.InterviewQuestion;
import com.yunshuzhixue.modules.question.entity.PracticeQuestion;
import com.yunshuzhixue.modules.question.mapper.InterviewQuestionMapper;
import com.yunshuzhixue.modules.question.mapper.PracticeQuestionMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 批量导入刷题题目和面试题目（A-QST-07、14）。按第 1 行表头识别列（顺序不限，其他列忽略），
 * "所属课程"填课程 ID 或课程名称；部分行失败不影响成功的行；导入的题目默认上架。
 */
@Service
public class QuestionImportService {

    public record ImportResult(int successCount, int failureCount, List<Map<String, Object>> failures) {
    }

    /** 刷题题目模板表头 */
    public static final List<String> PRACTICE_HEADERS = List.of("所属课程（必填）", "题型（必填）", "题干（必填）",
            "选项A", "选项B", "选项C", "选项D", "选项E", "选项F", "选项G", "选项H",
            "正确答案", "参考答案", "评分要点", "解析", "排序号");
    /** 面试题目模板表头 */
    public static final List<String> INTERVIEW_HEADERS =
            List.of("所属课程（必填）", "题干（必填）", "参考答案（必填）", "评分要点", "追问方向");

    private enum Col {
        COURSE(true, "所属课程", "课程"),
        TYPE(true, "题型"),
        STEM(true, "题干", "题目"),
        ANSWER(false, "正确答案", "答案"),
        REFERENCE(false, "参考答案"),
        SCORING(false, "评分要点"),
        ANALYSIS(false, "解析"),
        SORT(false, "排序号", "排序"),
        FOLLOWUP(false, "追问方向");

        final boolean requiredForPractice;
        final List<String> aliases;

        Col(boolean required, String... aliases) {
            this.requiredForPractice = required;
            this.aliases = List.of(aliases);
        }
    }

    private final PracticeQuestionMapper practiceMapper;
    private final InterviewQuestionMapper interviewMapper;
    private final PracticeQuestionService practiceService;
    private final QuestionSupport support;

    public QuestionImportService(PracticeQuestionMapper practiceMapper, InterviewQuestionMapper interviewMapper,
                                 PracticeQuestionService practiceService, QuestionSupport support) {
        this.practiceMapper = practiceMapper;
        this.interviewMapper = interviewMapper;
        this.practiceService = practiceService;
        this.support = support;
    }

    @Transactional
    public ImportResult importPractice(CurrentAdmin operator, InputStream in) {
        ExcelSheets.Table table = read(in);
        Map<Col, Integer> cols = locate(table.headers(), List.of(Col.COURSE, Col.TYPE, Col.STEM));
        Map<Integer, Integer> optionCols = optionColumns(table.headers());
        QuestionSupport.CourseMatcher matcher = support.matcher();
        Map<Long, Integer> nextSort = maxSortByCourse();
        List<Map<String, Object>> failures = new ArrayList<>();
        int success = 0;
        for (ExcelSheets.SheetRow row : table.rows()) {
            String[] reason = new String[1];
            CourseRef course = cell(row, cols, Col.COURSE).isEmpty() ? null : matcher.match(cell(row, cols, Col.COURSE), reason);
            String typeText = cell(row, cols, Col.TYPE);
            String type = typeText.isEmpty() ? null : QuestionImportParsers.type(typeText);
            if (cell(row, cols, Col.COURSE).isEmpty()) {
                reason[0] = "必填项为空：所属课程";
            } else if (course != null && typeText.isEmpty()) {
                reason[0] = "必填项为空：题型";
            } else if (course != null && type == null) {
                reason[0] = "题型不正确：" + typeText + "（应为判断、单选、多选、简答）";
            }
            Content content = null;
            if (reason[0] == null) {
                List<Option> options = new ArrayList<>();
                optionCols.forEach((index, column) -> {
                    String text = row.cell(column);
                    if (!text.isEmpty()) {
                        options.add(new Option(String.valueOf(PracticeQuestionRules.KEYS.charAt(index)), text));
                    }
                });
                String answerText = cell(row, cols, Col.ANSWER);
                Object answer = null;
                String reference = cell(row, cols, Col.REFERENCE);
                if (PracticeQuestionRules.TRUE_FALSE.equals(type)) {
                    answer = answerText.isEmpty() ? null : QuestionImportParsers.trueFalse(answerText);
                    if (answer == null) {
                        reason[0] = "判断题正确答案应为 对 或 错";
                    }
                } else if (PracticeQuestionRules.SHORT_ANSWER.equals(type)) {
                    // 简答题的参考答案也可以写在"正确答案"列
                    if (reference.isEmpty()) {
                        reference = answerText;
                    }
                } else {
                    answer = answerText.isEmpty() ? null : QuestionImportParsers.choices(answerText);
                    if (answer == null) {
                        reason[0] = "正确答案应为选项字母，多选用逗号分隔，如 A,C";
                    } else if (!consecutive(options)) {
                        reason[0] = "选项须从选项A开始依次填写，中间不能空缺";
                    }
                }
                if (reason[0] == null) {
                    PracticeQuestionRules.Result result = PracticeQuestionRules.check(new Content(type,
                            cell(row, cols, Col.STEM), options.isEmpty() ? null : options, answer, reference,
                            cell(row, cols, Col.SCORING), cell(row, cols, Col.ANALYSIS)));
                    if (result.ok()) {
                        content = result.content();
                    } else {
                        reason[0] = result.errors().get(0).reason();
                    }
                }
            }
            Integer sortNo = null;
            String sortText = cell(row, cols, Col.SORT);
            if (reason[0] == null && !sortText.isEmpty()) {
                try {
                    sortNo = Integer.parseInt(sortText.trim());
                } catch (NumberFormatException e) {
                    reason[0] = "排序号需为整数";
                }
            }
            if (reason[0] != null) {
                failures.add(Map.of("row_no", row.rowNo(), "reason", reason[0]));
                continue;
            }
            PracticeQuestion question = new PracticeQuestion();
            question.setCourseId(course.id());
            practiceService.apply(question, content);
            question.setSortNo(sortNo != null ? sortNo : nextSort.merge(course.id(), 1, Integer::sum));
            if (sortNo != null) {
                nextSort.merge(course.id(), sortNo, Math::max);
            }
            question.setStatus(PracticeQuestionService.ON_SHELF);
            practiceMapper.insert(question);
            success++;
        }
        if (success > 0) {
            support.log(operator, AuditModules.PRACTICE_QUESTION, "导入题目", "practice_question", null,
                    "成功 " + success + " 道，失败 " + failures.size() + " 行");
        }
        return new ImportResult(success, failures.size(), failures);
    }

    @Transactional
    public ImportResult importInterview(CurrentAdmin operator, InputStream in) {
        ExcelSheets.Table table = read(in);
        Map<Col, Integer> cols = locate(table.headers(), List.of(Col.COURSE, Col.STEM, Col.REFERENCE));
        QuestionSupport.CourseMatcher matcher = support.matcher();
        List<Map<String, Object>> failures = new ArrayList<>();
        int success = 0;
        for (ExcelSheets.SheetRow row : table.rows()) {
            String[] reason = new String[1];
            String courseText = cell(row, cols, Col.COURSE);
            String stem = cell(row, cols, Col.STEM);
            String reference = cell(row, cols, Col.REFERENCE);
            String scoring = cell(row, cols, Col.SCORING);
            String followup = cell(row, cols, Col.FOLLOWUP);
            CourseRef course = null;
            List<String> missing = new ArrayList<>();
            if (courseText.isEmpty()) {
                missing.add("所属课程");
            }
            if (stem.isEmpty()) {
                missing.add("题干");
            }
            if (reference.isEmpty()) {
                missing.add("参考答案");
            }
            if (!missing.isEmpty()) {
                reason[0] = "必填项为空：" + String.join("、", missing);
            } else if ((course = matcher.match(courseText, reason)) == null) {
                // reason 已给出
            } else if (stem.length() > PracticeQuestionRules.MAX_TEXT || reference.length() > PracticeQuestionRules.MAX_TEXT
                    || scoring.length() > PracticeQuestionRules.MAX_TEXT || followup.length() > PracticeQuestionRules.MAX_TEXT) {
                reason[0] = "题干、参考答案、评分要点、追问方向各最多 " + PracticeQuestionRules.MAX_TEXT + " 个字符";
            }
            if (reason[0] != null) {
                failures.add(Map.of("row_no", row.rowNo(), "reason", reason[0]));
                continue;
            }
            InterviewQuestion question = new InterviewQuestion();
            question.setCourseId(course.id());
            question.setStem(stem);
            question.setReferenceAnswer(reference);
            question.setScoringPoints(scoring.isEmpty() ? null : scoring);
            question.setFollowupDirection(followup.isEmpty() ? null : followup);
            question.setStatus(PracticeQuestionService.ON_SHELF);
            interviewMapper.insert(question);
            success++;
        }
        if (success > 0) {
            support.log(operator, AuditModules.INTERVIEW_QUESTION, "导入题目", "interview_question", null,
                    "成功 " + success + " 道，失败 " + failures.size() + " 行");
        }
        return new ImportResult(success, failures.size(), failures);
    }

    /* ---------------- 内部 ---------------- */

    private static ExcelSheets.Table read(InputStream in) {
        try {
            return ExcelSheets.readTable(in);
        } catch (ExcelSheets.TooManyRowsException e) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "单次最多导入 " + ExcelSheets.MAX_IMPORT_ROWS + " 行");
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.FILE_INVALID, "无法读取文件，请使用 xlsx 格式的导入模板");
        }
    }

    private static Map<Col, Integer> locate(List<String> headers, List<Col> required) {
        Map<Col, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String key = headerKey(headers.get(i));
            for (Col col : Col.values()) {
                if (!result.containsKey(col) && col.aliases.stream().anyMatch(a -> headerKey(a).equals(key))) {
                    result.put(col, i);
                    break;
                }
            }
        }
        List<String> missing = required.stream().filter(c -> !result.containsKey(c)).map(c -> c.aliases.get(0)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_INVALID,
                    "第 1 行表头缺少：" + String.join("、", missing) + "，请使用导入模板");
        }
        return result;
    }

    /** "选项A"~"选项H"所在的列：key 为选项序号（0 表示 A） */
    private static Map<Integer, Integer> optionColumns(List<String> headers) {
        Map<Integer, Integer> result = new java.util.TreeMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String key = headerKey(headers.get(i)).toUpperCase();
            if (key.matches("选项[A-H]")) {
                result.putIfAbsent(key.charAt(2) - 'A', i);
            }
        }
        return result;
    }

    private static boolean consecutive(List<Option> options) {
        for (int i = 0; i < options.size(); i++) {
            if (!options.get(i).key().equals(String.valueOf(PracticeQuestionRules.KEYS.charAt(i)))) {
                return false;
            }
        }
        return true;
    }

    private static String headerKey(String header) {
        return header.replaceAll("[\\s（）()\\[\\]【】*＊]", "").replace("必填", "").toLowerCase();
    }

    private static String cell(ExcelSheets.SheetRow row, Map<Col, Integer> cols, Col col) {
        Integer index = cols.get(col);
        return index == null ? "" : row.cell(index);
    }

    private Map<Long, Integer> maxSortByCourse() {
        Map<Long, Integer> result = new HashMap<>();
        for (Map<String, Object> row : practiceMapper.selectMaps(new QueryWrapper<PracticeQuestion>()
                .select("course_id", "MAX(sort_no) AS max_sort").groupBy("course_id"))) {
            result.put(((Number) row.get("course_id")).longValue(), ((Number) row.get("max_sort")).intValue());
        }
        return result;
    }
}
