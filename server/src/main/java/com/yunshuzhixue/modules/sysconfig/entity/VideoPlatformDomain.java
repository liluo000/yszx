package com.yunshuzhixue.modules.sysconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("video_platform_domain")
public class VideoPlatformDomain {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long videoPlatformId;
    private String domain;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVideoPlatformId() { return videoPlatformId; }
    public void setVideoPlatformId(Long videoPlatformId) { this.videoPlatformId = videoPlatformId; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }}
