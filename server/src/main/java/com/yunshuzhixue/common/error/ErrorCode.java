package com.yunshuzhixue.common.error;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码，与 04.API接口设计.md 2.6 的错误码表一致。功能开发时按需补充。
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "参数校验失败"),
    OLD_PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST, "原密码错误"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "未登录或登录已失效"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "账号或密码错误"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "没有权限"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "后台账号已停用"),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "需要先修改密码"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在或无权访问"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "不支持的请求方法"),
    LOGIN_LOCKED(HttpStatus.LOCKED, "密码错误次数过多，已锁定 15 分钟"),
    ACCOUNT_ALREADY_EXISTS(HttpStatus.CONFLICT, "后台账号已存在"),
    CANNOT_DISABLE_SELF(HttpStatus.CONFLICT, "不能停用自己的账号"),
    LAST_SUPER_ADMIN(HttpStatus.CONFLICT, "必须保留至少 1 个启用的超级管理员"),
    SENSITIVE_WORD_EXISTS(HttpStatus.CONFLICT, "敏感词已存在"),
    PLATFORM_NAME_EXISTS(HttpStatus.CONFLICT, "平台名称已存在"),
    PLATFORM_DOMAIN_EXISTS(HttpStatus.CONFLICT, "域名已被其他平台使用"),
    VIDEO_DOMAIN_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "视频链接域名不属于所选平台"),
    COURSE_ALREADY_IN_ROUTE(HttpStatus.CONFLICT, "课程已被同一路线的其他阶段引用"),
    ROUTE_PUBLISH_INVALID(HttpStatus.CONFLICT, "路线不满足上架条件"),
    FEEDBACK_ALREADY_HANDLED(HttpStatus.CONFLICT, "反馈已被处理"),
    STUDENT_STATE_INVALID(HttpStatus.CONFLICT, "学员当前状态不允许此操作"),
    SUBJECT_NAME_EXISTS(HttpStatus.CONFLICT, "学科名称已存在"),
    CONTENT_IN_USE(HttpStatus.CONFLICT, "内容已被使用，不能删除"),
    FILE_INVALID(HttpStatus.BAD_REQUEST, "文件格式、大小或行数不符合要求"),
    EXPORT_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "导出数量超过上限，请缩小筛选范围"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
