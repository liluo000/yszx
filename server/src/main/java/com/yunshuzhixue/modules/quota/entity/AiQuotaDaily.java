package com.yunshuzhixue.modules.quota.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 学员每日 AI 次数使用量（需求 7.1，S-QTA-01） */
@TableName("ai_quota_daily")
public class AiQuotaDaily {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private LocalDate quotaDate;
    private Integer interviewUsed;
    private Integer scoringUsed;
    private Integer scoringReserved;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public LocalDate getQuotaDate() { return quotaDate; }
    public void setQuotaDate(LocalDate quotaDate) { this.quotaDate = quotaDate; }
    public Integer getInterviewUsed() { return interviewUsed; }
    public void setInterviewUsed(Integer interviewUsed) { this.interviewUsed = interviewUsed; }
    public Integer getScoringUsed() { return scoringUsed; }
    public void setScoringUsed(Integer scoringUsed) { this.scoringUsed = scoringUsed; }
    public Integer getScoringReserved() { return scoringReserved; }
    public void setScoringReserved(Integer scoringReserved) { this.scoringReserved = scoringReserved; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
