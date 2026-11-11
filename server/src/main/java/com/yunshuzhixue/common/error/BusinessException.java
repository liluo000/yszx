package com.yunshuzhixue.common.error;

import java.util.List;

/**
 * 业务异常，由全局异常处理器转换为统一的失败响应。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<Object> errors;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), List.of());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public BusinessException(ErrorCode errorCode, String message, List<Object> errors) {
        super(message);
        this.errorCode = errorCode;
        this.errors = errors;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<Object> getErrors() {
        return errors;
    }
}
