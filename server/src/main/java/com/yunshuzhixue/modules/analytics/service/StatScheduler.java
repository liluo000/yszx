package com.yunshuzhixue.modules.analytics.service;

import com.yunshuzhixue.common.time.BeijingDates;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 看板汇总的定时任务（需求 6.8 数据更新频率，02.整体架构设计.md 5.x 后台任务）：
 * 每小时汇总当天；每天凌晨汇总前一天，并补算缺失的日期、重算近 7 天的留存。启动时也执行一次。
 * 用 Redis 锁保证多个实例时只有一个在汇总。
 */
@Component
public class StatScheduler {

    private static final Logger log = LoggerFactory.getLogger(StatScheduler.class);
    private static final String LOCK = "analytics:stat-aggregation-lock";
    /** 补算的最长天数，与看板可查询的跨度一致 */
    private static final int MAX_BACKFILL_DAYS = 366;

    private final StatAggregationService aggregation;
    private final StringRedisTemplate redis;

    public StatScheduler(StatAggregationService aggregation, StringRedisTemplate redis) {
        this.aggregation = aggregation;
        this.redis = redis;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        Thread thread = new Thread(() -> {
            daily();
            hourly();
        }, "stat-startup");
        thread.setDaemon(true);
        thread.start();
    }

    @Scheduled(cron = "0 5 * * * *", zone = "Asia/Shanghai")
    public void hourly() {
        withLock(() -> aggregation.aggregate(LocalDate.now(BeijingDates.ZONE), true));
    }

    @Scheduled(cron = "0 20 0 * * *", zone = "Asia/Shanghai")
    public void daily() {
        withLock(() -> {
            LocalDate today = LocalDate.now(BeijingDates.ZONE);
            LocalDate yesterday = today.minusDays(1);
            LocalDate first = aggregation.firstRegistrationDate();
            if (first == null) {
                aggregation.aggregate(yesterday, true);
                return;
            }
            LocalDate from = first.isBefore(today.minusDays(MAX_BACKFILL_DAYS)) ? today.minusDays(MAX_BACKFILL_DAYS) : first;
            Set<LocalDate> done = new HashSet<>(aggregation.aggregatedDates(from, yesterday));
            for (LocalDate d = from; !d.isAfter(yesterday); d = d.plusDays(1)) {
                // 缺失的日期补算；近 8 天重算以补上次日、7 日留存
                if (!done.contains(d) || !d.isBefore(today.minusDays(8))) {
                    aggregation.aggregate(d, !done.contains(d) || d.equals(yesterday));
                }
            }
        });
    }

    private void withLock(Runnable task) {
        Boolean locked = redis.opsForValue().setIfAbsent(LOCK, "1", Duration.ofMinutes(30));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            task.run();
        } catch (RuntimeException e) {
            log.error("看板数据汇总失败", e);
        } finally {
            redis.delete(LOCK);
        }
    }
}
