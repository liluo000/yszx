package com.yunshuzhixue.infra.ai;

/** infra 内统一的调用异常，由各适配器转换为对应业务契约的 UnavailableException */
public class DashScopeException extends RuntimeException {

    public DashScopeException(String message) {
        super(message);
    }

    public DashScopeException(String message, Throwable cause) {
        super(message, cause);
    }
}
