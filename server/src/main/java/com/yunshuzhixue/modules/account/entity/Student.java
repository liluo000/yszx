package com.yunshuzhixue.modules.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 学员（需求 5.1）。字段与 03.数据库设计.md 的 student 表一致，phone_last4 为生成列不映射 */
@TableName("student")
public class Student {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String phone;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String education;
    private Long jobIntentLearningRouteId;
    private String jobIntentName;
    private String workYears;
    private String status;
    private String banReason;
    private LocalDateTime bannedAt;
    private LocalDateTime cancellationRequestedAt;
    private LocalDateTime cancellationEffectiveAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }
    public Long getJobIntentLearningRouteId() { return jobIntentLearningRouteId; }
    public void setJobIntentLearningRouteId(Long id) { this.jobIntentLearningRouteId = id; }
    public String getJobIntentName() { return jobIntentName; }
    public void setJobIntentName(String name) { this.jobIntentName = name; }
    public String getWorkYears() { return workYears; }
    public void setWorkYears(String workYears) { this.workYears = workYears; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBanReason() { return banReason; }
    public void setBanReason(String banReason) { this.banReason = banReason; }
    public LocalDateTime getBannedAt() { return bannedAt; }
    public void setBannedAt(LocalDateTime bannedAt) { this.bannedAt = bannedAt; }
    public LocalDateTime getCancellationRequestedAt() { return cancellationRequestedAt; }
    public void setCancellationRequestedAt(LocalDateTime at) { this.cancellationRequestedAt = at; }
    public LocalDateTime getCancellationEffectiveAt() { return cancellationEffectiveAt; }
    public void setCancellationEffectiveAt(LocalDateTime at) { this.cancellationEffectiveAt = at; }
    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime at) { this.lastActiveAt = at; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime at) { this.createdAt = at; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime at) { this.updatedAt = at; }
}
