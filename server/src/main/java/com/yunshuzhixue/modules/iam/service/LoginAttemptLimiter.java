package com.yunshuzhixue.modules.iam.service;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 后台密码连续输错 5 次锁定 15 分钟（需求 6.9）。计数保存在 Redis，登录成功或重置密码后清零。
 * 按账号计数，账号不存在时同样计数，避免通过提示差异判断账号是否存在。
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;

    public LoginAttemptLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean isLocked(String account) {
        String value = redis.opsForValue().get(key(account));
        return value != null && Integer.parseInt(value) >= MAX_FAILURES;
    }

    /** 记录一次失败，返回剩余可尝试次数，0 表示已锁定 */
    public int recordFailure(String account) {
        String key = key(account);
        Long failures = redis.opsForValue().increment(key);
        redis.expire(key, LOCK_DURATION);
        return Math.max(0, MAX_FAILURES - (failures == null ? 0 : failures.intValue()));
    }

    public void reset(String account) {
        redis.delete(key(account));
    }

    private static String key(String account) {
        return "iam:admin:login-failures:" + account;
    }
}
