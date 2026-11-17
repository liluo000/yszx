package com.yunshuzhixue.modules.interview.service;

import com.yunshuzhixue.modules.ai.api.InterviewAi;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 * WS-02 文字面试的语音输入（需求 5.6.2）：听写不占语音面试名额、不扣次数、不写入数据库。
 * ASR 厂商待定，当前由 dev_transcript 文本帧承载开发占位转写结果。
 */
@Component
public class VoiceInputHandler extends AbstractWebSocketHandler {

    private static final class State {
        Long studentId;
        boolean authenticated;
        boolean listening;
        final long createdAt = Instant.now().toEpochMilli();
        final StringBuilder buffer = new StringBuilder();
        WebSocketSession session;
    }

    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final WsTicketService tickets;
    private final InterviewAi ai;
    private final JsonMapper json;

    public VoiceInputHandler(WsTicketService tickets, InterviewAi ai, JsonMapper json) {
        this.tickets = tickets;
        this.ai = ai;
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
            send(session, "unavailable", Map.of());
            return;
        }
        String type = text(node, "type");
        if (!state.authenticated) {
            if (!"auth".equals(type)) {
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            Long studentId = tickets.consume(text(node, "ticket"), "voice_input");
            if (studentId == null) {
                send(session, "unavailable", Map.of());
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            state.studentId = studentId;
            state.authenticated = true;
            send(session, "auth_ok", Map.of());
            return;
        }
        switch (type) {
            case "start" -> {
                state.listening = true;
                state.buffer.setLength(0);
            }
            case "dev_transcript" -> {
                String text = text(node, "text");
                if (text != null && !text.isBlank()) {
                    append(state, text);
                    send(session, "transcript", Map.of("text", text, "is_final", false));
                }
            }
            case "stop" -> {
                state.listening = false;
                if (state.buffer.isEmpty()) {
                    send(session, "no_speech", Map.of());
                } else {
                    send(session, "transcript", Map.of("text", state.buffer.toString(), "is_final", true));
                }
            }
            default -> send(session, "unavailable", Map.of());
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        State state = states.get(session.getId());
        if (state == null || !state.authenticated || !state.listening) {
            return;
        }
        byte[] audio = new byte[message.getPayload().remaining()];
        message.getPayload().get(audio);
        String text = ai.transcribe(audio);
        if (text != null && !text.isBlank()) {
            append(state, text);
            send(session, "transcript", Map.of("text", text, "is_final", false));
        }
    }

    private void append(State state, String text) {
        if (state.buffer.length() > 0) {
            state.buffer.append(' ');
        }
        state.buffer.append(text);
    }

    /** 5 秒内未认证，按契约关闭连接 */
    @Scheduled(fixedDelay = 1000)
    public void closeUnauthenticated() {
        long now = Instant.now().toEpochMilli();
        for (Map.Entry<String, State> entry : states.entrySet()) {
            State state = entry.getValue();
            if (!state.authenticated && now - state.createdAt > 5000 && state.session.isOpen()) {
                try {
                    state.session.close(CloseStatus.POLICY_VIOLATION);
                } catch (IOException ignored) {
                    // 连接已断开
                }
                states.remove(entry.getKey());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        states.remove(session.getId());
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
