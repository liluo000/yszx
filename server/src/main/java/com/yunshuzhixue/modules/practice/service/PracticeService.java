package com.yunshuzhixue.modules.practice.service;

import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.time.BeijingDates;
import com.yunshuzhixue.modules.content.api.CourseDirectory;
import com.yunshuzhixue.modules.content.api.CourseDirectory.CourseRef;
import com.yunshuzhixue.modules.practice.api.AnswerCompletedEvent;
import com.yunshuzhixue.modules.question.api.PracticeQuestionDirectory;
import com.yunshuzhixue.modules.question.api.PracticeQuestionDirectory.QuestionDetail;
import com.yunshuzhixue.modules.quota.api.AiQuotaApi;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 刷题与考试（M09，S-PRC-01~05）：本批只实现顺序练习闭环。
 *
 * <p>客观题自动判分；简答题因 AI 能力（M14）尚未接入，按需求 5.4.1 的「次数用完转自评」降级为自评。
 * 作答在同一事务内发布 {@link AnswerCompletedEvent}，由 M10 收录/移出错题。</p>
 */
@Service
public class PracticeService {

    private static final String SEQUENTIAL = "sequential";
    private static final String IN_PROGRESS = "in_progress";
    private static final String COMPLETED = "completed";
    private static final String ON_SHELF = "on_shelf";

    private final JdbcTemplate jdbc;
    private final PracticeQuestionDirectory questions;
    private final CourseDirectory courses;
    private final AiQuotaApi quota;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;

    public PracticeService(JdbcTemplate jdbc, PracticeQuestionDirectory questions, CourseDirectory courses,
                           AiQuotaApi quota, ApplicationEventPublisher events, JsonMapper json) {
        this.jdbc = jdbc;
        this.questions = questions;
        this.courses = courses;
        this.quota = quota;
        this.events = events;
        this.json = json;
    }

