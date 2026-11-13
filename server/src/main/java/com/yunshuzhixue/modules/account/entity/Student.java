package com.yunshuzhixue.modules.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 学员（需求 5.1）。学员账号模块的其他功能尚未开发，目前只映射后台使用的字段，开发 M01 时补全 */
@TableName("student")
public class Student {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String phone;
    private String nickname;
    private String status;
    private String banReason;
    private LocalDateTime bannedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBanReason() { return banReason; }
    public void setBanReason(String banReason) { this.banReason = banReason; }
    public LocalDateTime getBannedAt() { return bannedAt; }
    public void setBannedAt(LocalDateTime bannedAt) { this.bannedAt = bannedAt; }
}
