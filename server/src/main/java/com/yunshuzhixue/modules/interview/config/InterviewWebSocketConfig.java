package com.yunshuzhixue.modules.interview.config;

import com.yunshuzhixue.modules.interview.service.VoiceInterviewHandler;
import com.yunshuzhixue.modules.interview.service.VoiceInputHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** WS-01 语音面试 / WS-02 文字面试语音输入（04.API接口设计.md 7.1） */
@Configuration
@EnableWebSocket
public class InterviewWebSocketConfig implements WebSocketConfigurer {

    private final VoiceInterviewHandler voiceInterview;
    private final VoiceInputHandler voiceInput;

    public InterviewWebSocketConfig(VoiceInterviewHandler voiceInterview, VoiceInputHandler voiceInput) {
        this.voiceInterview = voiceInterview;
        this.voiceInput = voiceInput;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(voiceInterview, "/ws/v1/student/voice-interview").setAllowedOrigins("*");
        registry.addHandler(voiceInput, "/ws/v1/student/voice-input").setAllowedOrigins("*");
    }
}
