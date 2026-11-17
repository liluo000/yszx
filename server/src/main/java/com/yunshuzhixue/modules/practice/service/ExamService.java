package com.yunshuzhixue.modules.practice.service;

import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.time.UtcTime;
import com.yunshuzhixue.modules.ai.api.ShortAnswerScorer;
import com.yunshuzhixue.modules.content.api.RouteDirectory;
import com.yunshuzhixue.modules.practice.api.AnswerCompletedEvent;
import com.yunshuzhixue.modules.question.api.PracticeQuestionDirectory.QuestionDetail;
import com.yunshuzhixue.modules.quota.api.AiQuotaApi;
import com.yunshuzhixue.modules.sysconfig.api.SysConfigReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.json.JsonMapper;

/**
 * 模拟考试（M09，S-EXM-01~08，需求 5.4.4 / 8.5）：组卷、限时、设备接管、逐题保存、判分与重评分。
 *
 * <p>简答题评分通过 M14 的 {@link ShortAnswerScorer} 接口调用；真实 AI 服务未接入前由本地占位实现提供分数。
 * 评分失败进入 scoring_pending，不扣次数，学员可重新评分。</p>
 */
@Service
public class ExamService {

    private static final String IN_PROGRESS = "in_progress";
    private static final String SCORING_PENDING = "scoring_pending";
    private static final String GRADED = "graded";
    private static final String ABANDONED = "abandoned";
    private static final List<String> TYPES = List.of("true_false", "single_choice",
            "multiple_choice", "short_answer");

    private final JdbcTemplate jdbc;
    private final RouteDirectory routes;
    private final com.yunshuzhixue.modules.question.api.PracticeQuestionDirectory questions;
    private final SysConfigReader configs;
    private final AiQuotaApi quota;
    private final ShortAnswerScorer scorer;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;

    public ExamService(JdbcTemplate jdbc, RouteDirectory routes,
                       com.yunshuzhixue.modules.question.api.PracticeQuestionDirectory questions,
                       SysConfigReader configs, AiQuotaApi quota, ShortAnswerScorer scorer,
                       ApplicationEventPublisher events, JsonMapper json) {
        this.jdbc = jdbc;
        this.routes = routes;
        this.questions = questions;
        this.configs = configs;
        this.quota = quota;
        this.scorer = scorer;
        this.events = events;
        this.json = json;
    }

