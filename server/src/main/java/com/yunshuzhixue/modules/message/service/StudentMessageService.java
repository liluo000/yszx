package com.yunshuzhixue.modules.message.service;

import com.yunshuzhixue.modules.message.api.StudentMessageSender;
import com.yunshuzhixue.modules.message.entity.StudentMessage;
import com.yunshuzhixue.modules.message.mapper.StudentMessageMapper;
import org.springframework.stereotype.Service;

/** 站内信写入。消息中心（学员端未读数、已读）在开发学员端时补充 */
@Service
public class StudentMessageService implements StudentMessageSender {

    private final StudentMessageMapper mapper;

    public StudentMessageService(StudentMessageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void send(Long studentId, String messageType, String title, String content, Long businessId) {
        StudentMessage message = new StudentMessage();
        message.setStudentId(studentId);
        message.setMessageType(messageType);
        message.setTitle(title.length() > 100 ? title.substring(0, 100) : title);
        message.setContent(content.length() > 2000 ? content.substring(0, 2000) : content);
        message.setBusinessId(businessId);
        message.setIsRead(false);
        mapper.insert(message);
    }
}
