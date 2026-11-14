package com.yunshuzhixue.modules.wrongbook.api;

/** 错题本的只读计数，供学员端首页展示「待重做错题」（需求 5.2，S-PTL-01） */
public interface WrongQuestionCounter {

    /** 待重做错题数：只算题目仍上架的错题，已下架的题不能重做，不计入 */
    long countRedoable(long studentId);
}
