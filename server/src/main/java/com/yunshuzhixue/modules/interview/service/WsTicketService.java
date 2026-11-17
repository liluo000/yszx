package com.yunshuzhixue.modules.interview.service;

import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** S-IVW-08：一次性 WebSocket 票据，30 秒有效，建连后第一帧发送（不放入 URL） */
@Service
public class WsTicketService {

    public record Ticket(String ticket, int expires_in) {
    }

    private static final String PREFIX = "ws:ticket:";
    private final StringRedisTemplate redis;

    public WsTicketService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Ticket issue(long studentId, String purpose) {
        String ticket = UUID.randomUUID().toString();
        redis.opsForValue().set(PREFIX + ticket, studentId + ":" + purpose, Duration.ofSeconds(30));
        return new Ticket(ticket, 30);
    }

    /** 校验并立即作废；票据无效、用途不符或已过期返回 null */
    public Long consume(String ticket, String purpose) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        String key = PREFIX + ticket;
        String raw = redis.opsForValue().get(key);
        if (raw == null) {
            return null;
        }
        redis.delete(key);
        String[] parts = raw.split(":", 2);
        if (parts.length != 2 || !parts[1].equals(purpose)) {
            return null;
        }
        try {
            return Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
