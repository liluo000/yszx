package com.yunshuzhixue.common.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 时间统一按 UTC 存储（03.数据库设计.md 第 1 章），接口按 ISO 8601 UTC 返回。
 */
public final class UtcTime {

    private UtcTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public static Instant toInstant(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
    }
}
