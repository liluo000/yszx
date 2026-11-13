package com.yunshuzhixue.modules.audit.api;

/** 操作日志中的模块名称，与管理后台的菜单名称一致，用于按模块筛选 */
public final class AuditModules {

    public static final String ACCOUNT = "账号";
    public static final String SUBJECT = "学科管理";
    public static final String COURSE = "课程库";
    public static final String ROUTE = "学习路线";
    public static final String PRACTICE_QUESTION = "刷题题库";
    public static final String INTERVIEW_QUESTION = "面试题库";
    public static final String FEEDBACK = "纠错与反馈";
    public static final String STUDENT = "学员管理";
    public static final String DASHBOARD = "数据看板";
    public static final String SYS_USER = "系统用户";
    public static final String SYS_CONFIG = "系统配置";
    public static final String OPERATION_LOG = "操作日志";

    private AuditModules() {
    }
}
