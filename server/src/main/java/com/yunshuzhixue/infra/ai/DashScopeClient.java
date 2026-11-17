package com.yunshuzhixue.infra.ai;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

/**
 * DashScope 调用封装：加载版本化提示词、替换占位符、走官方 SDK 的同步 Generation 接口，
 * 并把模型输出切成「按句一行」的列表（面试话术按句 SSE 下发，评分话术直接解析 JSON）。
 *
 * <p>只被 infra.ai 下的适配器使用；对上层暴露纯文本/结构化结果，不泄漏 SDK 类型。</p>
 */
public class DashScopeClient {

    private static final Logger log = LoggerFactory.getLogger(DashScopeClient.class);
    private static final String PROMPT_DIR = "prompts/v1/";

    private final DashScopeProperties properties;
    private final Generation generation;

    public DashScopeClient(DashScopeProperties properties) {
        this.properties = properties;
        this.generation = new Generation();
    }

    /** 常规生成：system + user 两条消息，返回原始文本 */
    public String generate(String systemPromptFile, String userPromptFile, Map<String, Object> variables) {
        List<Message> messages = new ArrayList<>();
        messages.add(message(Role.SYSTEM.getValue(), render(systemPromptFile, Map.of())));
        messages.add(message(Role.USER.getValue(), render(userPromptFile, variables)));
        return call(messages);
    }

    /** 只给一条 system 提示的生成（用于自由文本） */
    public String generateOne(String promptFile, Map<String, Object> variables) {
        return call(List.of(message(Role.USER.getValue(), render(promptFile, variables))));
    }

    private Message message(String role, String content) {
        return Message.builder().role(role).content(content).build();
    }

    private String call(List<Message> messages) {
        GenerationParam param = GenerationParam.builder()
                .model(properties.model())
                .apiKey(properties.apiKey())
                .messages(messages)
                .resultFormat("message")
                .temperature(properties.temperature())
                .maxTokens(properties.maxTokens())
                .build();
        try {
            GenerationResult result = generation.call(param);
            String text = result.getOutput().getChoices().get(0).getMessage().getContent();
            if (text == null) {
                throw new IllegalStateException("模型返回内容为空");
            }
            return text.trim();
        } catch (ApiException | NoApiKeyException | InputRequiredException e) {
            log.warn("DashScope 调用失败: {}", e.getMessage());
            throw new DashScopeException("AI 服务不可用", e);
        }
    }

    /** 读取提示词模板并替换 {{var}} 占位符 */
    private String render(String file, Map<String, Object> variables) {
        String template = readPrompt(file);
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    private String readPrompt(String file) {
        ClassPathResource resource = new ClassPathResource(PROMPT_DIR + file);
        try (InputStream in = resource.getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("提示词模板读取失败: " + file, e);
        }
    }

    /**
     * 按中文句末标点切句，去掉空行；模型偶尔输出 markdown 序号，这里一并清理。
     * 若模型没有用标点分行，则退化为按换行切分。
     */
    public List<String> toSentences(String text) {
        List<String> lines = Arrays.stream(text.split("\\r?\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.replaceFirst("^[-*\\d.、\\s]+", ""))
                .toList();
        List<String> sentences = new ArrayList<>();
        for (String line : lines) {
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                current.append(c);
                if (c == '。' || c == '？' || c == '?' || c == '！' || c == '!') {
                    String part = current.toString().trim();
                    if (!part.isEmpty()) {
                        sentences.add(part);
                    }
                    current.setLength(0);
                }
            }
            if (current.length() > 0) {
                String rest = current.toString().trim();
                if (!rest.isEmpty()) {
                    sentences.add(rest);
                }
            }
        }
        return sentences.isEmpty() ? List.of(text) : sentences;
    }
}
