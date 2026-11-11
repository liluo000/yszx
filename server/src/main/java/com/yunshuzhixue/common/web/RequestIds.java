package com.yunshuzhixue.common.web;

import org.slf4j.MDC;

/**
 * 当前请求的请求标识，写入日志并随响应返回，用于排查问题。
 */
public final class RequestIds {

    public static final String MDC_KEY = "requestId";
    public static final String HEADER = "X-Request-Id";

    private RequestIds() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
