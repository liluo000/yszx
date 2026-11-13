package com.yunshuzhixue.modules.account.api;

/** 后台对学员账号的操作（需求 6.7.2、6.7.3），由学员账号模块执行，其他模块不直接写学员表 */
public interface StudentAccountAdmin {

    /** 学员的完整手机号；学员不存在时抛出 NOT_FOUND */
    String phoneOf(Long studentId);

    /** 封禁：只有状态正常的学员可以封禁，注销中的账号不能封禁；返回昵称用于日志 */
    String ban(Long studentId, String reason);

    /** 解封：只有被封禁的学员可以解封；返回昵称用于日志 */
    String unban(Long studentId);
}
