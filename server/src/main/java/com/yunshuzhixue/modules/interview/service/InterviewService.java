package com.yunshuzhixue.modules.interview.service;

import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.time.BeijingDates;
import com.yunshuzhixue.modules.ai.api.InterviewAi;
import com.yunshuzhixue.modules.content.api.RouteDirectory;
import com.yunshuzhixue.modules.interview.api.InterviewDirectory;
import com.yunshuzhixue.modules.interview.api.InterviewEndedEvent;
import com.yunshuzhixue.modules.interview.api.InterviewScoreWriter;
import com.yunshuzhixue.modules.question.api.InterviewQuestionDirectory;
import com.yunshuzhixue.modules.quota.api.AiQuotaApi;
import com.yunshuzhixue.modules.safety.api.SensitiveTextFilter;
import com.yunshuzhixue.modules.sysconfig.api.SysConfigReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 面试（M11，需求 5.6、S-IVW-01~07）：抽题、次数、对话流转、设备接管与超时结束。
 * 文字面试走 SSE，语音面试由 WS-01 调用同一套 submit/end 逻辑，保证两种模式报告结构一致。
 */
@Service
public class InterviewService implements InterviewDirectory, InterviewScoreWriter {

    private static final String IN_PROGRESS = "in_progress";
    private static final String ENDED = "ended";
    private static final String TEXT = "text";
    private static final String VOICE = "voice";

    /** 单场次同一时间只允许一条 AI 回复，避免重复提交（2.10 SESSION_BUSY） */
    private final Map<Long, Boolean> busy = new ConcurrentHashMap<>();

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final RouteDirectory routes;
    private final InterviewQuestionDirectory questions;
    private final SysConfigReader configs;
    private final AiQuotaApi quota;
    private final InterviewAi ai;
    private final SensitiveTextFilter safety;
    private final ApplicationEventPublisher events;

    public InterviewService(JdbcTemplate jdbc, JsonMapper json, RouteDirectory routes,
                            InterviewQuestionDirectory questions, SysConfigReader configs,
                            AiQuotaApi quota, InterviewAi ai, SensitiveTextFilter safety,
                            ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.json = json;
        this.routes = routes;
        this.questions = questions;
        this.configs = configs;
        this.quota = quota;
        this.ai = ai;
        this.safety = safety;
        this.events = events;
    }

    // ===== 视图与请求结构 =====

    public record StageOption(long route_stage_id, String stage_name, boolean is_interview_available) {
    }

    public record Options(int interview_remaining, int main_question_count, int duration_minutes,
                          List<StageOption> stages) {
    }

    public record MessageView(int seq_no, String speaker, String message_type, String content, Instant created_at) {
    }

    /** 组件名以 is 开头的字段会序列化成 is_xxx（契约约定） */
    public record InterviewView(long id, String route_name, String interview_mode, String status, String end_reason,
                                int main_question_count, int current_seq_no, Instant started_at,
                                Instant deadline_at, Instant server_time, List<MessageView> messages) {
    }

    public record InProgressView(InterviewView interview) {
    }

    public record StartRequest(Long learningRouteId, String scopeType, List<Long> routeStageIds) {
    }

    public record DeviceClaim(String device_claim) {
    }

    public record Termination(String status, String end_reason) {
    }

    /** 一段 AI 回复：SSE 与 WS-01 共用 */
    public record Segment(String message_type, List<String> sentences, int last_message_seq_no, int current_seq_no) {
    }

    public record ReplyResult(List<Segment> segments, boolean ended, String end_reason, int current_seq_no) {
    }

    /** S-IVW-01：剩余次数、题量、时长与各阶段是否有面试题 */
    public Options options(long studentId, long routeId) {
        RouteDirectory.RouteDetail detail = routes.routeDetail(routeId);
        if (detail == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学习路线不存在或已下架");
        }
        List<StageOption> stages = new ArrayList<>();
        for (RouteDirectory.StageDetail stage : detail.stages()) {
            long count = questionCountOfStage(stage);
            stages.add(new StageOption(stage.id(), stage.name(), count > 0));
        }
        return new Options(quota.current(studentId).interviewRemaining(),
                configs.getInt("interview.main_question_count"),
                configs.getInt("interview.duration_minutes"), stages);
    }

