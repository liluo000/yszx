package com.yunshuzhixue.modules.studentops.mapper;

import java.time.LocalDateTime;

/** 学员列表的一行（只读查询结果），含学习路线数和面试场次 */
public class StudentRow {
    private Long id;
    private String nickname;
    private String phone;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    private String education;
    private String jobIntentName;
    private String workYears;
    private Long learnedRouteCount;
    private Long interviewCount;
    private String status;
    private String banReason;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }
    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }
    public String getJobIntentName() { return jobIntentName; }
    public void setJobIntentName(String jobIntentName) { this.jobIntentName = jobIntentName; }
    public String getWorkYears() { return workYears; }
    public void setWorkYears(String workYears) { this.workYears = workYears; }
    public Long getLearnedRouteCount() { return learnedRouteCount; }
    public void setLearnedRouteCount(Long learnedRouteCount) { this.learnedRouteCount = learnedRouteCount; }
    public Long getInterviewCount() { return interviewCount; }
    public void setInterviewCount(Long interviewCount) { this.interviewCount = interviewCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBanReason() { return banReason; }
    public void setBanReason(String banReason) { this.banReason = banReason; }
}
