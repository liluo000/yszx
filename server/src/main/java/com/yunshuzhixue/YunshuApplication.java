package com.yunshuzhixue;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 云枢智学后端启动类。
 *
 * <p>模块化单体：业务模块位于 {@code com.yunshuzhixue.modules} 下，一个模块一个包，
 * 模块划分见 docs/design/02.整体架构设计.md 第 4 章。</p>
 */
@SpringBootApplication
@MapperScan(basePackages = "com.yunshuzhixue.modules", markerInterface = com.baomidou.mybatisplus.core.mapper.BaseMapper.class)
public class YunshuApplication {

    public static void main(String[] args) {
        SpringApplication.run(YunshuApplication.class, args);
    }
}
