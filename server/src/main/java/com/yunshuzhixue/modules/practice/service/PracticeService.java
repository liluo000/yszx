package com.yunshuzhixue.modules.practice.service;

import com.yunshuzhixue.common.api.PageQuery;
import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.time.BeijingDates;
import com.yunshuzhixue.modules.content.api.CourseDirectory;
import com.yunshuzhixue.modules.content.api.RouteDirectory;
import com.yunshuzhixue.modules.content.api.CourseDirectory.CourseRef;
import com.yunshuzhixue.modules.practice.api.AnswerCompletedEvent;
import com.yunshuzhixue.modules.practice.api.WrongQuestionDirectory;
import com.yunshuzhixue.modules.question.api.PracticeQuestionDirectory;
import com.yunshuzhixue.modules.question.api.PracticeQuestionDirectory.QuestionDetail;
import com.yunshuzhixue.modules.quota.api.AiQuotaApi;
import com.yunshuzhixue.modules.sysconfig.api.SysConfigReader;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private static final String WRONG_REDO = "wrong_redo";
    private static final String RANDOM = "random";
    private static final String IN_PROGRESS = "in_progress";
    private static final String COMPLETED = "completed";
    private static final String ON_SHELF = "on_shelf";

    private final JdbcTemplate jdbc;
    private final PracticeQuestionDirectory questions;
    private final CourseDirectory courses;
    private final RouteDirectory routes;
    private final SysConfigReader configs;
    private final AiQuotaApi quota;
    private final WrongQuestionDirectory wrongQuestions;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;

    public PracticeService(JdbcTemplate jdbc, PracticeQuestionDirectory questions, CourseDirectory courses,
                           RouteDirectory routes, SysConfigReader configs, AiQuotaApi quota,
                           WrongQuestionDirectory wrongQuestions,
                           ApplicationEventPublisher events, JsonMapper json) {
        this.jdbc = jdbc;
        this.questions = questions;
        this.courses = courses;
        this.routes = routes;
        this.configs = configs;
        this.quota = quota;
        this.wrongQuestions = wrongQuestions;
        this.events = events;
        this.json = json;
    }

    /** S-PRC-01：开始或继续练习（sequential 顺序练习、wrong_redo 错题重做） */
    @Transactional
    public PracticeSessionView start(long studentId, StartRequest request) {
        if (WRONG_REDO.equals(request.practiceMode())) {
            return startWrongRedo(studentId, request);
        }
        if (RANDOM.equals(request.practiceMode())) {
            return startRandom(studentId, request);
        }
        if (!SEQUENTIAL.equals(request.practiceMode())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "练习模式暂不支持");
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
        return buildView(studentId, row, sessionQuestionIds(row));
    }

    /** 顺序练习按课程取题；随机抽题 / 错题重做按 planned_question_ids 取题 */
    private List<Long> sessionQuestionIds(Map<String, Object> row) {
        String mode = String.valueOf(row.get("practice_mode"));
        if (RANDOM.equals(mode) || WRONG_REDO.equals(mode)) {
            return plannedIds(row.get("planned_question_ids"));
        }
        Long courseId = number(row.get("course_id"));
        return courseId == null ? List.of() : questions.questionIdsOfCourse(courseId);
    }

    /** S-PRC-01 random：按阶段抽题，题量不足按实际抽取，低于最低题量拒绝 */
    private PracticeSessionView startRandom(long studentId, StartRequest request) {
        Long routeId = request.learningRouteId();
        Long stageId = request.routeStageId();
        if (routeId == null || stageId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择要抽题的路线和阶段");
        }
        RouteDirectory.RouteDetail detail = routes.routeDetail(routeId);
        if (detail == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学习路线不存在或已下架");
        }
        RouteDirectory.StageDetail stage = detail.stages().stream()
                .filter(one -> one.id() == stageId).findFirst().orElse(null);
        if (stage == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "阶段不属于该路线");
        }
        List<Long> pool = new ArrayList<>();
        for (RouteDirectory.CourseItem course : stage.courses()) {
            pool.addAll(questions.questionIdsOfCourse(course.id()));
        }
        int wanted = configs.getInt("practice.random_question_count");
        int minimum = configs.getInt("practice.random_min_question_count");
        if (pool.size() < minimum) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_QUESTIONS,
                    "本阶段题目不足 " + minimum + " 道，暂时无法随机抽题");
        }
        Collections.shuffle(pool);
        List<Long> ids = pool.subList(0, Math.min(wanted, pool.size())).stream().distinct().toList();

        jdbc.update("UPDATE practice_session SET status = 'unfinished', finished_at = NOW(3)"
                + " WHERE student_id = ? AND practice_mode = 'random' AND status = 'in_progress'", studentId);
        jdbc.update("INSERT INTO practice_session"
                        + " (student_id, practice_mode, learning_route_id, route_stage_id, route_name, stage_name,"
                        + " planned_question_ids, status, question_count, started_at)"
                        + " VALUES (?, 'random', ?, ?, ?, ?, ?, 'in_progress', ?, NOW(3))",
                studentId, routeId, stageId, detail.name(), stage.name(),
                json.writeValueAsString(ids), ids.size());
        long sessionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return buildView(studentId, sessionRow(studentId, sessionId), ids);
    }

    /** S-PRC-01 wrong_redo：错题记录 ID → 题目 ID，已下架的题不能重做（需求 5.5） */
    private PracticeSessionView startWrongRedo(long studentId, StartRequest request) {
        List<Long> recordIds = request.wrongQuestionIds();
        if (recordIds == null || recordIds.isEmpty() || recordIds.size() > 200) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择 1~200 道要重做的错题");
        }
        Map<Long, Long> mapped = wrongQuestions.questionIdsOf(studentId, recordIds);
        if (mapped.size() < new HashSet<>(recordIds).size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部分错题记录不存在或已移出");
        }
        // 保持传入顺序，并过滤已下架的题
        List<Long> ids = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Map<Long, QuestionDetail> details = questions.details(new ArrayList<>(new LinkedHashSet<>(mapped.values())));
        for (Long recordId : recordIds) {
            Long questionId = mapped.get(recordId);
            if (questionId == null || !seen.add(questionId)) {
                continue;
            }
            QuestionDetail detail = details.get(questionId);
            if (detail != null && ON_SHELF.equals(detail.status())) {
                ids.add(questionId);
            }
        }
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_QUESTIONS, "错题对应的题目已下架，不能重做");
        }
        List<Long> active = jdbc.queryForList("SELECT id FROM practice_session WHERE student_id = ?"
                + " AND practice_mode = 'wrong_redo' AND status = 'in_progress'", Long.class, studentId);
        for (Long oldId : active) {
            jdbc.update("UPDATE practice_session SET status = 'unfinished', finished_at = NOW(3) WHERE id = ?", oldId);
        }
        jdbc.update("INSERT INTO practice_session"
                        + " (student_id, practice_mode, planned_question_ids, status, question_count, started_at)"
                        + " VALUES (?, 'wrong_redo', ?, 'in_progress', ?, NOW(3))",
                studentId, json.writeValueAsString(ids), ids.size());
        long sessionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return buildView(studentId, sessionRow(studentId, sessionId), ids);
    }

    /** S-PRC-03：提交一题答案，立即返回判分结果 */
    @Transactional
    public SubmitResult submit(long studentId, long sessionId, SubmitRequest request) {
        Map<String, Object> session = sessionRow(studentId, sessionId);
        if (!IN_PROGRESS.equals(session.get("status"))) {
            throw new BusinessException(ErrorCode.SESSION_NOT_IN_PROGRESS);
        }
        String mode = String.valueOf(session.get("practice_mode"));
        Long courseId = number(session.get("course_id"));
        Long questionId = request.practiceQuestionId();
        List<Long> ids = RANDOM.equals(mode) || WRONG_REDO.equals(mode)
                ? plannedIds(session.get("planned_question_ids"))
                : courseId == null ? List.of() : questions.questionIdsOfCourse(courseId);
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
        // 错题重做轮次没有 course_id，落到作答行与事件里的是题目所属课程
        Long answerCourseId = courseId != null ? courseId : detail.courseId();
        jdbc.update("INSERT INTO practice_answer (practice_session_id, student_id, practice_question_id,"
                        + " course_id, practice_mode, question_type, question_snapshot, student_answer,"
                        + " judge_method, is_correct, answered_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(3))",
                sessionId, studentId, questionId, answerCourseId, mode, detail.questionType(), snapshot, answerJson,
                judgeMethod, correct ? 1 : 0);

        jdbc.update("UPDATE practice_session SET answered_count = answered_count + 1,"
                + " correct_count = correct_count + ? WHERE id = ?", correct ? 1 : 0, sessionId);
        if (SEQUENTIAL.equals(mode)) {
            jdbc.update("INSERT INTO sequential_question_result"
                + " (student_id, practice_question_id, course_id, is_correct, answered_at) VALUES (?, ?, ?, ?, NOW(3))"
                + " ON DUPLICATE KEY UPDATE is_correct = VALUES(is_correct), answered_at = VALUES(answered_at)",
                    studentId, questionId, courseId, correct ? 1 : 0);
        }

        answered.add(questionId);
        boolean finished = answered.containsAll(ids);
        if (finished) {
            jdbc.update("UPDATE practice_session SET status = 'completed', finished_at = NOW(3) WHERE id = ?",
                    sessionId);
        }
        events.publishEvent(new AnswerCompletedEvent(studentId, questionId, answerCourseId, detail.questionType(),
                request.studentAnswer(), correct, mode));

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

    /** 解析 planned_question_ids（随机抽题、错题重做的题目列表） */
    private List<Long> plannedIds(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank() || "null".equals(raw)) {
            return List.of();
        }
        try {
            JsonNode node = json.readTree(String.valueOf(raw));
            List<Long> ids = new ArrayList<>();
            node.forEach(child -> ids.add(child.asLong()));
            return ids;
        } catch (RuntimeException e) {
            return List.of();
        }
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
