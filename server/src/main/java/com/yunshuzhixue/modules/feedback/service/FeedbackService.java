package com.yunshuzhixue.modules.feedback.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.time.BeijingDates;
import com.yunshuzhixue.common.time.UtcTime;
import com.yunshuzhixue.common.web.ClientIps;
import com.yunshuzhixue.modules.account.api.StudentDirectory;
import com.yunshuzhixue.modules.audit.api.AuditModules;
import com.yunshuzhixue.modules.audit.api.OperationLogCommand;
import com.yunshuzhixue.modules.audit.api.OperationLogRecorder;
import com.yunshuzhixue.modules.content.api.CourseDirectory;
import com.yunshuzhixue.modules.content.api.CourseDirectory.CourseRef;
import com.yunshuzhixue.modules.feedback.entity.Feedback;
import com.yunshuzhixue.modules.feedback.mapper.FeedbackMapper;
import com.yunshuzhixue.modules.iam.api.CurrentAdmin;
import com.yunshuzhixue.modules.message.api.StudentMessageSender;
import com.yunshuzhixue.modules.question.api.PracticeQuestionDirectory;
import com.yunshuzhixue.modules.question.api.PracticeQuestionDirectory.QuestionRef;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 纠错与反馈（需求 6.6，A-FDB-01、02）：待处理的按题目或课程合并，可一次处理全部并发站内信 */
@Service
public class FeedbackService {

    public static final String QUESTION_CORRECTION = "question_correction";
    public static final String VIDEO_INVALID = "video_invalid";
    public static final String PENDING = "pending";
    public static final Map<String, String> ERROR_TYPE_LABELS = Map.of("stem_error", "题干有误",
            "answer_error", "答案有误", "analysis_error", "解析有误", "other", "其他");

    /** 一组反馈：rows 按提交时间倒序，第一条为最新 */
    public record Group(List<Feedback> rows, String courseTitle, String stem, String studentNickname) {
        public Feedback latest() {
            return rows.get(0);
        }
    }

    private final FeedbackMapper mapper;
    private final CourseDirectory courses;
    private final PracticeQuestionDirectory questions;
    private final StudentDirectory students;
    private final StudentMessageSender messages;
    private final OperationLogRecorder recorder;

    public FeedbackService(FeedbackMapper mapper, CourseDirectory courses, PracticeQuestionDirectory questions,
                           StudentDirectory students, StudentMessageSender messages, OperationLogRecorder recorder) {
        this.mapper = mapper;
        this.courses = courses;
        this.questions = questions;
        this.students = students;
        this.messages = messages;
        this.recorder = recorder;
    }

