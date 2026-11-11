package com.yunshuzhixue.modules.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 面试题目（需求 6.5） */
@TableName("interview_question")
public class InterviewQuestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private String stem;
    private String referenceAnswer;
    private String scoringPoints;
    private String followupDirection;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getStem() { return stem; }
    public void setStem(String stem) { this.stem = stem; }
    public String getReferenceAnswer() { return referenceAnswer; }
    public void setReferenceAnswer(String referenceAnswer) { this.referenceAnswer = referenceAnswer; }
    public String getScoringPoints() { return scoringPoints; }
    public void setScoringPoints(String scoringPoints) { this.scoringPoints = scoringPoints; }
    public String getFollowupDirection() { return followupDirection; }
    public void setFollowupDirection(String followupDirection) { this.followupDirection = followupDirection; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