    /** S-EXM-01：按组卷规则抽题并固定快照；含简答题时校验并预留 1 次评分次数 */
    @Transactional
    public ExamView start(long studentId, StartExamRequest request) {
        Long routeId = request.learningRouteId();
        Long stageId = request.routeStageId();
        if (routeId == null || stageId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择要开考的路线和阶段");
        }
        List<Long> active = jdbc.queryForList("SELECT id FROM exam_session"
                + " WHERE student_id = ? AND status IN ('in_progress', 'submitted', 'scoring_pending')",
                Long.class, studentId);
        if (!active.isEmpty()) {
            throw new BusinessException(ErrorCode.SESSION_IN_PROGRESS,
                    "已有进行中的考试，请先继续或结束该场考试", List.of(active.get(0)));
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
        int minimum = configs.getInt("exam.min_question_count");
        if (pool.size() < minimum) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_QUESTIONS,
                    "本阶段题目不足 " + minimum + " 道，暂时无法开考");
        }
        Map<Long, QuestionDetail> byId = questions.details(new HashSet<>(pool));
        JsonNode rule = configs.get("exam.paper_rule");
        List<PaperItem> paper = new ArrayList<>();
        BigDecimal fullScore = BigDecimal.ZERO;
        int seq = 1;
        for (String type : TYPES) {
            JsonNode typeRule = rule == null ? null : rule.get(type);
            int count = typeRule == null ? 0 : typeRule.get("count").intValue();
            BigDecimal score = typeRule == null ? BigDecimal.ZERO
                    : BigDecimal.valueOf(typeRule.get("score").intValue());
            List<QuestionDetail> candidates = new ArrayList<>();
            for (Long id : pool) {
                QuestionDetail one = byId.get(id);
                if (one != null && type.equals(one.questionType()) && "on_shelf".equals(one.status())) {
                    candidates.add(one);
                }
            }
            Collections.shuffle(candidates);
            for (int i = 0; i < Math.min(count, candidates.size()); i++) {
                paper.add(new PaperItem(seq++, candidates.get(i), score));
                fullScore = fullScore.add(score);
            }
        }
        if (paper.isEmpty()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_QUESTIONS, "题目建设中，题量不足");
        }
        boolean hasShort = paper.stream().anyMatch(one -> "short_answer".equals(one.detail().questionType()));
        if (hasShort && quota.current(studentId).scoringRemaining() <= 0) {
            throw new BusinessException(ErrorCode.AI_QUOTA_EXHAUSTED);
        }

        int duration = configs.getInt("exam.duration_minutes");
        LocalDateTime started = UtcTime.now();
        LocalDateTime deadline = started.plusMinutes(duration);
        String ruleJson = rule == null ? "{}" : rule.toString();
        jdbc.update("INSERT INTO exam_session (student_id, learning_route_id, route_stage_id, route_name,"
                        + " stage_name, status, paper_rule, duration_minutes, has_short_answer,"
                        + " scoring_quota_status, quota_date, full_score, started_at, deadline_at)"
                        + " VALUES (?, ?, ?, ?, ?, 'in_progress', ?, ?, ?, ?, ?, ?, ?, ?)",
                studentId, routeId, stageId, detail.name(), stage.name(), ruleJson, duration,
                hasShort ? 1 : 0, hasShort ? "reserved" : "none", java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")),
                fullScore, started, deadline);
        long examId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        for (PaperItem item : paper) {
            jdbc.update("INSERT INTO exam_session_question (exam_session_id, seq_no, practice_question_id,"
                            + " course_id, question_type, question_snapshot, question_score, judge_status)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, 'pending')",
                    examId, item.seqNo(), item.detail().id(), item.detail().courseId(),
                    item.detail().questionType(), json.writeValueAsString(snapshotOf(item.detail())),
                    item.score());
        }
        if (hasShort && !quota.reserveScoring(studentId, examId)) {
            throw new BusinessException(ErrorCode.AI_QUOTA_EXHAUSTED);
        }
        return view(studentId, examId);
    }

    /** S-EXM-02：作答中的考试（含待重新评分的场次） */
    public InProgressView inProgress(long studentId) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM exam_session WHERE student_id = ?"
                + " AND status IN ('in_progress', 'scoring_pending') ORDER BY id DESC LIMIT 1",
                Long.class, studentId);
        return new InProgressView(ids.isEmpty() ? null : view(studentId, ids.get(0)));
    }

    /** S-EXM-03 / S-EXM-01 / S-EXM-06 / S-EXM-08 共用的场次视图 */
    public ExamView view(long studentId, long examId) {
        Map<String, Object> row = examRow(studentId, examId);
        String status = String.valueOf(row.get("status"));
        // 已放弃的场次不出成绩，答案与判分字段都不返回
        boolean finished = GRADED.equals(status) || SCORING_PENDING.equals(status);
        List<Map<String, Object>> qs = jdbc.queryForList("SELECT * FROM exam_session_question"
                + " WHERE exam_session_id = ? ORDER BY seq_no", examId);
        List<ExamQuestionView> items = new ArrayList<>();
        for (Map<String, Object> q : qs) {
            JsonNode snapshot = parse(String.valueOf(q.get("question_snapshot")));
            JsonNode answer = parse(q.get("student_answer") == null ? null : String.valueOf(q.get("student_answer")));
            items.add(new ExamQuestionView(intOf(q.get("seq_no")), questionOf(snapshot),
                    decimal(q.get("question_score")), answer,
                    finished ? String.valueOf(q.get("judge_status")) : null,
                    finished ? boolOrNull(q.get("is_correct")) : null,
                    finished ? decimal(q.get("earned_score")) : null,
                    finished && q.get("ai_score") != null ? intOf(q.get("ai_score")) : null,
                    finished ? (String) q.get("ai_comment") : null,
                    finished ? snapshot.get("correct_answer") : null,
                    finished && snapshot.hasNonNull("reference_answer") ? snapshot.get("reference_answer").asText() : null,
                    finished && snapshot.hasNonNull("analysis") ? snapshot.get("analysis").asText() : null));
        }
        BigDecimal score = row.get("score") == null ? null : decimal(row.get("score"));
        List<TypeScore> typeScores = GRADED.equals(status) ? typeScores(examId) : null;
        return new ExamView(examId, String.valueOf(row.get("route_name")), String.valueOf(row.get("stage_name")),
                status, intOf(row.get("duration_minutes")), toInstant(row.get("started_at")),
                toInstant(row.get("deadline_at")), Instant.now(), boolOf(row.get("has_short_answer")),
                score, typeScores, items);
    }

    /** S-EXM-04：新设备接管，旧凭证立即失效 */
    @Transactional
    public DeviceClaim claimDevice(long studentId, long examId) {
        Map<String, Object> row = examRow(studentId, examId);
        if (!IN_PROGRESS.equals(String.valueOf(row.get("status")))) {
            throw new BusinessException(ErrorCode.SESSION_NOT_IN_PROGRESS);
        }
        String claim = UUID.randomUUID().toString();
        jdbc.update("UPDATE exam_session SET device_claim = ? WHERE id = ?", claim, examId);
        return new DeviceClaim(claim);
    }

    /** S-EXM-05：保存一题答案（可传 null 清空），仅截止前且持有设备凭证时可保存 */
    @Transactional
    public AnswerSaved saveAnswer(long studentId, long examId, int seqNo, Object studentAnswer, String claim) {
        Map<String, Object> row = examRow(studentId, examId);
        requireInProgress(row);
        requireClaim(row, claim);
        if (isExpired(row)) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM exam_session_question"
                + " WHERE exam_session_id = ? AND seq_no = ?", Integer.class, examId, seqNo);
        if (exists == null || exists == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "题号不存在");
        }
        String value = studentAnswer == null ? null : json.writeValueAsString(studentAnswer);
        jdbc.update("UPDATE exam_session_question SET student_answer = ?, answered_at = NOW(3)"
                + " WHERE exam_session_id = ? AND seq_no = ?", value, examId, seqNo);
        return new AnswerSaved(Instant.now());
    }

    /** S-EXM-06：交卷判分；截止时间之后仍可调用，按已保存答案判分 */
    @Transactional
    public ExamView submit(long studentId, long examId, String claim) {
        Map<String, Object> row = examRow(studentId, examId);
        requireInProgress(row);
        requireClaim(row, claim);
        return grade(studentId, examId, row, true);
    }

    /** 定时任务：到时自动交卷（无需设备凭证） */
    @Transactional
    public void submitByTimeout(long studentId, long examId) {
        Map<String, Object> row = examRow(studentId, examId);
        if (!IN_PROGRESS.equals(String.valueOf(row.get("status")))) {
            return;
        }
        grade(studentId, examId, row, true);
    }

    /** S-EXM-07：放弃考试，释放预留次数且不出成绩 */
    @Transactional
    public void abandon(long studentId, long examId, String claim) {
        Map<String, Object> row = examRow(studentId, examId);
        requireInProgress(row);
        requireClaim(row, claim);
        if ("reserved".equals(String.valueOf(row.get("scoring_quota_status")))) {
            quota.releaseScoring(studentId, examId);
            jdbc.update("UPDATE exam_session SET scoring_quota_status = 'released' WHERE id = ?", examId);
        }
        jdbc.update("UPDATE exam_session SET status = 'abandoned', submitted_at = NOW(3) WHERE id = ?", examId);
    }

    /** S-EXM-08：仅 scoring_pending 可重新评分简答题 */
    @Transactional
    public ExamView rescore(long studentId, long examId) {
        Map<String, Object> row = examRow(studentId, examId);
        if (!SCORING_PENDING.equals(String.valueOf(row.get("status")))) {
            throw new BusinessException(ErrorCode.SESSION_STATE_INVALID);
        }
        return grade(studentId, examId, row, false);
    }

    /** 判分：客观题自动判分；简答题调用 M14，失败则 scoring_pending 且不扣次数 */
    private ExamView grade(long studentId, long examId, Map<String, Object> row, boolean publishWrong) {
        jdbc.update("UPDATE exam_session SET status = 'submitted', submitted_at = COALESCE(submitted_at, NOW(3))"
                + " WHERE id = ?", examId);
        List<Map<String, Object>> qs = jdbc.queryForList("SELECT * FROM exam_session_question"
                + " WHERE exam_session_id = ? ORDER BY seq_no", examId);
        int passLine = configs.getInt("learning.short_answer_pass_score");
        boolean failed = false;
        for (Map<String, Object> q : qs) {
            JsonNode snapshot = parse(String.valueOf(q.get("question_snapshot")));
            String type = String.valueOf(q.get("question_type"));
            JsonNode answerNode = parse(q.get("student_answer") == null ? null
                    : String.valueOf(q.get("student_answer")));
            String studentText = textOf(answerNode);
            BigDecimal questionScore = decimal(q.get("question_score"));
            BigDecimal earned = BigDecimal.ZERO;
            boolean correct = false;
            Integer aiScore = null;
            String aiComment = null;
            if ("short_answer".equals(type)) {
                // 重评分时只对上次失败的题重新请求 AI，避免重复扣次数语义
                if (!publishWrong && !"failed".equals(String.valueOf(q.get("judge_status")))) {
                    continue;
                }
                try {
                    ShortAnswerScorer.Score scored = scorer.score(
                            snapshot.path("stem").asText(""),
                            snapshot.path("reference_answer").asText(""),
                            snapshot.path("analysis").asText(""), studentText);
                    aiScore = scored.score();
                    aiComment = scored.comment();
                    correct = aiScore >= passLine;
                    earned = questionScore.multiply(BigDecimal.valueOf(aiScore))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                } catch (ShortAnswerScorer.UnavailableException e) {
                    failed = true;
                    jdbc.update("UPDATE exam_session_question SET judge_status = 'failed' WHERE id = ?",
                            q.get("id"));
                    continue;
                }
            } else {
                correct = judge(snapshot.get("correct_answer"), answerNode);
                earned = correct ? questionScore : BigDecimal.ZERO;
            }
            jdbc.update("UPDATE exam_session_question SET judge_status = 'judged', is_correct = ?,"
                            + " earned_score = ?, ai_score = ?, ai_comment = ? WHERE id = ?",
                    correct ? 1 : 0, earned, aiScore, aiComment, q.get("id"));
            if (!correct) {
                events.publishEvent(new AnswerCompletedEvent(studentId,
                        ((Number) q.get("practice_question_id")).longValue(),
                        ((Number) q.get("course_id")).longValue(), type,
                        answerNode == null ? null : json.readTree(String.valueOf(answerNode)), false, "exam"));
            }
        }
        if (failed) {
            jdbc.update("UPDATE exam_session SET status = 'scoring_pending' WHERE id = ?", examId);
            return view(studentId, examId);
        }
        BigDecimal fullScore = decimal(row.get("full_score"));
        BigDecimal earnedTotal = jdbc.queryForObject("SELECT COALESCE(SUM(earned_score), 0)"
                + " FROM exam_session_question WHERE exam_session_id = ?", BigDecimal.class, examId);
        BigDecimal score = fullScore.signum() == 0 ? BigDecimal.ZERO
                : earnedTotal.multiply(BigDecimal.valueOf(100)).divide(fullScore, 1, RoundingMode.HALF_UP);
        if ("reserved".equals(String.valueOf(row.get("scoring_quota_status")))) {
            if (quota.consumeReservedScoring(studentId, examId)) {
                jdbc.update("UPDATE exam_session SET scoring_quota_status = 'consumed' WHERE id = ?", examId);
            }
        }
        jdbc.update("UPDATE exam_session SET status = 'graded', score = ?, graded_at = NOW(3) WHERE id = ?",
                score, examId);
        return view(studentId, examId);
    }

    private List<TypeScore> typeScores(long examId) {
        return jdbc.queryForList("SELECT question_type, SUM(earned_score) AS earned,"
                        + " SUM(question_score) AS total FROM exam_session_question"
                        + " WHERE exam_session_id = ? GROUP BY question_type ORDER BY question_type", examId)
                .stream().map(row -> new TypeScore(String.valueOf(row.get("question_type")),
                        decimal(row.get("earned")), decimal(row.get("total")))).toList();
    }

    private Map<String, Object> examRow(long studentId, long examId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM exam_session"
                + " WHERE id = ? AND student_id = ?", examId, studentId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return rows.get(0);
    }

    private static void requireInProgress(Map<String, Object> row) {
        if (!IN_PROGRESS.equals(String.valueOf(row.get("status")))) {
            throw new BusinessException(ErrorCode.SESSION_NOT_IN_PROGRESS);
        }
    }

    private static void requireClaim(Map<String, Object> row, String claim) {
        String current = row.get("device_claim") == null ? null : String.valueOf(row.get("device_claim"));
        if (claim == null || claim.isBlank() || current == null || !current.equals(claim)) {
            throw new BusinessException(ErrorCode.DEVICE_TAKEN_OVER);
        }
    }

    private static boolean isExpired(Map<String, Object> row) {
        LocalDateTime deadline = toLocal(row.get("deadline_at"));
        return deadline != null && !UtcTime.now().isBefore(deadline);
    }

    private Map<String, Object> snapshotOf(QuestionDetail detail) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", detail.id());
        out.put("course_id", detail.courseId());
        out.put("question_type", detail.questionType());
        out.put("stem", detail.stem());
        out.put("options", detail.options());
        out.put("correct_answer", detail.correctAnswer());
        out.put("reference_answer", detail.referenceAnswer());
        out.put("analysis", detail.analysis());
        return out;
    }

    private Map<String, Object> questionOf(JsonNode snapshot) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (snapshot == null) {
            return out;
        }
        out.put("id", snapshot.path("id").asLong());
        out.put("course_id", snapshot.path("course_id").asLong());
        out.put("question_type", snapshot.path("question_type").asText(""));
        out.put("stem", snapshot.path("stem").asText(""));
        out.put("options", snapshot.get("options"));
        return out;
    }

    private JsonNode parse(String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw)) {
            return null;
        }
        try {
            return json.readTree(raw);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 客观题：选项完全一致才算对（与顺序练习判分口径一致） */
    /** Jackson 3 的 ArrayNode.asText() 会抛异常，非标量答案按原样 JSON 交给评分器 */
    private static String textOf(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.isValueNode() ? node.asText() : node.toString();
    }

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

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal bd ? bd : new BigDecimal(String.valueOf(value));
    }

    private static int intOf(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static boolean boolOf(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value instanceof Number n && n.intValue() == 1;
    }

    private static Boolean boolOrNull(Object value) {
        if (value == null) {
            return null;
        }
        return boolOf(value);
    }

    private static Instant toInstant(Object value) {
        LocalDateTime local = toLocal(value);
        return local == null ? null : local.toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime toLocal(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        return null;
    }

    private record PaperItem(int seqNo, QuestionDetail detail, BigDecimal score) {
    }

    public record StartExamRequest(Long learningRouteId, Long routeStageId) {
    }

    public record DeviceClaim(String deviceClaim) {
    }

    public record AnswerSaved(Instant answeredAt) {
    }

    public record InProgressView(ExamView exam) {
    }

    public record TypeScore(String questionType, BigDecimal earnedScore, BigDecimal questionScore) {
    }

    /** 判分字段在作答中通过 NON_NULL 省略，避免提前泄露答案 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExamQuestionView(int seqNo, Object question, BigDecimal questionScore,
                                   Object studentAnswer, String judgeStatus, Boolean isCorrect,
                                   BigDecimal earnedScore, Integer aiScore, String aiComment,
                                   Object correctAnswer, String referenceAnswer, String analysis) {
    }

    public record ExamView(Long id, String routeName, String stageName, String status,
                           int durationMinutes, Instant startedAt, Instant deadlineAt, Instant serverTime,
                           boolean hasShortAnswer, BigDecimal score, List<TypeScore> typeScores,
                           List<ExamQuestionView> questions) {
    }
}
