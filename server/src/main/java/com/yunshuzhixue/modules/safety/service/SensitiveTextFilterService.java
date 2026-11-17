package com.yunshuzhixue.modules.safety.service;

import com.yunshuzhixue.modules.safety.api.SensitiveTextFilter;
import com.yunshuzhixue.modules.sysconfig.api.SensitiveWordCatalog;
import com.yunshuzhixue.modules.sysconfig.api.SensitiveWordsChangedEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 进程内词库缓存 + 顺序匹配（词库规模小，命中路径毫秒级；接入 AC 自动机时可替换本实现）。
 * 命中日志只留存，不提供后台查看页面（需求 7.6）。
 */
@Service
public class SensitiveTextFilterService implements SensitiveTextFilter {

    private final SensitiveWordCatalog catalog;
    private final JdbcTemplate jdbc;
    private final AtomicReference<List<String>> words = new AtomicReference<>(List.of());

    public SensitiveTextFilterService(SensitiveWordCatalog catalog, JdbcTemplate jdbc) {
        this.catalog = catalog;
        this.jdbc = jdbc;
    }

    @EventListener(SensitiveWordsChangedEvent.class)
    public void refresh() {
        words.set(List.of());
    }

    private List<String> load() {
        List<String> cached = words.get();
        if (!cached.isEmpty()) {
            return cached;
        }
        List<String> loaded = catalog.words();
        words.compareAndSet(List.of(), loaded.isEmpty() ? List.of("__empty__") : loaded);
        List<String> current = words.get();
        return "__empty__".equals(current.get(0)) ? List.of() : current;
    }

    @Override
    public boolean studentInputHits(long studentId, String scene, Long interviewSessionId, String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        boolean hit = false;
        for (String word : load()) {
            if (text.contains(word)) {
                logHit(studentId, scene, "student", word, text, interviewSessionId);
                hit = true;
            }
        }
        return hit;
    }

    @Override
    public String maskAiOutput(long studentId, String scene, Long interviewSessionId, String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String masked = text;
        for (String word : load()) {
            if (masked.contains(word)) {
                logHit(studentId, scene, "ai", word, text, interviewSessionId);
                masked = masked.replace(word, "*".repeat(word.length()));
            }
        }
        return masked;
    }

    private void logHit(long studentId, String scene, String source, String word, String sentence, Long sessionId) {
        jdbc.update("INSERT INTO sensitive_word_hit_log"
                        + " (student_id, scene, source, word, sentence, interview_session_id)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                studentId, scene, source, word, sentence.substring(0, Math.min(sentence.length(), 500)), sessionId);
    }
}
