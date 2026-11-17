package com.yunshuzhixue.modules.interview.service;

import com.yunshuzhixue.modules.sysconfig.api.SysConfigReader;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 语音面试名额（需求 5.6.3）：Redis ZSET 记占用，score 为释放时间。
 * 正常占用 score=Long.MAX_VALUE；断线后标记 3 分钟到期，超时释放。
 */
@Service
public class VoiceSlotService {

    private static final String KEY = "interview:voice:slots";
    private static final double PERMANENT = Long.MAX_VALUE;

    private final StringRedisTemplate redis;
    private final SysConfigReader configs;

    public VoiceSlotService(StringRedisTemplate redis, SysConfigReader configs) {
        this.redis = redis;
        this.configs = configs;
    }

    public int limit() {
        return configs.getInt("interview.voice_concurrency_limit");
    }

    public void evictExpired() {
        redis.opsForZSet().removeRangeByScore(KEY, 0, Instant.now().toEpochMilli());
    }

    public boolean acquire(String slotId) {
        evictExpired();
        Long size = redis.opsForZSet().zCard(KEY);
        if (size != null && size >= limit()) {
            return false;
        }
        return Boolean.TRUE.equals(redis.opsForZSet().add(KEY, slotId, PERMANENT));
    }

    public void release(String slotId) {
        redis.opsForZSet().remove(KEY, slotId);
    }

    /** 断线后名额保留 3 分钟（需求 5.6.4） */
    public void holdOnDisconnect(String slotId) {
        redis.opsForZSet().add(KEY, slotId, Instant.now().toEpochMilli() + 3 * 60 * 1000L);
    }
}
