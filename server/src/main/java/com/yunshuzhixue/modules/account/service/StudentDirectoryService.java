package com.yunshuzhixue.modules.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunshuzhixue.modules.account.api.StudentDirectory;
import com.yunshuzhixue.modules.account.entity.Student;
import com.yunshuzhixue.modules.account.mapper.StudentMapper;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 学员昵称查询，供后台显示提交人等使用 */
@Service
public class StudentDirectoryService implements StudentDirectory {

    private final StudentMapper mapper;

    public StudentDirectoryService(StudentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Map<Long, String> nicknames(Collection<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<Student>()
                        .select(Student::getId, Student::getNickname).in(Student::getId, studentIds))
                .stream().collect(Collectors.toMap(Student::getId, Student::getNickname));
    }
}
