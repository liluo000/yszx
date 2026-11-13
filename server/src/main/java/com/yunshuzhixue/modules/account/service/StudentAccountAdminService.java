package com.yunshuzhixue.modules.account.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yunshuzhixue.common.error.BusinessException;
import com.yunshuzhixue.common.error.ErrorCode;
import com.yunshuzhixue.common.time.UtcTime;
import com.yunshuzhixue.modules.account.api.StudentAccountAdmin;
import com.yunshuzhixue.modules.account.entity.Student;
import com.yunshuzhixue.modules.account.mapper.StudentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 封禁、解封、读取完整手机号。封禁后"所有设备立即退出"和"进行中的考试、面试不能继续"
 * 在学员端登录和考试、面试模块开发时按 status 判断实现。
 */
@Service
public class StudentAccountAdminService implements StudentAccountAdmin {

    static final String NORMAL = "normal";
    static final String BANNED = "banned";

    private final StudentMapper mapper;

    public StudentAccountAdminService(StudentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String phoneOf(Long studentId) {
        return require(studentId).getPhone();
    }

    @Override
    @Transactional
    public String ban(Long studentId, String reason) {
        Student student = require(studentId);
        if (!NORMAL.equals(student.getStatus())) {
            throw new BusinessException(ErrorCode.STUDENT_STATE_INVALID,
                    BANNED.equals(student.getStatus()) ? "学员已被封禁" : "注销中的账号不能封禁");
        }
        mapper.update(null, new LambdaUpdateWrapper<Student>().eq(Student::getId, studentId).eq(Student::getStatus, NORMAL)
                .set(Student::getStatus, BANNED).set(Student::getBanReason, reason).set(Student::getBannedAt, UtcTime.now()));
        return student.getNickname();
    }

    @Override
    @Transactional
    public String unban(Long studentId) {
        Student student = require(studentId);
        if (!BANNED.equals(student.getStatus())) {
            throw new BusinessException(ErrorCode.STUDENT_STATE_INVALID, "学员未被封禁");
        }
        mapper.update(null, new LambdaUpdateWrapper<Student>().eq(Student::getId, studentId).eq(Student::getStatus, BANNED)
                .set(Student::getStatus, NORMAL).set(Student::getBanReason, null).set(Student::getBannedAt, null));
        return student.getNickname();
    }

    private Student require(Long id) {
        Student student = mapper.selectById(id);
        if (student == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return student;
    }
}
