package com.yunshuzhixue.modules.interview.service;

import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.modules.ai.api.InterviewAi;
import com.yunshuzhixue.modules.quota.api.AiQuotaApi;
import com.yunshuzhixue.modules.safety.api.SensitiveTextFilter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * WS-01 语音面试（04.API接口设计.md 7.2）：一次性票据认证、排队与名额、设备检测、
 * 实时字幕、AI 文本、敏感词拦截、接管与结束。
 *
 * 架构 T05 的 ASR/TTS 厂商待定：当前音频帧由 {@link InterviewAi#transcribe(byte[])} 占位处理，
 * 前端可用 dev_transcript 文本帧走完整对话流程；接入真实语音服务时只需替换 ai 适配器。
 */
@Component
public class VoiceInterviewHandler extends AbstractWebSocketHandler {

    private static final String PURPOSE = "voice_interview";

    private static final class State {
        WebSocketSession session;
        Long studentId;
        boolean authenticated;
        final long createdAt = Instant.now().toEpochMilli();
        String slotId;
        boolean slotHeld;
        Long sessionId;
        String claim;
        InterviewService.StartRequest pending;
        final StringBuilder transcript = new StringBuilder();
    }

    private static final class Queued {
        final WebSocketSession session;
        final State state;
        Queued(WebSocketSession session, State state) {
            this.session = session;
            this.state = state;
        }
    }

    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Queued> queue = new ConcurrentLinkedQueue<>();
    private final WsTicketService tickets;
    private final VoiceSlotService slots;
    private final InterviewService interviews;
    private final InterviewAi ai;
    private final SensitiveTextFilter safety;
    private final AiQuotaApi quota;
    private final JsonMapper json;

    public VoiceInterviewHandler(WsTicketService tickets, VoiceSlotService slots, InterviewService interviews,
                                 InterviewAi ai, SensitiveTextFilter safety, AiQuotaApi quota, JsonMapper json) {
        this.tickets = tickets;
        this.slots = slots;
        this.interviews = interviews;
        this.ai = ai;
        this.safety = safety;
        this.quota = quota;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        State state = new State();
        state.session = session;
        states.put(session.getId(), state);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        State state = states.get(session.getId());
        if (state == null) {
            return;
        }
        JsonNode node;
        try {
            node = json.readTree(message.getPayload());
        } catch (RuntimeException e) {
            send(session, "error", Map.of("code", "VALIDATION_FAILED", "message", "消息格式不正确"));
            return;
        }
        String type = text(node, "type");
        if (!state.authenticated) {
            if (!"auth".equals(type)) {
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            Long studentId = tickets.consume(text(node, "ticket"), PURPOSE);
            if (studentId == null) {
                send(session, "error", Map.of("code", "WS_TICKET_INVALID", "message", "票据无效或已过期"));
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            state.studentId = studentId;
            state.authenticated = true;
            send(session, "auth_ok", Map.of());
            return;
        }
        switch (type) {
            case "start" -> onStart(session, state, node);
            case "resume" -> onResume(session, state, node);
            case "device_check_audio_end" -> send(session, "device_check_result",
                    Map.of("text", "（语音识别服务待接入，可用文字模拟回答）"));
            case "device_check_passed" -> onDevicePassed(session, state);
            case "device_check_failed" -> {
                releaseSlot(state);
                send(session, "error", Map.of("code", "DEVICE_CHECK_FAILED",
                        "message", "设备检测未通过，未扣次数，可改用文字面试"));
            }
            case "answer_done" -> submitAccumulated(session, state, "button");
            case "dev_transcript" -> {
                String text = text(node, "text");
                if (text != null && !text.isBlank()) {
                    if (state.transcript.length() > 0) {
                        state.transcript.append(' ');
                    }
                    state.transcript.append(text);
                    send(session, "transcript", Map.of("text", text, "is_final", false));
                }
                submitAccumulated(session, state, "pause");
            }
            case "interrupt" -> send(session, "thinking", Map.of());
            case "end_interview" -> endInterview(session, state);
            default -> send(session, "error", Map.of("code", "VALIDATION_FAILED", "message", "不支持的消息类型"));
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        State state = states.get(session.getId());
        if (state == null || !state.authenticated) {
            return;
        }
        // 录音不落盘：交给 ASR 适配器转写，只保留文字
        byte[] audio = new byte[message.getPayload().remaining()];
        message.getPayload().get(audio);
        String text = ai.transcribe(audio);
        if (text != null && !text.isBlank()) {
            state.transcript.append(text);
            send(session, "transcript", Map.of("text", text, "is_final", false));
        }
    }

    private void onStart(WebSocketSession session, State state, JsonNode node) throws IOException {
        List<Long> stageIds = new ArrayList<>();
        JsonNode ids = node.get("route_stage_ids");
        if (ids != null) {
            ids.forEach(id -> stageIds.add(id.asLong()));
        }
        InterviewService.StartRequest request = new InterviewService.StartRequest(
                node.get("learning_route_id") == null ? null : node.get("learning_route_id").asLong(),
                text(node, "scope_type"), stageIds);
        state.pending = request;
        state.slotId = session.getId();
        if (quota.current(state.studentId).interviewRemaining() <= 0) {
            send(session, "error", Map.of("code", "AI_QUOTA_EXHAUSTED", "message", "当日 AI 次数已用完"));
            return;
        }
        if (slots.acquire(state.slotId)) {
            state.slotHeld = true;
            send(session, "device_check_required", Map.of());
        } else {
            queue.add(new Queued(session, state));
            send(session, "queue_update", Map.of("position", queue.size()));
        }
    }

    private void onDevicePassed(WebSocketSession session, State state) throws IOException {
        if (state.pending == null) {
            send(session, "error", Map.of("code", "VALIDATION_FAILED", "message", "缺少面试发起参数"));
            return;
        }
        try {
            InterviewService.InterviewView view = interviews.startVoice(state.studentId, state.pending);
            state.sessionId = view.id();
            state.claim = interviews.claim(state.studentId, view.id()).device_claim();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("interview_session_id", view.id());
            data.put("deadline_at", String.valueOf(view.deadline_at()));
            data.put("server_time", String.valueOf(view.server_time()));
            data.put("main_question_count", view.main_question_count());
            send(session, "interview_started", data);
        } catch (BusinessException e) {
            releaseSlot(state);
            send(session, "error", Map.of("code", e.getErrorCode().name(), "message", e.getMessage()));
        }
    }

    private void onResume(WebSocketSession session, State state, JsonNode node) throws IOException {
        long sessionId = node.get("interview_session_id") == null ? 0 : node.get("interview_session_id").asLong();
        try {
            InterviewService.InterviewView view = interviews.view(state.studentId, sessionId);
            if (!"in_progress".equals(view.status()) || !"voice".equals(view.interview_mode())) {
                send(session, "error", Map.of("code", "SESSION_NOT_IN_PROGRESS", "message", "该面试不可继续"));
                return;
            }
            state.sessionId = sessionId;
            state.claim = interviews.claim(state.studentId, sessionId).device_claim();
            state.slotId = session.getId();
            state.slotHeld = slots.acquire(state.slotId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("interview_session_id", sessionId);
            data.put("deadline_at", String.valueOf(view.deadline_at()));
            data.put("server_time", String.valueOf(view.server_time()));
            data.put("messages", view.messages());
            send(session, "resumed", data);
        } catch (BusinessException e) {
            send(session, "error", Map.of("code", e.getErrorCode().name(), "message", e.getMessage()));
        }
    }

    private void submitAccumulated(WebSocketSession session, State state, String reason) throws IOException {
        if (state.sessionId == null || state.transcript.isEmpty()) {
            return;
        }
        String content = state.transcript.toString();
        state.transcript.setLength(0);
        if (safety.studentInputHits(state.studentId, SensitiveTextFilter.SCENE_VOICE_INTERVIEW,
                state.sessionId, content)) {
            send(session, "sensitive_blocked", Map.of());
            return;
        }
        send(session, "transcript", Map.of("text", content, "is_final", true));
        send(session, "thinking", Map.of());
        try {
            InterviewService.ReplyResult result = interviews.submit(state.studentId, state.sessionId,
                    content, state.claim, SensitiveTextFilter.SCENE_VOICE_INTERVIEW);
            for (InterviewService.Segment segment : result.segments()) {
                for (String sentence : segment.sentences()) {
                    send(session, "ai_text", Map.of("content", sentence,
                            "message_type", segment.message_type(), "seq_no", segment.last_message_seq_no()));
                }
                send(session, "ai_speech_end", Map.of());
            }
            send(session, "answer_end", Map.of("reason", reason));
            send(session, "progress", Map.of("current_seq_no", result.current_seq_no()));
            if (result.ended()) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("end_reason", result.end_reason());
                data.put("interview_session_id", state.sessionId);
                send(session, "interview_ended", data);
                releaseSlot(state);
            }
        } catch (BusinessException e) {
            send(session, "error", Map.of("code", e.getErrorCode().name(), "message", e.getMessage()));
        }
    }

    private void endInterview(WebSocketSession session, State state) throws IOException {
        if (state.sessionId == null) {
            return;
        }
        try {
            InterviewService.Termination result = interviews.terminate(state.studentId, state.sessionId, state.claim);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("end_reason", result.end_reason());
            data.put("interview_session_id", state.sessionId);
            send(session, "interview_ended", data);
        } catch (BusinessException e) {
            send(session, "error", Map.of("code", e.getErrorCode().name(), "message", e.getMessage()));
        } finally {
            releaseSlot(state);
        }
    }

    /** 5 秒未认证关闭；名额释放后放行排队者并推送排队位置 */
    @Scheduled(fixedDelay = 1000)
    public void maintain() {
        long now = Instant.now().toEpochMilli();
        for (Map.Entry<String, State> entry : states.entrySet()) {
            State state = entry.getValue();
            if (!state.authenticated && now - state.createdAt > 5000) {
                try {
                    if (state.session.isOpen()) {
                        state.session.close(CloseStatus.POLICY_VIOLATION);
                    }
                } catch (IOException ignored) {
                    // 连接已断开
                }
                states.remove(entry.getKey());
            }
        }
        slots.evictExpired();
        for (Queued item : queue) {
            if (!item.session.isOpen()) {
                queue.remove(item);
                continue;
            }
            if (slots.acquire(item.state.slotId)) {
                item.state.slotHeld = true;
                queue.remove(item);
                try {
                    send(item.session, "device_check_required", Map.of());
                } catch (IOException ignored) {
                    // 连接已断开，名额在 afterConnectionClosed 中释放
                }
            } else {
                try {
                    send(item.session, "queue_update", Map.of("position", queue.size()));
                } catch (IOException ignored) {
                    // 同上
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        State state = states.remove(session.getId());
        queue.removeIf(item -> item.session.getId().equals(session.getId()));
        if (state != null && state.slotHeld && state.slotId != null) {
            // 需求 5.6.4：断线后名额保留 3 分钟
            slots.holdOnDisconnect(state.slotId);
        }
    }

    private void releaseSlot(State state) {
        if (state.slotHeld && state.slotId != null) {
            slots.release(state.slotId);
            state.slotHeld = false;
        }
    }

    private void send(WebSocketSession session, String type, Object data) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        if (data instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                payload.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json.writeValueAsString(payload)));
            }
        }
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node == null ? null : node.get(key);
        return value == null || value.isNull() ? null : value.asText();
    }
}
