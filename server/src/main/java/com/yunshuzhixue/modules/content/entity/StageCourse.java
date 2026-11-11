package com.yunshuzhixue.modules.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 阶段引用的课程（需求 6.3）。learning_route_id 冗余存放，用唯一索引保证同一路线内课程只出现一次 */
@TableName("stage_course")
public class StageCourse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long learningRouteId;
    private Long routeStageId;
    private Long courseId;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getLearningRouteId() { return learningRouteId; }
    public void setLearningRouteId(Long learningRouteId) { this.learningRouteId = learningRouteId; }
    public Long getRouteStageId() { return routeStageId; }
    public void setRouteStageId(Long routeStageId) { this.routeStageId = routeStageId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