    private long questionCountOfStage(RouteDirectory.StageDetail stage) {
        List<Long> courseIds = stage.courses().stream().map(RouteDirectory.CourseItem::id).toList();
        return questions.countsOfCourses(courseIds).values().stream().mapToLong(Long::longValue).sum();
    }

    /** S-IVW-02：发起文字面试（发起时扣次数） */
    @Transactional
    public InterviewView start(long studentId, StartRequest request) {
        return startInternal(studentId, request, TEXT);
    }

    /** WS-01 设备检测通过后调用：创建语音面试场次并扣次数 */
    @Transactional
    public InterviewView startVoice(long studentId, StartRequest request) {
        return startInternal(studentId, request, VOICE);
    }

    private InterviewView startInternal(long studentId, StartRequest request, String mode) {
        Long routeId = request.learningRouteId();
        if (routeId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择学习路线");
        }
        RouteDirectory.RouteDetail detail = routes.routeDetail(routeId);
        if (detail == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学习路线不存在或已下架");
        }
        Map<String, Object> existing = findInProgress(studentId);
        if (existing != null) {
            throw new BusinessException(ErrorCode.SESSION_IN_PROGRESS, "已有进行中的面试，请先继续或结束该场面试",
                    List.of(Map.of("interview_session_id", existing.get("id"))));
        }
        if (quota.current(studentId).interviewRemaining() <= 0) {
            throw new BusinessException(ErrorCode.AI_QUOTA_EXHAUSTED);
        }

        int mainN = configs.getInt("interview.main_question_count");
        int maxFollowup = configs.getInt("interview.max_followup_count");
        int minutes = configs.getInt("interview.duration_minutes");
        int minimum = configs.getInt("interview.min_question_count");
        List<PaperItem> paper = buildPaper(detail, request.scopeType(), request.routeStageIds(), mainN);
        if (paper.size() < minimum) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_QUESTIONS, "题目建设中，题量不足");
        }

        LocalDate quotaDate = LocalDate.now(BeijingDates.ZONE);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("main_question_count", mainN);
        snapshot.put("max_followup_count", maxFollowup);
        snapshot.put("duration_minutes", minutes);
        snapshot.put("scope_type", request.scopeType());
        snapshot.put("stage_ids", request.routeStageIds() == null ? List.of() : request.routeStageIds());
        snapshot.put("grade_thresholds", configs.get("interview.grade_thresholds").toString());

        jdbc.update("INSERT INTO interview_session (student_id, learning_route_id, route_name, interview_mode,"
                        + " scope_type, status, config_snapshot, main_question_count, answered_count,"
                        + " duration_minutes, quota_date, started_at, deadline_at, device_claim)"
                        + " VALUES (?, ?, ?, ?, ?, 'in_progress', ?, ?, 0, ?, ?, NOW(3),"
                        + " DATE_ADD(NOW(3), INTERVAL ? MINUTE), ?)",
                studentId, routeId, detail.name(), mode, request.scopeType(),
                json.writeValueAsString(snapshot), paper.size(), minutes, quotaDate,
                minutes, UUID.randomUUID().toString());
        long sessionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // 阶段与题目快照
        Map<Long, Integer> perStage = new LinkedHashMap<>();
        for (PaperItem item : paper) {
            perStage.merge(item.stageId(), 1, Integer::sum);
        }
        for (Map.Entry<Long, Integer> entry : perStage.entrySet()) {
            String stageName = detail.stages().stream().filter(s -> s.id() == entry.getKey())
                    .findFirst().map(RouteDirectory.StageDetail::name).orElse("阶段");
            jdbc.update("INSERT INTO interview_session_stage"
                            + " (interview_session_id, route_stage_id, stage_name, question_count) VALUES (?, ?, ?, ?)",
                    sessionId, entry.getKey(), stageName, entry.getValue());
        }
        int seqNo = 1;
        for (PaperItem item : paper) {
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("id", item.question().id());
            snap.put("stem", item.question().stem());
            snap.put("reference_answer", item.question().referenceAnswer());
            snap.put("scoring_points", item.question().scoringPoints());
            snap.put("followup_direction", item.question().followupDirection());
            jdbc.update("INSERT INTO interview_session_question"
                            + " (interview_session_id, seq_no, interview_question_id, course_id, route_stage_id,"
                            + " question_snapshot, answer_status, followup_count)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, 0)",
                    sessionId, seqNo, item.question().id(), item.question().courseId(), item.stageId(),
                    json.writeValueAsString(snap), seqNo == 1 ? "unanswered" : "not_asked");
            seqNo++;
        }

        // 扣次数：流水唯一键保证幂等；不足时整场回滚
        if (!quota.charge(studentId, "interview", "interview_session", sessionId)) {
            throw new BusinessException(ErrorCode.AI_QUOTA_EXHAUSTED);
        }

        // 开场白 + 第一题
        Long firstRowId = questionRowId(sessionId, 1);
        for (String sentence : ai.opening(detail.name())) {
            appendMessage(sessionId, null, "ai", "opening",
                    safety.maskAiOutput(studentId, SensitiveTextFilter.SCENE_TEXT_INTERVIEW, sessionId, sentence));
        }
        String firstStem = stemOf(paper.get(0).question());
        for (String sentence : ai.mainQuestion(firstStem, 1, paper.size())) {
            appendMessage(sessionId, firstRowId, "ai", "main_question",
                    safety.maskAiOutput(studentId, SensitiveTextFilter.SCENE_TEXT_INTERVIEW, sessionId, sentence));
        }
        return viewInternal(sessionRow(studentId, sessionId));
    }

    /** S-IVW-03 */
    public InProgressView inProgress(long studentId) {
        Map<String, Object> row = findInProgress(studentId);
        return new InProgressView(row == null ? null : viewInternal(row));
    }

    /** S-IVW-04 */
    public InterviewView view(long studentId, long sessionId) {
        return viewInternal(sessionRow(studentId, sessionId));
    }

    /** S-IVW-05：新设备接管，旧凭证立即失效 */
    public DeviceClaim claim(long studentId, long sessionId) {
        Map<String, Object> row = sessionRow(studentId, sessionId);
        if (!IN_PROGRESS.equals(String.valueOf(row.get("status")))) {
            throw new BusinessException(ErrorCode.SESSION_NOT_IN_PROGRESS);
        }
        String claim = UUID.randomUUID().toString();
        jdbc.update("UPDATE interview_session SET device_claim = ? WHERE id = ?", claim, sessionId);
        return new DeviceClaim(claim);
    }

    /**
     * S-IVW-06 / WS-01 共用：提交一条回答，生成 AI 追问或推进到下一题。
     * SSE 与 WebSocket 只负责把返回的 Segment 按各自协议下发。
     */
    public ReplyResult submit(long studentId, long sessionId, String content, String claim, String scene) {
        Map<String, Object> row = sessionRow(studentId, sessionId);
        assertActive(row, claim);
        if (busy.putIfAbsent(sessionId, true) != null) {
            throw new BusinessException(ErrorCode.SESSION_BUSY);
        }
        try {
            if (content == null || content.isBlank() || content.length() > 5000) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "回答内容需为 1~5000 字符");
            }
            if (safety.studentInputHits(studentId, scene, sessionId, content)) {
                throw new BusinessException(ErrorCode.SENSITIVE_WORD_BLOCKED);
            }
            int mainN = number(row.get("main_question_count")).intValue();
            int answered = number(row.get("answered_count")).intValue();
            int current = Math.min(answered + 1, mainN);
            int maxFollowup = readConfigInt(row, "max_followup_count", 2);
            long questionRowId = questionRowId(sessionId, current);

            appendMessage(sessionId, questionRowId, "student", "answer", content);
            jdbc.update("UPDATE interview_session_question SET answer_status = 'answered' WHERE id = ?", questionRowId);

            List<Segment> segments = new ArrayList<>();
            boolean ended = false;
            String endReason = null;
            int nextCurrent = current;

            boolean followup = ai.needsFollowup(content,
                    number(jdbc.queryForObject("SELECT followup_count FROM interview_session_question WHERE id = ?",
                            Integer.class, questionRowId)).intValue(), maxFollowup);
            if (followup) {
                jdbc.update("UPDATE interview_session_question SET followup_count = followup_count + 1 WHERE id = ?",
                        questionRowId);
                segments.add(emit(studentId, sessionId, scene, questionRowId, "followup",
                        ai.followup(stemOfSnapshot(sessionId, current), content, 1), current));
            } else {
                jdbc.update("UPDATE interview_session SET answered_count = answered_count + 1 WHERE id = ?", sessionId);
                answered++;
                if (answered >= mainN) {
                    segments.add(emit(studentId, sessionId, scene, null, "closing", ai.closing(), mainN));
                    endSession(studentId, sessionId, row, "completed", false);
                    ended = true;
                    endReason = "completed";
                    nextCurrent = mainN;
                } else {
                    nextCurrent = answered + 1;
                    long nextRowId = questionRowId(sessionId, nextCurrent);
                    jdbc.update("UPDATE interview_session_question SET answer_status = 'unanswered' WHERE id = ?",
                            nextRowId);
                    segments.add(emit(studentId, sessionId, scene, null, "transition",
                            ai.transition(nextCurrent, mainN), nextCurrent));
                    segments.add(emit(studentId, sessionId, scene, nextRowId, "main_question",
                            ai.mainQuestion(stemOfSnapshot(sessionId, nextCurrent), nextCurrent, mainN),
                            nextCurrent));
                }
            }
            return new ReplyResult(segments, ended, endReason, nextCurrent);
        } catch (InterviewAi.UnavailableException e) {
            // 需求 8.6：1 分钟内无法恢复则结束面试、退还次数并生成报告
            endSession(studentId, sessionId, row, "ai_failure", true);
            return new ReplyResult(List.of(), true, "ai_failure", 0);
        } finally {
            busy.remove(sessionId);
        }
    }

    /** S-IVW-07：主动结束；过截止时间视为超时结束 */
    public Termination terminate(long studentId, long sessionId, String claim) {
        Map<String, Object> row = sessionRow(studentId, sessionId);
        assertActive(row, claim);
        boolean expired = instant(row.get("deadline_at")) != null
                && instant(row.get("deadline_at")).isBefore(Instant.now());
        String reason = expired ? "timeout" : "student_ended";
        endSession(studentId, sessionId, row, reason, false);
        return new Termination(ENDED, reason);
    }

    /** 定时任务调用：超过时长上限自动结束 */
    public void endByTimeout(long studentId, long sessionId) {
        Map<String, Object> row;
        try {
            row = sessionRow(studentId, sessionId);
        } catch (RuntimeException e) {
            return;
        }
        if (IN_PROGRESS.equals(String.valueOf(row.get("status")))) {
            endSession(studentId, sessionId, row, "timeout", false);
        }
    }

    /** 面试结束后发布事件，由 M12 生成报告 */
    private void endSession(long studentId, long sessionId, Map<String, Object> row, String reason, boolean refund) {
        int updated = jdbc.update("UPDATE interview_session SET status = 'ended', end_reason = ?,"
                        + " ended_at = NOW(3), is_quota_refunded = ? WHERE id = ? AND status = 'in_progress'",
                reason, refund ? 1 : 0, sessionId);
        if (updated == 0) {
            return;
        }
        if (refund) {
            quota.refundInterview(studentId, LocalDate.ofInstant(
                    instant(row.get("started_at")) == null ? Instant.now() : instant(row.get("started_at")),
                    BeijingDates.ZONE), sessionId);
        }
        events.publishEvent(new InterviewEndedEvent(studentId, sessionId, reason, refund));
    }

    /** AI 回复逐句过滤后写入消息表，并返回可下发的一段 */
    private Segment emit(long studentId, long sessionId, String scene, Long questionRowId,
                         String messageType, List<String> sentences, int currentSeqNo) {
        List<String> filtered = new ArrayList<>();
        int lastSeq = 0;
        for (String sentence : sentences) {
            String masked = safety.maskAiOutput(studentId, scene, sessionId, sentence);
            filtered.add(masked);
            lastSeq = appendMessage(sessionId, questionRowId, "ai",
                    messageType.equals("main_question") ? "main_question"
                            : messageType.equals("transition") ? "transition"
                            : messageType.equals("closing") ? "closing" : "followup",
                    masked);
        }
        return new Segment(messageType, filtered, lastSeq, currentSeqNo);
    }

    private int appendMessage(long sessionId, Long questionRowId, String speaker, String type, String content) {
        int seqNo = jdbc.queryForObject("SELECT COALESCE(MAX(seq_no), 0) + 1 FROM interview_message"
                + " WHERE interview_session_id = ?", Integer.class, sessionId);
        jdbc.update("INSERT INTO interview_message"
                        + " (interview_session_id, interview_session_question_id, seq_no, speaker, message_type, content)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                sessionId, questionRowId, seqNo, speaker, type, content);
        return seqNo;
    }

    private void assertActive(Map<String, Object> row, String claim) {
        if (!IN_PROGRESS.equals(String.valueOf(row.get("status")))) {
            throw new BusinessException(ErrorCode.SESSION_NOT_IN_PROGRESS);
        }
        Instant deadline = instant(row.get("deadline_at"));
        if (deadline != null && deadline.isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }
        String expected = row.get("device_claim") == null ? null : String.valueOf(row.get("device_claim"));
        if (expected != null && !expected.equals(claim)) {
            throw new BusinessException(ErrorCode.DEVICE_TAKEN_OVER);
        }
    }

    // ===== 抽题：所选阶段尽量平均分配，每阶段至少 1 题，题量不足由其他阶段补足 =====

    private record PaperItem(long stageId, InterviewQuestionDirectory.Question question) {
    }

    private List<PaperItem> buildPaper(RouteDirectory.RouteDetail detail, String scopeType,
                                       List<Long> stageIds, int mainN) {
        boolean wholeRoute = "whole_route".equals(scopeType);
        if (!wholeRoute && !"selected_stages".equals(scopeType)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "面试范围不正确");
        }
        if (!wholeRoute && (stageIds == null || stageIds.isEmpty() || stageIds.size() > mainN)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择 1 至主问题数量个阶段");
        }
        Map<Long, List<InterviewQuestionDirectory.Question>> pool = new LinkedHashMap<>();
        for (RouteDirectory.StageDetail stage : detail.stages()) {
            List<Long> courseIds = stage.courses().stream().map(RouteDirectory.CourseItem::id).toList();
            pool.put(stage.id(), new ArrayList<>(questions.questionsOfCourses(courseIds)));
        }
        List<Long> chosen = new ArrayList<>();
        if (wholeRoute) {
            List<Long> available = pool.entrySet().stream().filter(e -> !e.getValue().isEmpty())
                    .map(Map.Entry::getKey).toList();
            if (available.size() > mainN) {
                List<Long> shuffled = new ArrayList<>(available);
                Collections.shuffle(shuffled);
                chosen.addAll(shuffled.subList(0, mainN));
            } else {
                chosen.addAll(available);
            }
        } else {
            for (Long id : stageIds) {
                if (!pool.containsKey(id)) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "所选阶段不属于该路线");
                }
                if (pool.get(id).isEmpty()) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "所选阶段暂无面试题");
                }
                if (!chosen.contains(id)) {
                    chosen.add(id);
                }
            }
        }
        if (chosen.isEmpty()) {
            return List.of();
        }
        int total = chosen.stream().mapToInt(id -> pool.get(id).size()).sum();
        int wanted = Math.min(mainN, total);
        // 平均分配，余数给靠前的阶段
        int base = wanted / chosen.size();
        int extra = wanted % chosen.size();
        Map<Long, Integer> quota = new LinkedHashMap<>();
        for (int i = 0; i < chosen.size(); i++) {
            quota.put(chosen.get(i), Math.max(1, base + (i < extra ? 1 : 0)));
        }
        // 先按配额取，题量不够的差额由其它阶段补足
        List<PaperItem> paper = new ArrayList<>();
        for (Long stageId : chosen) {
            List<InterviewQuestionDirectory.Question> available = pool.get(stageId);
            int take = Math.min(quota.get(stageId), available.size());
            for (int i = 0; i < take; i++) {
                paper.add(new PaperItem(stageId, available.remove(0)));
            }
        }
        for (Long stageId : chosen) {
            while (paper.size() < wanted) {
                List<InterviewQuestionDirectory.Question> available = pool.get(stageId);
                if (available.isEmpty()) {
                    break;
                }
                paper.add(new PaperItem(stageId, available.remove(0)));
            }
            if (paper.size() >= wanted) {
                break;
            }
        }
        return paper;
    }

    // ===== InterviewDirectory：M12 通过公开接口读取场次、题目与回答 =====

    @Override
    public List<RecordItem> records(long studentId, long offset, int limit) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT s.id, s.started_at, s.route_name,"
                        + " s.interview_mode, s.status, s.end_reason, s.deadline_at, s.learning_route_id,"
                        + " (SELECT COUNT(*) FROM learning_route r WHERE r.id = s.learning_route_id"
                        + "   AND r.status = 'on_shelf') AS on_shelf"
                        + " FROM interview_session s WHERE s.student_id = ?"
                        + " ORDER BY s.started_at DESC, s.id DESC LIMIT ? OFFSET ?",
                studentId, limit, offset);
        List<RecordItem> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            long id = number(row.get("id")).longValue();
            List<String> stageNames = jdbc.queryForList("SELECT stage_name FROM interview_session_stage"
                    + " WHERE interview_session_id = ? ORDER BY id", String.class, id);
            result.add(new RecordItem(id, instant(row.get("started_at")), str(row.get("route_name")), stageNames,
                    str(row.get("interview_mode")), str(row.get("status")),
                    row.get("end_reason") == null ? null : str(row.get("end_reason")),
                    instant(row.get("deadline_at")), number(row.get("on_shelf")).intValue() == 0));
        }
        return result;
    }

    @Override
    public long countRecords(long studentId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM interview_session WHERE student_id = ?",
                Long.class, studentId);
        return count == null ? 0 : count;
    }

    @Override
    public ReportData reportData(long sessionId) {
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM interview_session WHERE id = ?", sessionId);
        List<StageItem> stages = jdbc.queryForList("SELECT route_stage_id, stage_name, question_count, score"
                        + " FROM interview_session_stage WHERE interview_session_id = ? ORDER BY id", sessionId)
                .stream().map(s -> new StageItem(number(s.get("route_stage_id")).longValue(),
                        str(s.get("stage_name")), number(s.get("question_count")).intValue(),
                        s.get("score") == null ? null : ((Number) s.get("score")).doubleValue())).toList();
        List<QuestionItem> items = new ArrayList<>();
        List<Map<String, Object>> qs = jdbc.queryForList("SELECT id, seq_no, route_stage_id, course_id,"
                        + " question_snapshot, answer_status, followup_count, score, ai_comment, missing_points"
                        + " FROM interview_session_question WHERE interview_session_id = ? ORDER BY seq_no", sessionId);
        for (Map<String, Object> q : qs) {
            JsonNode snap = parse(str(q.get("question_snapshot")));
            long rowId = number(q.get("id")).longValue();
            List<String> answers = jdbc.queryForList("SELECT content FROM interview_message"
                            + " WHERE interview_session_id = ? AND interview_session_question_id = ?"
                            + " AND speaker = 'student' ORDER BY seq_no", String.class, sessionId, rowId);
            items.add(new QuestionItem(rowId, number(q.get("seq_no")).intValue(),
                    number(q.get("route_stage_id")).longValue(), number(q.get("course_id")).longValue(),
                    text(snap, "stem"),
                    text(snap, "reference_answer"), text(snap, "scoring_points"),
                    str(q.get("answer_status")), number(q.get("followup_count")).intValue(), answers,
                    q.get("score") == null ? null : number(q.get("score")).intValue(),
                    str(q.get("ai_comment")), str(q.get("missing_points"))));
        }
        List<MessageItem> messages = jdbc.queryForList("SELECT seq_no, interview_session_question_id,"
                        + " speaker, message_type, content, created_at FROM interview_message"
                        + " WHERE interview_session_id = ? ORDER BY seq_no", sessionId)
                .stream().map(m -> new MessageItem(number(m.get("seq_no")).intValue(),
                        m.get("interview_session_question_id") == null ? 0L
                                : number(m.get("interview_session_question_id")).longValue(),
                        str(m.get("speaker")), str(m.get("message_type")), str(m.get("content")),
                        instant(m.get("created_at"))))
                .toList();
        return new ReportData(sessionId, number(row.get("student_id")).longValue(), str(row.get("route_name")),
                str(row.get("interview_mode")), row.get("end_reason") == null ? null : str(row.get("end_reason")),
                instant(row.get("started_at")), number(row.get("main_question_count")).intValue(), stages, items,
                messages);
    }

    // ===== InterviewScoreWriter：M12 回写逐题与分阶段得分 =====

    @Override
    public void write(long sessionId, Map<Integer, QuestionScore> questionScores, Map<Long, Double> stageScores) {
        for (Map.Entry<Integer, QuestionScore> entry : questionScores.entrySet()) {
            QuestionScore score = entry.getValue();
            jdbc.update("UPDATE interview_session_question SET score = ?, ai_comment = ?, missing_points = ?"
                            + " WHERE interview_session_id = ? AND seq_no = ?",
                    score.score(), score.comment(), score.missingPoints(), sessionId, entry.getKey());
        }
        for (Map.Entry<Long, Double> entry : stageScores.entrySet()) {
            jdbc.update("UPDATE interview_session_stage SET score = ?"
                            + " WHERE interview_session_id = ? AND route_stage_id = ?",
                    oneDecimal(entry.getValue()), sessionId, entry.getKey());
        }
    }

    private static java.math.BigDecimal oneDecimal(double value) {
        return java.math.BigDecimal.valueOf(Math.round(value * 10) / 10.0);
    }

    // ===== 工具 =====

    private InterviewView viewInternal(Map<String, Object> row) {
        long sessionId = number(row.get("id")).longValue();
        List<MessageView> messages = jdbc.queryForList("SELECT seq_no, speaker, message_type, content, created_at"
                        + " FROM interview_message WHERE interview_session_id = ? ORDER BY seq_no", sessionId)
                .stream().map(m -> new MessageView(number(m.get("seq_no")).intValue(), str(m.get("speaker")),
                        str(m.get("message_type")), str(m.get("content")), instant(m.get("created_at"))))
                .toList();
        int answered = number(row.get("answered_count")).intValue();
        int mainN = number(row.get("main_question_count")).intValue();
        return new InterviewView(sessionId, str(row.get("route_name")), str(row.get("interview_mode")),
                str(row.get("status")), row.get("end_reason") == null ? null : str(row.get("end_reason")),
                mainN, ENDED.equals(str(row.get("status"))) ? Math.min(answered + 1, mainN) : answered + 1,
                instant(row.get("started_at")), instant(row.get("deadline_at")), Instant.now(), messages);
    }

    private Map<String, Object> findInProgress(long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM interview_session"
                + " WHERE student_id = ? AND status = 'in_progress' ORDER BY id DESC LIMIT 1", studentId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> sessionRow(long studentId, long sessionId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM interview_session"
                + " WHERE id = ? AND student_id = ?", sessionId, studentId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "面试场次不存在");
        }
        return rows.get(0);
    }

    private long questionRowId(long sessionId, int seqNo) {
        return jdbc.queryForObject("SELECT id FROM interview_session_question"
                + " WHERE interview_session_id = ? AND seq_no = ?", Long.class, sessionId, seqNo);
    }

    private String stemOfSnapshot(long sessionId, int seqNo) {
        return text(parse(str(jdbc.queryForObject("SELECT question_snapshot FROM interview_session_question"
                + " WHERE interview_session_id = ? AND seq_no = ?", String.class, sessionId, seqNo))), "stem");
    }

    private String stemOf(InterviewQuestionDirectory.Question q) {
        return q.stem() == null ? "" : q.stem();
    }

    private int readConfigInt(Map<String, Object> row, String key, int fallback) {
        try {
            JsonNode node = parse(str(row.get("config_snapshot")));
            return node == null || node.get(key) == null ? fallback : node.get(key).asInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return json.readTree(raw);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String key) {
        if (node == null || node.get(key) == null || node.get(key).isNull()) {
            return null;
        }
        return node.get(key).asText();
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Number number(Object value) {
        return value instanceof Number n ? n : 0;
    }

    private static Instant instant(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Instant i) {
            return i;
        }
        if (raw instanceof LocalDateTime ldt) {
            return ldt.toInstant(ZoneOffset.UTC);
        }
        if (raw instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toInstant(ZoneOffset.UTC);
        }
        return null;
    }
}