    public PageResult<Group> list(PageQuery page, String feedbackType, String status, Long courseId, String errorType,
                                  LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "结束日期不能早于开始日期");
        }
        FeedbackMapper.Filter filter = new FeedbackMapper.Filter(feedbackType, status, courseId, errorType,
                startDate == null ? null : BeijingDates.startOfDayUtc(startDate),
                endDate == null ? null : BeijingDates.endOfDayUtc(endDate));
        long total = mapper.countGroups(filter);
        List<String> keys = mapper.pageGroupKeys(filter, page.pageSize(), (page.page() - 1) * page.pageSize());
        if (keys.isEmpty()) {
            return new PageResult<>(List.of(), total, page.page(), page.pageSize());
        }
        Map<String, List<Feedback>> byKey = new LinkedHashMap<>();
        keys.forEach(k -> byKey.put(k, new ArrayList<>()));
        for (Feedback row : mapper.selectByGroupKeys(filter, keys)) {
            byKey.get(groupKey(row)).add(row);
        }
        List<Feedback> all = byKey.values().stream().flatMap(List::stream).toList();
        Map<Long, CourseRef> courseRefs = courses.find(all.stream().map(Feedback::getCourseId).collect(Collectors.toSet()));
        Map<Long, QuestionRef> questionRefs = questions.find(all.stream().map(Feedback::getPracticeQuestionId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, String> nicknames = students.nicknames(all.stream().map(Feedback::getStudentId).collect(Collectors.toSet()));
        List<Group> groups = byKey.values().stream().filter(rows -> !rows.isEmpty()).map(rows -> {
            Feedback latest = rows.get(0);
            CourseRef course = courseRefs.get(latest.getCourseId());
            QuestionRef question = latest.getPracticeQuestionId() == null ? null : questionRefs.get(latest.getPracticeQuestionId());
            return new Group(rows, course == null ? null : course.title(), question == null ? null : question.stem(),
                    nicknames.get(latest.getStudentId()));
        }).toList();
        return new PageResult<>(groups, total, page.page(), page.pageSize());
    }

    /**
     * 处理反馈：全部须为待处理。每位提交者对同一对象只收到一条站内信（需求 6.6）。
     */
    @Transactional
    public int handle(CurrentAdmin operator, List<Long> feedbackIds, String status, String note) {
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(feedbackIds));
        List<Feedback> rows = mapper.selectByIds(ids);
        if (rows.size() != ids.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "反馈不存在，请刷新后重试");
        }
        if (rows.stream().anyMatch(r -> !PENDING.equals(r.getStatus()))) {
            throw new BusinessException(ErrorCode.FEEDBACK_ALREADY_HANDLED, "反馈已被处理，请刷新后重试");
        }
        String trimmed = note.strip();
        LocalDateTime now = UtcTime.now();
        int updated = mapper.update(null, new LambdaUpdateWrapper<Feedback>()
                .in(Feedback::getId, ids).eq(Feedback::getStatus, PENDING)
                .set(Feedback::getStatus, status)
                .set(Feedback::getHandlingNote, trimmed)
                .set(Feedback::getHandledBySysUserId, operator.id())
                .set(Feedback::getHandledAt, now));
        if (updated != ids.size()) {
            // 并发时其他人已处理了其中一部分，整体回滚
            throw new BusinessException(ErrorCode.FEEDBACK_ALREADY_HANDLED, "反馈已被处理，请刷新后重试");
        }

        Map<Long, CourseRef> courseRefs = courses.find(rows.stream().map(Feedback::getCourseId).collect(Collectors.toSet()));
        Map<Long, QuestionRef> questionRefs = questions.find(rows.stream().map(Feedback::getPracticeQuestionId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        String result = "corrected".equals(status) ? "已修正" : "已驳回";
        Set<String> sent = new LinkedHashSet<>();
        for (Feedback row : rows) {
            if (!sent.add(row.getStudentId() + "|" + objectKey(row))) {
                continue;
            }
            String object = objectLabel(row, courseRefs, questionRefs);
            messages.send(row.getStudentId(), StudentMessageSender.FEEDBACK_RESULT, "纠错与反馈处理结果",
                    "你提交的" + typeLabel(row) + "（" + object + "）已处理，结果：" + result + "。处理说明：" + trimmed,
                    row.getId());
        }
        Feedback first = rows.get(0);
        recorder.record(new OperationLogCommand(operator.id(), operator.name(), operator.role(), AuditModules.FEEDBACK,
                "处理反馈", "feedback", ids.size() == 1 ? first.getId() : null,
                truncate(typeLabel(first) + "：" + objectLabel(first, courseRefs, questionRefs), 120) + "，" + result
                        + "（" + ids.size() + " 条，通知 " + sent.size() + " 位学员）",
                ClientIps.current()));
        return updated;
    }

    /* ---------------- 内部 ---------------- */

    private static String groupKey(Feedback row) {
        return PENDING.equals(row.getStatus()) ? objectKey(row) : "h:" + row.getId();
    }

    private static String objectKey(Feedback row) {
        return row.getFeedbackType() + ":"
                + (row.getPracticeQuestionId() != null ? row.getPracticeQuestionId() : row.getCourseId());
    }

    private static String typeLabel(Feedback row) {
        return QUESTION_CORRECTION.equals(row.getFeedbackType()) ? "题目纠错" : "视频失效反馈";
    }

    private static String objectLabel(Feedback row, Map<Long, CourseRef> courses, Map<Long, QuestionRef> questions) {
        if (QUESTION_CORRECTION.equals(row.getFeedbackType())) {
            QuestionRef question = questions.get(row.getPracticeQuestionId());
            return "题目：" + (question == null ? "已删除的题目" : truncate(question.stem(), 30));
        }
        CourseRef course = courses.get(row.getCourseId());
        return "课程：" + (course == null ? "已删除的课程" : course.title());
    }

    private static String truncate(String text, int max) {
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() > max ? flat.substring(0, max) + "…" : flat;
    }
}
