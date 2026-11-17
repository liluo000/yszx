package com.yunshuzhixue.modules.content.api;

/**
 * 当前登录学员（02.整体架构设计.md 4.3 的扩展接口模式）。
 *
 * <p>课程状态需要按「当前登录学员」取，但登录态归 M01：M05 只定义接口，
 * 由 M01 实现，避免 content 依赖 account 而与 account → content.api 形成循环。</p>
 */
public interface CurrentStudentProvider {

    /** 已登录返回学员 ID，游客返回 null */
    Long currentStudentId();
}
