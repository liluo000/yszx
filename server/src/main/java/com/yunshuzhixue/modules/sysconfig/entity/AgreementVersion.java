package com.yunshuzhixue.modules.sysconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("agreement_version")
public class AgreementVersion {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String agreementType;
    private Integer versionNo;
    private String content;
    private Long publishedBySysUserId;
    private LocalDateTime publishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAgreementType() { return agreementType; }
    public void setAgreementType(String agreementType) { this.agreementType = agreementType; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getPublishedBySysUserId() { return publishedBySysUserId; }
    public void setPublishedBySysUserId(Long publishedBySysUserId) { this.publishedBySysUserId = publishedBySysUserId; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }}
