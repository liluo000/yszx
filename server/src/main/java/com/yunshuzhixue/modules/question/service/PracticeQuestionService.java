package com.yunshuzhixue.modules.question.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yunshuzhixue.common.api.FieldViolation;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.audit.api.AuditModules;
import com.yunshuzhixue.modules.content.api.CourseDirectory.CourseRef;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import com.yunshuzhixue.modules.question.api.QuestionUsageProvider;
import com.yunshuzhixue.modules.question.domain.PracticeQuestionRules;
import com.yunshuzhixue.modules.question.domain.PracticeQuestionRules.Content;
import com.yunshuzhixue.modules.question.domain.PracticeQuestionRules.Option;
import com.yunshuzhixue.modules.question.entity.PracticeQuestion;
import com.yunshuzhixue.modules.question.mapper.PracticeQuestionMapper;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 刷题题库（需求 6.4、8.7，A-QST-01~06）。新建默认上架；已被作答的题目不能删除，只能下架 */
@Service
public class PracticeQuestionService {

    public static final String ON_SHELF = "on_shelf";

    /** 题目及展示所需的课程名、是否已有作答 */
    public record Item(PracticeQuestion question, String courseTitle, List<Option> options, JsonNode correctAnswer,
                       boolean answered) {
    }

    /** 编辑时为空的字段保持原值；analysis、scoring_points 传空字符串表示清空 */
    public record Fields(Long courseId, String questionType, String stem, List<Option> options, Object correctAnswer,
                         String referenceAnswer, String scoringPoints, String analysis, Integer sortNo) {
    }

    private final PracticeQuestionMapper mapper;
    private final QuestionSupport support;
    private final List<QuestionUsageProvider> usageProviders;
    private final JsonMapper json;

    public PracticeQuestionService(PracticeQuestionMapper mapper, QuestionSupport support,
                                   List<QuestionUsageProvider> usageProviders, JsonMapper json) {
        this.mapper = mapper;
        this.support = support;
        this.usageProviders = usageProviders;
        this.json = json;
    }

    public PageResult<Item> list(PageQuery page, Long subjectId, Long courseId, String type, String status) {
        LambdaQueryWrapper<PracticeQuestion> query = new LambdaQueryWrapper<PracticeQuestion>()
                .eq(courseId != null, PracticeQuestion::getCourseId, courseId)
                .eq(StringUtils.hasText(type), PracticeQuestion::getQuestionType, type)
                .eq(StringUtils.hasText(status), PracticeQuestion::getStatus, status);
        if (subjectId != null) {
            Set<Long> courseIds = support.courses().courseIdsOfSubject(subjectId);
            if (courseIds.isEmpty()) {
                return new PageResult<>(List.of(), 0, page.page(), page.pageSize());
            }
            query.in(PracticeQuestion::getCourseId, courseIds);
        }
        long total = mapper.selectCount(query);
        query.orderByAsc(PracticeQuestion::getCourseId).orderByAsc(PracticeQuestion::getSortNo)
                .orderByAsc(PracticeQuestion::getId).last(page.limitClause());
        return new PageResult<>(enrich(mapper.selectList(query)), total, page.page(), page.pageSize());
    }

    public Item get(Long id) {
        return enrich(List.of(require(id))).get(0);
    }

    @Transactional
    public Item create(CurrentAdmin operator, Fields fields) {
        support.requireCourse(fields.courseId());
        Content content = check(new Content(fields.questionType(), fields.stem(), fields.options(),
                fields.correctAnswer(), fields.referenceAnswer(), fields.scoringPoints(), fields.analysis()));
        PracticeQuestion question = new PracticeQuestion();
        question.setCourseId(fields.courseId());
        apply(question, content);
        question.setSortNo(fields.sortNo());
        question.setStatus(ON_SHELF);
        mapper.insert(question);
        support.log(operator, AuditModules.PRACTICE_QUESTION, "新增题目", "practice_question", question.getId(),
                content.stem());
        return get(question.getId());
    }

