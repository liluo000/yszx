package com.yunshuzhixue.modules.help.service;

import com.yunshuzhixue.modules.account.api.StudentRegisteredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 注册时记录协议同意（需求 5.1.1）：订阅 M01 的“学员已注册”事件 */
@Component
public class StudentRegisteredListener {

    private final AgreementConsentService consents;

    public StudentRegisteredListener(AgreementConsentService consents) {
        this.consents = consents;
    }

    @EventListener
    @Transactional
    public void onStudentRegistered(StudentRegisteredEvent event) {
        consents.recordRegistration(event.studentId(), event.agreements().stream()
                .map(item -> new AgreementConsentService.Consent(item.agreementType(), item.versionNo()))
                .toList());
    }
}
