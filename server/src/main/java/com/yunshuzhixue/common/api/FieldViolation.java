package com.yunshuzhixue.common.api;

/**
 * 参数校验错误的一项，放在失败响应的 errors 中。
 */
public record FieldViolation(String field, String reason) {
}
