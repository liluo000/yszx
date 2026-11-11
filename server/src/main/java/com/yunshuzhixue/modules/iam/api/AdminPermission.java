package com.yunshuzhixue.modules.iam.api;

import java.util.List;

/**
 * 后台权限码，前端用于控制菜单，后端用于接口鉴权。
 */
public final class AdminPermission {

    public static final String WORKBENCH = "workbench";
    public static final String SUBJECT = "subject";
    public static final String COURSE = "course";
    public static final String ROUTE = "route";
    public static final String PRACTICE_QUESTION = "practice_question";
    public static final String INTERVIEW_QUESTION = "interview_question";
    public static final String FEEDBACK = "feedback";
    public static final String STUDENT = "student";
    public static final String DASHBOARD = "dashboard";
    public static final String SYS_USER = "sys_user";
    public static final String ROLE = "role";
    public static final String OPERATION_LOG = "operation_log";
    public static final String CONFIG = "config";

    public static final List<String> ALL = List.of(WORKBENCH, SUBJECT, COURSE, ROUTE, PRACTICE_QUESTION,
            INTERVIEW_QUESTION, FEEDBACK, STUDENT, DASHBOARD, SYS_USER, ROLE, OPERATION_LOG, CONFIG);

    private AdminPermission() {
    }
}
