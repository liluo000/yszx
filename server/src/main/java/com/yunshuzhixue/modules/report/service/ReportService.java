package com.yunshuzhixue.modules.report.service;

import com.yunshuzhixue.common.api.PageResult;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.ai.api.InterviewAi;
import com.yunshuzhixue.modules.interview.api.InterviewDirectory;
import com.yunshuzhixue.modules.interview.api.InterviewEndedEvent;
import com.yunshuzhixue.modules.interview.api.InterviewScoreWriter;
import com.yunshuzhixue.modules.message.api.StudentMessageSender;
import com.yunshuzhixue.modules.sysconfig.api.SysConfigReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 面试报告与记录（M12，需求 5.6.5 / 5.8、S-RPT-01~03）。
 * 订阅 M11 的「面试已结束」事件生成报告；逐题得分经 M11 公开接口写回，不直接改 M11 的表。
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final InterviewDirectory directory;
    private final InterviewScoreWriter scoreWriter;
    private final InterviewAi ai;
    private final SysConfigReader configs;
    private final StudentMessageSender messages;

    public ReportService(JdbcTemplate jdbc, JsonMapper json, InterviewDirectory directory,
                         InterviewScoreWriter scoreWriter, InterviewAi ai, SysConfigReader configs,
                         StudentMessageSender messages) {
        this.jdbc = jdbc;
        this.json = json;
        this.directory = directory;
        this.scoreWriter = scoreWriter;
        this.ai = ai;
        this.configs = configs;
        this.messages = messages;
    }

    // ===== 对外视图（组件名用契约里的 snake_case） =====

    public record InterviewListItem(long id, Instant started_at, String route_name, List<String> stage_names,
                                    String interview_mode, BigDecimal total_score, String grade,
                                    String record_status, boolean can_resume, boolean is_route_off_shelf) {
    }

    public record StageScore(long route_stage_id, String stage_name, BigDecimal score) {
    }

    public record MessageView(int seq_no, String speaker, String message_type, String content, Instant created_at) {
    }

    public record ReportQuestion(int seq_no, String stem, String answer_status, Integer score, String ai_comment,
                                 String missing_points, String reference_answer, List<MessageView> messages) {
    }

    public record CourseRef(long id, String title) {
    }

    public record LearningAdvice(long route_stage_id, String advice, List<CourseRef> courses) {
    }

    public record ReportView(long interview_session_id, String status, BigDecimal total_score, String grade,
                             boolean is_ended_by_system_failure, List<StageScore> stages,
                             List<ReportQuestion> questions, List<LearningAdvice> learning_advice,
                             Instant generated_at) {
    }

    /** S-RPT-01 面试记录列表 */
    public PageResult<InterviewListItem> list(long studentId, int page, int pageSize) {
        long total = directory.countRecords(studentId);
        List<InterviewDirectory.RecordItem> records = directory.records(studentId,
                (long) (page - 1) * pageSize, pageSize);
        List<InterviewListItem> items = new ArrayList<>();
        for (InterviewDirectory.RecordItem record : records) {
            Map<String, Object> report = reportRow(record.id());
            String status = report == null ? null : str(report.get("status"));
            BigDecimal totalScore = report == null || report.get("total_score") == null
                    ? null : new BigDecimal(str(report.get("total_score")));
            String grade = report == null ? null : str(report.get("grade"));
            String recordStatus;
            if ("in_progress".equals(record.status())) {
                recordStatus = "in_progress";
            } else if (report == null && "ai_failure".equals(record.endReason())) {
                recordStatus = "system_failed";
            } else if ("generating".equals(status)) {
                recordStatus = "report_generating";
            } else if ("failed".equals(status)) {
                recordStatus = "report_failed";
            } else {
                recordStatus = "ended";
            }
            boolean canResume = "in_progress".equals(record.status())
                    && (record.deadlineAt() == null || record.deadlineAt().isAfter(Instant.now()));
            items.add(new InterviewListItem(record.id(), record.startedAt(), record.routeName(),
                    record.stageNames(), record.interviewMode(), totalScore, grade, recordStatus,
                    canResume, record.isRouteOffShelf()));
        }
        return new PageResult<>(items, total, page, pageSize);
    }

    /** S-RPT-02 查看报告；生成中返回 status=generating，一题未答的异常场次返回 null */
    public ReportView view(long studentId, long sessionId) {
        Map<String, Object> report = reportRow(sessionId);
        if (report == null) {
            return null;
        }
        InterviewDirectory.ReportData data = directory.reportData(sessionId);
        if (data.studentId() != studentId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "面试报告不存在");
        }
        boolean failure = "ai_failure".equals(data.endReason());
        String status = str(report.get("status"));
        if (!"generated".equals(status)) {
            return new ReportView(sessionId, status, null, null, failure,
                    List.of(), List.of(), List.of(), null);
        }
        List<StageScore> stages = data.stages().stream()
                .map(s -> new StageScore(s.routeStageId(), s.stageName(),
                        s.score() == null ? null : oneDecimal(s.score())))
                .toList();
        List<ReportQuestion> questions = data.questions().stream()
                .map(q -> new ReportQuestion(q.seqNo(), q.stem(), q.answerStatus(), q.score(),
                        q.aiComment(), q.missingPoints(), q.referenceAnswer(), messagesOf(data, q.rowId())))
                .toList();
        return new ReportView(sessionId, "generated",
                report.get("total_score") == null ? null : new BigDecimal(str(report.get("total_score"))),
                str(report.get("grade")), failure, stages, questions,
                parseAdvice(report.get("learning_advice")), instant(report.get("generated_at")));
    }

    /** S-RPT-03 仅生成失败可重新生成，不额外扣次数 */
    public ReportView regenerate(long studentId, long sessionId) {
        Map<String, Object> report = reportRow(sessionId);
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "面试报告不存在");
        }
        if (!"failed".equals(str(report.get("status")))) {
            throw new BusinessException(ErrorCode.SESSION_STATE_INVALID, "仅报告生成失败时可重新生成");
        }
        jdbc.update("UPDATE interview_report SET retry_count = 0, failure_reason = NULL"
                + " WHERE interview_session_id = ?", sessionId);
        generate(sessionId);
        return view(studentId, sessionId);
    }

    /** M11 事件：面试结束后生成报告（需求 8.4） */
    @EventListener(InterviewEndedEvent.class)
    public void onInterviewEnded(InterviewEndedEvent event) {
        generate(event.interviewSessionId());
    }

    /** 报告生成失败自动重试 3 次（需求 8.6） */
    @Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    public void retryFailed() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT interview_session_id FROM interview_report"
                + " WHERE status IN ('generating', 'failed') AND retry_count < 3"
                + " AND (next_retry_at IS NULL OR next_retry_at <= NOW(3)) LIMIT 20");
        for (Map<String, Object> row : rows) {
            try {
                generate(number(row.get("interview_session_id")).longValue());
            } catch (RuntimeException e) {
                log.warn("面试报告重试失败 sessionId={}: {}", row.get("interview_session_id"), e.getMessage());
            }
        }
    }

    /** 生成一次报告：评分 → 写回 M11 → 更新报告 → 站内信 */
    public void generate(long sessionId) {
        InterviewDirectory.ReportData data = directory.reportData(sessionId);
        boolean systemFailure = "ai_failure".equals(data.endReason());
        boolean anyAsked = data.questions().stream()
                .anyMatch(q -> !"not_asked".equals(q.answerStatus()));
        if (systemFailure && !anyAsked) {
            // 需求 5.6.5：一题未答的异常场次不生成报告，记录显示「因系统异常结束」
            return;
        }
        jdbc.update("INSERT INTO interview_report (interview_session_id, status, retry_count)"
                        + " VALUES (?, 'generating', 0)"
                        + " ON DUPLICATE KEY UPDATE status = 'generating', failure_reason = NULL,"
                        + " next_retry_at = NULL", sessionId);
        try {
            Map<Integer, InterviewScoreWriter.QuestionScore> questionScores = new LinkedHashMap<>();
            for (InterviewDirectory.QuestionItem q : data.questions()) {
                if ("answered".equals(q.answerStatus())) {
                    InterviewAi.QuestionScore score = ai.score(q.stem(), q.referenceAnswer(),
                            q.scoringPoints(), String.join("\n", q.answers()));
                    questionScores.put(q.seqNo(), new InterviewScoreWriter.QuestionScore(
                            score.score(), score.comment(), score.missingPoints()));
                } else {
                    questionScores.put(q.seqNo(), new InterviewScoreWriter.QuestionScore(
                            0, "本题未作答。", "未覆盖参考答案中的要点。"));
                }
            }
            // 正常结束按全部已抽取主问题平均（未问到计 0）；系统异常只按已问到的题计算
            List<InterviewDirectory.QuestionItem> counted = systemFailure
                    ? data.questions().stream().filter(q -> !"not_asked".equals(q.answerStatus())).toList()
                    : data.questions();
            double total = counted.stream()
                    .mapToInt(q -> questionScores.get(q.seqNo()).score()).average().orElse(0);
            BigDecimal totalScore = oneDecimal(total);

            // 分阶段得分：该阶段所有主问题平均分；异常结束没问到的阶段不测评
            Map<Long, Double> stageScores = new LinkedHashMap<>();
            for (InterviewDirectory.StageItem stage : data.stages()) {
                List<InterviewDirectory.QuestionItem> stageQuestions = counted.stream()
                        .filter(q -> q.routeStageId() == stage.routeStageId()).toList();
                if (stageQuestions.isEmpty()) {
                    continue;
                }
                double avg = stageQuestions.stream()
                        .mapToInt(q -> questionScores.get(q.seqNo()).score()).average().orElse(0);
                stageScores.put(stage.routeStageId(), avg);
            }
            scoreWriter.write(sessionId, questionScores, stageScores);

            JsonNode thresholds = configs.get("interview.grade_thresholds");
            int excellent = thresholds == null || thresholds.get("excellent") == null
                    ? 80 : thresholds.get("excellent").asInt();
            int good = thresholds == null || thresholds.get("good") == null
                    ? 60 : thresholds.get("good").asInt();
            String grade = totalScore.compareTo(BigDecimal.valueOf(excellent)) >= 0 ? "excellent"
                    : totalScore.compareTo(BigDecimal.valueOf(good)) >= 0 ? "good" : "needs_improvement";

            List<LearningAdvice> advice = learningAdvice(data, stageScores, good);
            jdbc.update("UPDATE interview_report SET status = 'generated', total_score = ?, grade = ?,"
                            + " learning_advice = ?, generated_at = NOW(3), failure_reason = NULL"
                            + " WHERE interview_session_id = ?",
                    totalScore, grade, json.writeValueAsString(advice), sessionId);
            messages.send(data.studentId(), StudentMessageSender.INTERVIEW_REPORT_GENERATED,
                    "面试报告已生成", "你在「" + data.routeName() + "」的面试报告已生成，可在面试记录中查看。",
                    sessionId);
        } catch (RuntimeException e) {
            jdbc.update("UPDATE interview_report SET status = 'failed',"
                            + " retry_count = LEAST(retry_count + 1, 3),"
                            + " next_retry_at = DATE_ADD(NOW(3), INTERVAL 1 MINUTE), failure_reason = ?"
                            + " WHERE interview_session_id = ?",
                    e.getMessage() == null ? "未知错误" : e.getMessage().substring(0,
                            Math.min(e.getMessage().length(), 500)), sessionId);
            log.warn("面试报告生成失败 sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /** 薄弱阶段给建议并推荐课程；没有薄弱阶段时取得分最低的阶段 */
    private List<LearningAdvice> learningAdvice(InterviewDirectory.ReportData data,
                                                Map<Long, Double> stageScores, int goodLine) {
        if (stageScores.isEmpty()) {
            return List.of();
        }
        List<Map.Entry<Long, Double>> weak = stageScores.entrySet().stream()
                .filter(e -> e.getValue() < goodLine).toList();
        if (weak.isEmpty()) {
            weak = List.of(stageScores.entrySet().stream()
                    .min(Map.Entry.comparingByValue()).orElseThrow());
        }
        List<LearningAdvice> result = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : weak) {
            InterviewDirectory.StageItem stage = data.stages().stream()
                    .filter(s -> s.routeStageId() == entry.getKey()).findFirst().orElse(null);
            if (stage == null) {
                continue;
            }
            List<Long> courseIds = data.questions().stream()
                    .filter(q -> q.routeStageId() == entry.getKey())
                    .map(InterviewDirectory.QuestionItem::courseId).distinct().toList();
            List<CourseRef> courses = new ArrayList<>();
            if (!courseIds.isEmpty()) {
                String placeholders = String.join(",", java.util.Collections.nCopies(courseIds.size(), "?"));
                courses = jdbc.queryForList("SELECT id, title FROM course WHERE id IN (" + placeholders + ")"
                                + " ORDER BY id", courseIds.toArray())
                        .stream().map(c -> new CourseRef(number(c.get("id")).longValue(), str(c.get("title"))))
                        .toList();
            }
            result.add(new LearningAdvice(stage.routeStageId(), ai.advice(stage.stageName(), entry.getValue()),
                    courses));
        }
        return result;
    }

    private List<MessageView> messagesOf(InterviewDirectory.ReportData data, long questionRowId) {
        return data.messages().stream().filter(m -> m.questionRowId() == questionRowId)
                .map(m -> new MessageView(m.seqNo(), m.speaker(), m.messageType(), m.content(), m.createdAt()))
                .toList();
    }

    private Map<String, Object> reportRow(long sessionId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM interview_report"
                + " WHERE interview_session_id = ?", sessionId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<LearningAdvice> parseAdvice(Object raw) {
        if (raw == null) {
            return List.of();
        }
        try {
            JsonNode node = json.readTree(str(raw));
            List<LearningAdvice> result = new ArrayList<>();
            for (JsonNode item : node) {
                long stageId = item.get("route_stage_id") == null ? 0 : item.get("route_stage_id").asLong();
                String advice = item.get("advice") == null ? "" : item.get("advice").asText();
                List<CourseRef> courses = new ArrayList<>();
                JsonNode courseNodes = item.get("courses");
                if (courseNodes != null) {
                    for (JsonNode c : courseNodes) {
                        courses.add(new CourseRef(c.get("id").asLong(),
                                c.get("title") == null ? "" : c.get("title").asText()));
                    }
                }
                result.add(new LearningAdvice(stageId, advice, courses));
            }
            return result;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static BigDecimal oneDecimal(double value) {
        return BigDecimal.valueOf(Math.round(value * 10) / 10.0).setScale(1, RoundingMode.HALF_UP);
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
