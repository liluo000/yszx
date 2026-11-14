package com.yunshuzhixue.modules.wrongbook.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 错题本（需求 5.5，03.数据库设计.md 5.5）：一个学员同一道题只有一条，答对或手动移除后删除 */
@TableName("wrong_question")
public class WrongQuestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long practiceQuestionId;
    private Long courseId;
    private String questionType;
    private Integer wrongCount;
    /** 学员上次的答案，答对时原样保留（需求 5.5） */
    private String lastStudentAnswer;
    private LocalDateTime lastWrongAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getPracticeQuestionId() { return practiceQuestionId; }
    public void setPracticeQuestionId(Long practiceQuestionId) { this.practiceQuestionId = practiceQuestionId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public Integer getWrongCount() { return wrongCount; }
    public void setWrongCount(Integer wrongCount) { this.wrongCount = wrongCount; }
    public String getLastStudentAnswer() { return lastStudentAnswer; }
    public void setLastStudentAnswer(String lastStudentAnswer) { this.lastStudentAnswer = lastStudentAnswer; }
    public LocalDateTime getLastWrongAt() { return lastWrongAt; }
    public void setLastWrongAt(LocalDateTime lastWrongAt) { this.lastWrongAt = lastWrongAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
