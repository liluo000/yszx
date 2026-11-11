package com.yunshuzhixue.modules.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunshuzhixue.modules.audit.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
