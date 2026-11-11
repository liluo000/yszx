package com.yunshuzhixue.modules.feedback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 题目纠错与视频失效反馈（需求 5.4.5、5.3、6.6） */
@TableName("feedback")
public class Feedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String feedbackType;
    private Long studentId;
    private Long courseId;
    private Long practiceQuestionId;
    private String errorType;
    private String description;
    private String videoUrl;
    private String status;
    private String handlingNote;
    private Long handledBySysUserId;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFeedbackType() { return feedbackType; }
    public void setFeedbackType(String feedbackType) { this.feedbackType = feedbackType; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getPracticeQuestionId() { return practiceQuestionId; }
    public void setPracticeQuestionId(Long practiceQuestionId) { this.practiceQuestionId = practiceQuestionId; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHandlingNote() { return handlingNote; }
    public void setHandlingNote(String handlingNote) { this.handlingNote = handlingNote; }
    public Long getHandledBySysUserId() { return handledBySysUserId; }
    public void setHandledBySysUserId(Long handledBySysUserId) { this.handledBySysUserId = handledBySysUserId; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
