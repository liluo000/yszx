package com.yunshuzhixue.modules.message.api;

/** 写入站内信（需求 5.10），只发站内信，不发短信 */
public interface StudentMessageSender {

    String FEEDBACK_RESULT = "feedback_result";
    String AGREEMENT_UPDATED = "agreement_updated";
    String INTERVIEW_REPORT_GENERATED = "interview_report_generated";

    void send(Long studentId, String messageType, String title, String content, Long businessId);
}