    /** 修改后历史作答保留快照，错题本和之后的作答使用最新版本（需求 6.4） */
    @Transactional
    public Item update(CurrentAdmin operator, Long id, Fields fields) {
        PracticeQuestion existing = require(id);
        if (fields.courseId() != null) {
            support.requireCourse(fields.courseId());
        }
        String type = fields.questionType() == null ? existing.getQuestionType() : fields.questionType();
        boolean typeChanged = !type.equals(existing.getQuestionType());
        Content merged = new Content(type,
                fields.stem() == null ? existing.getStem() : fields.stem(),
                fields.options() != null ? fields.options() : (typeChanged ? null : readOptions(existing.getOptions())),
                fields.correctAnswer() != null ? fields.correctAnswer()
                        : (typeChanged ? null : readAnswer(existing.getCorrectAnswer())),
                fields.referenceAnswer() == null ? existing.getReferenceAnswer() : fields.referenceAnswer(),
                fields.scoringPoints() == null ? existing.getScoringPoints() : fields.scoringPoints(),
                fields.analysis() == null ? existing.getAnalysis() : fields.analysis());
        Content content = check(merged);
        PracticeQuestion update = new PracticeQuestion();
        apply(update, content);
        mapper.update(null, new LambdaUpdateWrapper<PracticeQuestion>().eq(PracticeQuestion::getId, id)
                .set(fields.courseId() != null, PracticeQuestion::getCourseId, fields.courseId())
                .set(PracticeQuestion::getQuestionType, update.getQuestionType())
                .set(PracticeQuestion::getStem, update.getStem())
                .set(PracticeQuestion::getOptions, update.getOptions())
                .set(PracticeQuestion::getCorrectAnswer, update.getCorrectAnswer())
                .set(PracticeQuestion::getReferenceAnswer, update.getReferenceAnswer())
                .set(PracticeQuestion::getScoringPoints, update.getScoringPoints())
                .set(PracticeQuestion::getAnalysis, update.getAnalysis())
                .set(fields.sortNo() != null, PracticeQuestion::getSortNo, fields.sortNo()));
        support.log(operator, AuditModules.PRACTICE_QUESTION, "编辑题目", "practice_question", id, content.stem());
        return get(id);
    }

    @Transactional
    public Item changeStatus(CurrentAdmin operator, Long id, String status) {
        PracticeQuestion question = require(id);
        if (!status.equals(question.getStatus())) {
            mapper.update(null, new LambdaUpdateWrapper<PracticeQuestion>().eq(PracticeQuestion::getId, id)
                    .set(PracticeQuestion::getStatus, status));
            support.log(operator, AuditModules.PRACTICE_QUESTION, ON_SHELF.equals(status) ? "上架题目" : "下架题目",
                    "practice_question", id, question.getStem());
        }
        return get(id);
    }

    @Transactional
    public void delete(CurrentAdmin operator, Long id) {
        PracticeQuestion question = require(id);
        if (!answered(List.of(id)).isEmpty()) {
            throw new BusinessException(ErrorCode.CONTENT_IN_USE, "题目已被学员作答过，不能删除，只能下架",
                    List.of(new FieldViolation("question", "已有学员作答记录")));
        }
        mapper.deleteById(id);
        support.log(operator, AuditModules.PRACTICE_QUESTION, "删除题目", "practice_question", id, question.getStem());
    }

    /* ---------------- 供导入和课程统计使用 ---------------- */

    /** 校验题目内容，不通过时抛出 VALIDATION_FAILED 并给出字段错误 */
    Content check(Content input) {
        PracticeQuestionRules.Result result = PracticeQuestionRules.check(input);
        if (!result.ok()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, result.errors().get(0).reason(),
                    result.errors().stream().map(v -> (Object) new FieldViolation(v.field(), v.reason())).toList());
        }
        return result.content();
    }

    void apply(PracticeQuestion target, Content content) {
        target.setQuestionType(content.type());
        target.setStem(content.stem());
        target.setOptions(content.options() == null ? null : json.writeValueAsString(content.options()));
        target.setCorrectAnswer(content.correctAnswer() == null ? null : json.writeValueAsString(content.correctAnswer()));
        target.setReferenceAnswer(content.referenceAnswer());
        target.setScoringPoints(content.scoringPoints());
        target.setAnalysis(content.analysis());
    }

    /* ---------------- 内部 ---------------- */

    private List<Item> enrich(List<PracticeQuestion> questions) {
        if (questions.isEmpty()) {
            return List.of();
        }
        Map<Long, CourseRef> courses = support.courses().find(
                questions.stream().map(PracticeQuestion::getCourseId).collect(java.util.stream.Collectors.toSet()));
        Set<Long> answered = answered(questions.stream().map(PracticeQuestion::getId).toList());
        return questions.stream().map(q -> new Item(q,
                courses.containsKey(q.getCourseId()) ? courses.get(q.getCourseId()).title() : "",
                readOptions(q.getOptions()),
                q.getCorrectAnswer() == null ? null : json.readTree(q.getCorrectAnswer()),
                answered.contains(q.getId()))).toList();
    }

    private Set<Long> answered(Collection<Long> ids) {
        Set<Long> result = new HashSet<>();
        usageProviders.forEach(p -> result.addAll(p.answeredPracticeQuestions(ids)));
        return result;
    }

    private List<Option> readOptions(String text) {
        return text == null ? null : json.readValue(text, new TypeReference<List<Option>>() { });
    }

    private Object readAnswer(String text) {
        if (text == null) {
            return null;
        }
        JsonNode node = json.readTree(text);
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return json.readValue(text, new TypeReference<List<String>>() { });
    }

    private PracticeQuestion require(Long id) {
        PracticeQuestion question = mapper.selectById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return question;
    }
}
