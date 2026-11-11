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
import com.yunshuzhixue.modules.question.entity.InterviewQuestion;
import com.yunshuzhixue.modules.question.mapper.InterviewQuestionMapper;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 面试题库（需求 6.5、8.7，A-QST-08~13）。新建默认上架；已被面试抽取过的题目不能删除，只能下架 */
@Service
public class InterviewQuestionService {

    public record Item(InterviewQuestion question, String courseTitle, boolean drawn) {
    }

    /** 编辑时为空的字段保持原值；scoring_points、followup_direction 传空字符串表示清空 */
    public record Fields(Long courseId, String stem, String referenceAnswer, String scoringPoints,
                         String followupDirection) {
    }

    private final InterviewQuestionMapper mapper;
    private final QuestionSupport support;
    private final List<QuestionUsageProvider> usageProviders;

    public InterviewQuestionService(InterviewQuestionMapper mapper, QuestionSupport support,
                                    List<QuestionUsageProvider> usageProviders) {
        this.mapper = mapper;
        this.support = support;
        this.usageProviders = usageProviders;
    }

    public PageResult<Item> list(PageQuery page, Long subjectId, Long courseId, String status) {
        LambdaQueryWrapper<InterviewQuestion> query = new LambdaQueryWrapper<InterviewQuestion>()
                .eq(courseId != null, InterviewQuestion::getCourseId, courseId)
                .eq(StringUtils.hasText(status), InterviewQuestion::getStatus, status);
        if (subjectId != null) {
            Set<Long> courseIds = support.courses().courseIdsOfSubject(subjectId);
            if (courseIds.isEmpty()) {
                return new PageResult<>(List.of(), 0, page.page(), page.pageSize());
            }
            query.in(InterviewQuestion::getCourseId, courseIds);
        }
        long total = mapper.selectCount(query);
        query.orderByAsc(InterviewQuestion::getCourseId).orderByAsc(InterviewQuestion::getId).last(page.limitClause());
        return new PageResult<>(enrich(mapper.selectList(query)), total, page.page(), page.pageSize());
    }

    public Item get(Long id) {
        return enrich(List.of(require(id))).get(0);
    }

    @Transactional
    public Item create(CurrentAdmin operator, Fields fields) {
        support.requireCourse(fields.courseId());
        InterviewQuestion question = new InterviewQuestion();
        question.setCourseId(fields.courseId());
        question.setStem(required(fields.stem(), "stem", "请填写题干"));
        question.setReferenceAnswer(required(fields.referenceAnswer(), "reference_answer", "请填写参考答案"));
        question.setScoringPoints(optional(fields.scoringPoints()));
        question.setFollowupDirection(optional(fields.followupDirection()));
        question.setStatus(PracticeQuestionService.ON_SHELF);
        mapper.insert(question);
        support.log(operator, AuditModules.INTERVIEW_QUESTION, "新增题目", "interview_question", question.getId(),
                question.getStem());
        return get(question.getId());
    }

    /** 修改不影响已生成的报告和进行中的场次（需求 6.5） */
    @Transactional
    public Item update(CurrentAdmin operator, Long id, Fields fields) {
        InterviewQuestion existing = require(id);
        if (fields.courseId() != null) {
            support.requireCourse(fields.courseId());
        }
        String stem = fields.stem() == null ? existing.getStem() : required(fields.stem(), "stem", "请填写题干");
        LambdaUpdateWrapper<InterviewQuestion> update = new LambdaUpdateWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getId, id)
                .set(fields.courseId() != null, InterviewQuestion::getCourseId, fields.courseId())
                .set(fields.stem() != null, InterviewQuestion::getStem, stem)
                .set(fields.referenceAnswer() != null, InterviewQuestion::getReferenceAnswer,
                        fields.referenceAnswer() == null ? null
                                : required(fields.referenceAnswer(), "reference_answer", "请填写参考答案"))
                .set(fields.scoringPoints() != null, InterviewQuestion::getScoringPoints, optional(fields.scoringPoints()))
                .set(fields.followupDirection() != null, InterviewQuestion::getFollowupDirection,
                        optional(fields.followupDirection()));
        if (StringUtils.hasText(update.getSqlSet())) {
            mapper.update(null, update);
        }
        support.log(operator, AuditModules.INTERVIEW_QUESTION, "编辑题目", "interview_question", id, stem);
        return get(id);
    }

    @Transactional
    public Item changeStatus(CurrentAdmin operator, Long id, String status) {
        InterviewQuestion question = require(id);
        if (!status.equals(question.getStatus())) {
            mapper.update(null, new LambdaUpdateWrapper<InterviewQuestion>().eq(InterviewQuestion::getId, id)
                    .set(InterviewQuestion::getStatus, status));
            support.log(operator, AuditModules.INTERVIEW_QUESTION,
                    PracticeQuestionService.ON_SHELF.equals(status) ? "上架题目" : "下架题目",
                    "interview_question", id, question.getStem());
        }
        return get(id);
    }

    @Transactional
    public void delete(CurrentAdmin operator, Long id) {
        InterviewQuestion question = require(id);
        if (!drawn(List.of(id)).isEmpty()) {
            throw new BusinessException(ErrorCode.CONTENT_IN_USE, "题目已在面试中被抽取过，不能删除，只能下架",
                    List.of(new FieldViolation("question", "已在面试中被抽取过")));
        }
        mapper.deleteById(id);
        support.log(operator, AuditModules.INTERVIEW_QUESTION, "删除题目", "interview_question", id, question.getStem());
    }

    /* ---------------- 内部 ---------------- */

    private List<Item> enrich(List<InterviewQuestion> questions) {
        if (questions.isEmpty()) {
            return List.of();
        }
        Map<Long, CourseRef> courses = support.courses().find(
                questions.stream().map(InterviewQuestion::getCourseId).collect(Collectors.toSet()));
        Set<Long> drawn = drawn(questions.stream().map(InterviewQuestion::getId).toList());
        return questions.stream().map(q -> new Item(q,
                courses.containsKey(q.getCourseId()) ? courses.get(q.getCourseId()).title() : "",
                drawn.contains(q.getId()))).toList();
    }

    private Set<Long> drawn(Collection<Long> ids) {
        Set<Long> result = new HashSet<>();
        usageProviders.forEach(p -> result.addAll(p.drawnInterviewQuestions(ids)));
        return result;
    }

    static String required(String value, String field, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, message, List.of(new FieldViolation(field, message)));
        }
        String trimmed = value.strip();
        if (trimmed.length() > PracticeQuestionRules.MAX_TEXT) {
            String reason = "最多 " + PracticeQuestionRules.MAX_TEXT + " 个字符";
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, reason, List.of(new FieldViolation(field, reason)));
        }
        return trimmed;
    }

    static String optional(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    private InterviewQuestion require(Long id) {
        InterviewQuestion question = mapper.selectById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return question;
    }
}
