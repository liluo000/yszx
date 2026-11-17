package com.yunshuzhixue.modules.account.config;

import com.yunshuzhixue.common.agreement.AgreementConsentGateway;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.modules.account.api.StudentStp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 协议更新拦截（需求 5.1.2，04.API接口设计.md 的错误码 AGREEMENT_CONSENT_REQUIRED）。
 *
 * <p>已登录学员存在未同意的最新协议时，除「看本人信息 / 同意协议 / 看协议正文 / 退出登录 /
 * 登录注册相关」外一律返回 403，前端据此弹窗要求同意。</p>
 */
@Component
public class StudentAgreementInterceptor implements HandlerInterceptor {

    /** 未同意协议时仍可访问的路径：登录态自检、协议正文、同意协议、退出、登录注册与验证码 */
    private static final List<String> ALLOWED = List.of(
            "/api/v1/student/me",
            "/api/v1/student/agreements/",
            "/api/v1/student/agreement-consents",
            "/api/v1/student/sessions",
            "/api/v1/student/captchas",
            "/api/v1/student/sms-codes",
            "/api/v1/student/registrations",
            "/api/v1/student/password-resets",
            "/api/v1/student/help");

    private final AgreementConsentGateway consents;

    public StudentAgreementInterceptor(AgreementConsentGateway consents) {
        this.consents = consents;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !StudentStp.LOGIC.isLogin()) {
            return true;
        }
        String uri = request.getRequestURI();
        if (ALLOWED.stream().anyMatch(uri::startsWith)) {
            return true;
        }
        if (!consents.pending(StudentStp.LOGIC.getLoginIdAsLong()).isEmpty()) {
            throw new BusinessException(ErrorCode.AGREEMENT_CONSENT_REQUIRED);
        }
        return true;
    }
}
