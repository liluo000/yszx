package com.yunshuzhixue.common.api;

/** 分页参数：page 从 1 开始，page_size 默认 20、最大 100 */
public record PageQuery(int page, int pageSize) {

    public static PageQuery of(Integer page, Integer pageSize) {
        int p = page == null || page < 1 ? 1 : page;
        int s = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        return new PageQuery(p, s);
    }

    public long offset() {
        return (long) (page - 1) * pageSize;
    }

    /** MyBatis-Plus 查询末尾追加的分页子句 */
    public String limitClause() {
        return "LIMIT " + pageSize + " OFFSET " + offset();
    }
}
