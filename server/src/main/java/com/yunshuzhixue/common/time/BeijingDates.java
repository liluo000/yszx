package com.yunshuzhixue.common.time;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 接口中的日期按北京时间理解，数据库按 UTC 存储，查询时换算（03.数据库设计.md 第 1 章） */
public final class BeijingDates {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private BeijingDates() {
    }

    /** 北京时间某天 00:00 对应的 UTC 时间 */
    public static LocalDateTime startOfDayUtc(LocalDate date) {
        return date.atStartOfDay(ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** 北京时间某天结束（下一天 00:00）对应的 UTC 时间，作为不含边界的上限 */
    public static LocalDateTime endOfDayUtc(LocalDate date) {
        return startOfDayUtc(date.plusDays(1));
    }
}
