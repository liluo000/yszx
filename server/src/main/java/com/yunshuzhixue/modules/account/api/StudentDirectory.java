package com.yunshuzhixue.modules.account.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 学员的只读查询，供其他模块显示提交人等信息使用 */
public interface StudentDirectory {

    /** 学员昵称；已注销（记录已删除）的学员不返回，调用方显示"已注销用户" */
    Map<Long, String> nicknames(Collection<Long> studentIds);

    /** 所有未注销学员的 ID，用于协议发布时的全量站内信通知 */
    List<Long> activeStudentIds();
}
