package com.yunshuzhixue.common.api;

import java.util.List;

/**
 * 失败响应：{@code {code, message, errors, request_id}}，见 04.API接口设计.md 2.2。
 */
public record ApiErrorResponse(String code, String message, List<Object> errors, String requestId) {
}
