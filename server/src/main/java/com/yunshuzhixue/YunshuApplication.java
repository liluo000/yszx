package com.yunshuzhixue;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 云枢智学后端启动类。
 *
 * <p>模块化单体：业务模块位于 {@code com.yunshuzhixue.modules} 下，一个模块一个包，
 * 模块划分见 docs/design/02.整体架构设计.md 第 4 章。</p>
 */
@SpringBootApplication
// 扫描标注 @Mapper 的接口：既有继承 BaseMapper 的表映射，也有学员筛选、看板汇总等只读查询的 Mapper
@MapperScan(basePackages = "com.yunshuzhixue.modules", annotationClass = org.apache.ibatis.annotations.Mapper.class)
@EnableScheduling
public class YunshuApplication {

    public static void main(String[] args) {
        SpringApplication.run(YunshuApplication.class, args);
    }
}