    /** S-PRC-01：开始或继续课程顺序练习 */
    @Transactional
    public PracticeSessionView start(long studentId, StartRequest request) {
        if (!SEQUENTIAL.equals(request.practiceMode())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "本批只支持按课程顺序练习");
        }
        Long courseId = request.courseId();
        if (courseId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择课程");
        }
        CourseRef course = courses.find(List.of(courseId)).get(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!ON_SHELF.equals(course.status())) {
            throw new BusinessException(ErrorCode.CONTENT_OFF_SHELF);
        }
        List<Long> ids = questions.questionIdsOfCourse(courseId);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_QUESTIONS);
        }

        List<Long> active = jdbc.queryForList("SELECT id FROM practice_session WHERE student_id = ?"
                + " AND practice_mode = 'sequential' AND course_id = ? AND status = 'in_progress'"
                + " ORDER BY id DESC LIMIT 1", Long.class, studentId, courseId);
        long sessionId;
        if (!active.isEmpty() && !Boolean.TRUE.equals(request.restart())) {
            sessionId = active.get(0);
        } else {
            if (!active.isEmpty()) {
                jdbc.update("UPDATE practice_session SET status = 'unfinished', finished_at = NOW(3) WHERE id = ?",
                        active.get(0));
            }
            jdbc.update("INSERT INTO practice_session"
                    + " (student_id, practice_mode, course_id, course_title, status, question_count, started_at)"
                    + " VALUES (?, 'sequential', ?, ?, 'in_progress', ?, NOW(3))",
                    studentId, courseId, course.title(), ids.size());
            sessionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        }
        return view(studentId, sessionId);
    }

    /** S-PRC-02：练习进度与下一题 */
    public PracticeSessionView view(long studentId, long sessionId) {
        Map<String, Object> row = sessionRow(studentId, sessionId);
        Long courseId = number(row.get("course_id"));
        List<Long> ids = courseId == null ? List.of() : questions.questionIdsOfCourse(courseId);
        return buildView(studentId, row, ids);
    }

    /** S-PRC-03：提交一题答案，立即返回判分结果 */
    @Transactional
    public SubmitResult submit(long studentId, long sessionId, SubmitRequest request) {
        Map<String, Object> session = sessionRow(studentId, sessionId);
        if (!IN_PROGRESS.equals(session.get("status"))) {
            throw new BusinessException(ErrorCode.SESSION_NOT_IN_PROGRESS);
        }
        Long courseId = number(session.get("course_id"));
        Long questionId = request.practiceQuestionId();
        List<Long> ids = courseId == null ? List.of() : questions.questionIdsOfCourse(courseId);
        if (questionId == null || !ids.contains(questionId)) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_IN_SESSION);
        }
        Set<Long> answered = new HashSet<>(jdbc.queryForList("SELECT practice_question_id FROM practice_answer"
                + " WHERE practice_session_id = ?", Long.class, sessionId));
        if (answered.contains(questionId)) {
            throw new BusinessException(ErrorCode.QUESTION_ALREADY_ANSWERED);
        }
        QuestionDetail detail = questions.details(List.of(questionId)).get(questionId);
        if (detail == null || !ON_SHELF.equals(detail.status())) {
            throw new BusinessException(ErrorCode.CONTENT_OFF_SHELF);
        }

        boolean shortAnswer = "short_answer".equals(detail.questionType());
        boolean correct;
        String judgeMethod;
        Integer aiScore = null;
        String aiComment = null;
        if (shortAnswer) {
            // AI 评分能力尚未接入：按需求 5.4.1 的降级规则由学员自评
            String assessment = request.selfAssessment();
            if (assessment == null) {
                return new SubmitResult(true, detail.referenceAnswer(), null, null, IN_PROGRESS,
                        quota.current(studentId).scoringRemaining());
            }
            if (!"can".equals(assessment) && !"cannot".equals(assessment)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "自评取值不正确");
            }
            correct = "can".equals(assessment);
            judgeMethod = "self";
        } else {
            correct = judge(detail.correctAnswer(), request.studentAnswer());
            judgeMethod = "auto";
        }

        String snapshot = json.writeValueAsString(snapshotOf(detail));
        String answerJson = json.writeValueAsString(request.studentAnswer());
        jdbc.update("INSERT INTO practice_answer (practice_session_id, student_id, practice_question_id,"
                        + " course_id, practice_mode, question_type, question_snapshot, student_answer,"
                        + " judge_method, is_correct, answered_at) VALUES (?, ?, ?, ?, 'sequential', ?, ?, ?, ?, ?, NOW(3))",
                sessionId, studentId, questionId, courseId, detail.questionType(), snapshot, answerJson,
                judgeMethod, correct ? 1 : 0);

        jdbc.update("UPDATE practice_session SET answered_count = answered_count + 1,"
                + " correct_count = correct_count + ? WHERE id = ?", correct ? 1 : 0, sessionId);
        jdbc.update("INSERT INTO sequential_question_result"
                + " (student_id, practice_question_id, course_id, is_correct, answered_at) VALUES (?, ?, ?, ?, NOW(3))"
                + " ON DUPLICATE KEY UPDATE is_correct = VALUES(is_correct), answered_at = VALUES(answered_at)",
                studentId, questionId, courseId, correct ? 1 : 0);

        answered.add(questionId);
        boolean finished = answered.containsAll(ids);
        if (finished) {
            jdbc.update("UPDATE practice_session SET status = 'completed', finished_at = NOW(3) WHERE id = ?",
                    sessionId);
        }
        events.publishEvent(new AnswerCompletedEvent(studentId, questionId, courseId, detail.questionType(),
                request.studentAnswer(), correct, SEQUENTIAL));

        AnswerResult result = new AnswerResult(correct, judgeMethod, aiScore, aiComment,
                shortAnswer ? null : detail.correctAnswer(), detail.referenceAnswer(), detail.analysis());
        QuestionForAnswer next = finished ? null : nextQuestion(ids, answered);
        return new SubmitResult(false, null, result, next, finished ? COMPLETED : IN_PROGRESS,
                quota.current(studentId).scoringRemaining());
    }

    /** S-PRC-05：本场作答详情，题目来自作答快照 */
    public AnswerList answers(long studentId, long sessionId) {
        sessionRow(studentId, sessionId);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT question_snapshot, student_answer, is_correct,"
                + " judge_method, ai_score, ai_comment, answered_at FROM practice_answer"
                + " WHERE practice_session_id = ? ORDER BY id", sessionId);
        List<AnswerDetail> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            items.add(new AnswerDetail(parse(String.valueOf(row.get("question_snapshot"))),
                    parse(String.valueOf(row.get("student_answer"))), boolOf(row.get("is_correct")),
                    String.valueOf(row.get("judge_method")),
                    row.get("ai_score") == null ? null : ((Number) row.get("ai_score")).intValue(),
                    (String) row.get("ai_comment"),
                    toInstant(row.get("answered_at"))));
        }
        return new AnswerList(items);
    }

    /** S-PRC-04：刷题记录（错题重做不进入学员刷题记录） */
    public PageResult<PracticeRecord> records(long studentId, PageQuery page, String recordType,
                                              Long routeId, LocalDate startDate, LocalDate endDate) {
        StringBuilder where = new StringBuilder(" WHERE student_id = ? AND practice_mode <> 'wrong_redo'");
        List<Object> args = new ArrayList<>();
        args.add(studentId);
        if (recordType != null && !recordType.isBlank()) {
            where.append(" AND practice_mode = ?");
            args.add(recordType);
        }
        if (routeId != null) {
            where.append(" AND learning_route_id = ?");
            args.add(routeId);
        }
        if (startDate != null) {
            where.append(" AND started_at >= ?");
            args.add(java.time.LocalDateTime.from(BeijingDates.startOfDayUtc(startDate)));
        }
        if (endDate != null) {
            where.append(" AND started_at < ?");
            args.add(java.time.LocalDateTime.from(BeijingDates.endOfDayUtc(endDate)));
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM practice_session" + where, Long.class,
                args.toArray());
        String sql = "SELECT id, practice_mode, course_id, course_title, route_name, stage_name, status,"
                + " question_count, answered_count, correct_count, started_at, finished_at"
                + " FROM practice_session" + where + " ORDER BY started_at DESC, id DESC " + page.limitClause();
        List<PracticeRecord> items = jdbc.queryForList(sql, args.toArray()).stream()
                .map(row -> new PracticeRecord(number(row.get("id")), String.valueOf(row.get("practice_mode")),
                        number(row.get("course_id")), (String) row.get("course_title"),
                        (String) row.get("route_name"), (String) row.get("stage_name"),
                        String.valueOf(row.get("status")), intOf(row.get("question_count")),
                        intOf(row.get("answered_count")), intOf(row.get("correct_count")),
                        toInstant(row.get("started_at")), toInstant(row.get("finished_at"))))
                .toList();
        return new PageResult<>(items, total, page.page(), page.pageSize());
    }

    private Map<String, Object> sessionRow(long studentId, long sessionId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM practice_session"
                + " WHERE id = ? AND student_id = ?", sessionId, studentId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return rows.get(0);
    }

    private PracticeSessionView buildView(long studentId, Map<String, Object> row, List<Long> ids) {
        Set<Long> answered = new HashSet<>(jdbc.queryForList("SELECT practice_question_id FROM practice_answer"
                + " WHERE practice_session_id = ?", Long.class, number(row.get("id"))));
        QuestionForAnswer next = nextQuestion(ids, answered);
        return new PracticeSessionView(number(row.get("id")), String.valueOf(row.get("practice_mode")),
                String.valueOf(row.get("status")), number(row.get("course_id")), number(row.get("route_stage_id")),
                intOf(row.get("question_count")), intOf(row.get("answered_count")), intOf(row.get("correct_count")),
                next, quota.current(studentId).scoringRemaining());
    }

    private QuestionForAnswer nextQuestion(List<Long> ids, Set<Long> answered) {
        Long nextId = ids.stream().filter(id -> !answered.contains(id)).findFirst().orElse(null);
        if (nextId == null) {
            return null;
        }
        QuestionDetail detail = questions.details(List.of(nextId)).get(nextId);
        if (detail == null) {
            return null;
        }
        return new QuestionForAnswer(detail.id(), detail.courseId(), detail.questionType(), detail.stem(),
                detail.options());
    }

    private Map<String, Object> snapshotOf(QuestionDetail detail) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", detail.id());
        snapshot.put("course_id", detail.courseId());
        snapshot.put("question_type", detail.questionType());
        snapshot.put("stem", detail.stem());
        snapshot.put("options", detail.options());
        snapshot.put("correct_answer", detail.correctAnswer());
        snapshot.put("reference_answer", detail.referenceAnswer());
        snapshot.put("analysis", detail.analysis());
        return snapshot;
    }

    /** 客观题：选项完全一致才算对（多选少选、错选都算错） */
    private static boolean judge(Object correctAnswer, Object studentAnswer) {
        Set<String> expected = normalized(correctAnswer);
        return !expected.isEmpty() && expected.equals(normalized(studentAnswer));
    }

    private static Set<String> normalized(Object value) {
        Set<String> out = new HashSet<>();
        if (value == null) {
            return out;
        }
        if (value instanceof JsonNode node) {
            if (node.isArray()) {
                node.forEach(child -> out.add(child.asText()));
            } else {
                out.add(node.asText());
            }
        } else if (value instanceof Iterable<?> items) {
            items.forEach(item -> out.add(String.valueOf(item)));
        } else {
            out.add(String.valueOf(value));
        }
        return out;
    }

    private Object parse(String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw)) {
            return null;
        }
        try {
            return json.readTree(raw);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Long number(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private static int intOf(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    /** MySQL TINYINT(1) 经 JDBC 映射为 Boolean，其余驱动可能返回 Number */
    private static boolean boolOf(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value instanceof Number n && n.intValue() == 1;
    }

    private static Instant toInstant(Object value) {
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toInstant(java.time.ZoneOffset.UTC);
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.toInstant(java.time.ZoneOffset.UTC);
        }
        return null;
    }

    public record StartRequest(String practiceMode, Long courseId, Boolean restart, Long learningRouteId,
                               Long routeStageId, List<Long> wrongQuestionIds) {
    }

    public record SubmitRequest(Long practiceQuestionId, Object studentAnswer, String selfAssessment) {
    }

    /** 作答时的题目，不含答案 */
    public record QuestionForAnswer(Long id, Long courseId, String questionType, String stem, Object options) {
    }

    public record AnswerResult(boolean isCorrect, String judgeMethod, Integer aiScore, String aiComment,
                               Object correctAnswer, String referenceAnswer, String analysis) {
    }

    public record PracticeSessionView(Long id, String practiceMode, String status, Long courseId,
                                      Long routeStageId, int questionCount, int answeredCount, int correctCount,
                                      QuestionForAnswer nextQuestion, int remainingScoringQuota) {
    }

    /** needSelfAssessment=true 时本题尚未入库，需学员自评后再次提交 */
    public record SubmitResult(boolean needSelfAssessment, String referenceAnswer, AnswerResult result,
                               QuestionForAnswer nextQuestion, String sessionStatus, int remainingScoringQuota) {
    }

    public record AnswerDetail(Object questionSnapshot, Object studentAnswer, boolean isCorrect,
                               String judgeMethod, Integer aiScore, String aiComment, Instant answeredAt) {
    }

    public record AnswerList(List<AnswerDetail> items) {
    }

    public record PracticeRecord(Long id, String practiceMode, Long courseId, String courseTitle,
                                 String routeName, String stageName, String status, int questionCount,
                                 int answeredCount, int correctCount, Instant startedAt, Instant finishedAt) {
    }
}
