package com.yunshuzhixue.modules.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 学员（需求 5.1）。学员账号模块尚未开发，目前只映射后台显示需要的字段，开发 M01 时补全 */
@TableName("student")
public class Student {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String nickname;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
