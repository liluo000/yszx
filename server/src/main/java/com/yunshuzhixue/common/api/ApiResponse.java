package com.yunshuzhixue.common.api;

import com.yunshuzhixue.common.web.RequestIds;

/**
 * 成功响应：{@code {code, message, data, request_id}}，见 04.API接口设计.md 2.2。
 */
public record ApiResponse<T>(String code, String message, T data, String requestId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data, RequestIds.current());
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }
}
