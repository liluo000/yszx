package com.yunshuzhixue.modules.help.service;

import com.yunshuzhixue.modules.account.api.StudentDirectory;
import com.yunshuzhixue.modules.message.api.StudentMessageSender;
import com.yunshuzhixue.modules.sysconfig.api.AgreementPublishedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 协议发布通知（架构 4.3）：M03 发布"协议已发布"事件，M17 订阅后调用 M16 写入站内信。
 * M16 不反向依赖 M03，保持消息中心作为公共能力。
 */
@Component
public class AgreementPublishedListener {

    private final StudentDirectory students;
    private final StudentMessageSender messages;

    public AgreementPublishedListener(StudentDirectory students, StudentMessageSender messages) {
        this.students = students;
        this.messages = messages;
    }

    @EventListener
    public void on(AgreementPublishedEvent event) {
        String name = "user_agreement".equals(event.agreementType()) ? "《用户协议》" : "《隐私政策》";
        String title = name + "已更新至第 " + event.versionNo() + " 版";
        String content = name + "已更新，请前往个人中心查看最新正文并重新同意后继续使用。";
        for (Long studentId : students.activeStudentIds()) {
            messages.send(studentId, StudentMessageSender.AGREEMENT_UPDATED, title, content, null);
        }
    }
}
