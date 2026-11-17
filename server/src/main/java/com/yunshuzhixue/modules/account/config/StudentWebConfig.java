package com.yunshuzhixue.modules.account.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 学员端 Web 配置：统一挂协议同意拦截器 */
@Configuration
public class StudentWebConfig implements WebMvcConfigurer {

    private final StudentAgreementInterceptor agreementInterceptor;

    public StudentWebConfig(StudentAgreementInterceptor agreementInterceptor) {
        this.agreementInterceptor = agreementInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(agreementInterceptor).addPathPatterns("/api/v1/student/**");
    }
}
