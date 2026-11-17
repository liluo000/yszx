package com.yunshuzhixue.modules.account.service;

import com.yunshuzhixue.modules.account.api.StudentStp;
import com.yunshuzhixue.modules.content.api.CurrentStudentProvider;
import org.springframework.stereotype.Component;

/**
 * 当前登录学员（实现 M05 定义的扩展接口）。
 *
 * <p>登录态归 M01，M05 需要按学员取课程状态时通过该接口读取，避免 content 直接依赖 account。</p>
 */
@Component
public class StudentContext implements CurrentStudentProvider {

    @Override
    public Long currentStudentId() {
        try {
            return StudentStp.LOGIC.isLogin() ? StudentStp.LOGIC.getLoginIdAsLong() : null;
        } catch (RuntimeException e) {
            // 非 Web 上下文（定时任务、单元测试）没有请求，按游客处理
            return null;
        }
    }
}
