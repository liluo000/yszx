package com.yunshuzhixue.modules.account.api;

/**
 * 学员活跃记录（02.整体架构设计.md 4.1）：活跃记录与最近活跃时间归 M01，
 * M08 学习、M09 刷题、M11 面试调用本接口记录，不直接写 M01 的表。
 */
public interface StudentActivityRecorder {

    /** 记录一次学习行为：写当日活跃，并更新最近活跃时间 */
    void record(long studentId);
}
