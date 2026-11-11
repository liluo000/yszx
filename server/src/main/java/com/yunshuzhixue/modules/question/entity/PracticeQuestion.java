package com.yunshuzhixue.modules.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 刷题题目（需求 6.4）。options、correct_answer 为 JSON 文本，结构见 03.数据库设计.md 5.2 */
@TableName("practice_question")
public class PracticeQuestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private String questionType;
    private String stem;
    private String options;
    private String correctAnswer;
    private String referenceAnswer;
    private String scoringPoints;
    private String analysis;
    private Integer sortNo;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public String getStem() { return stem; }
    public void setStem(String stem) { this.stem = stem; }
    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public String getReferenceAnswer() { return referenceAnswer; }
    public void setReferenceAnswer(String referenceAnswer) { this.referenceAnswer = referenceAnswer; }
    public String getScoringPoints() { return scoringPoints; }
    public void setScoringPoints(String scoringPoints) { this.scoringPoints = scoringPoints; }
    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
