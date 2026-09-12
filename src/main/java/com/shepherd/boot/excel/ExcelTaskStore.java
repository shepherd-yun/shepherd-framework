package com.shepherd.boot.excel;

import java.util.Optional;

/**
 * 任务存储 SPI。默认实现是 Redis（见 RedisExcelTaskStore）；
 * 下游需要持久化时声明自己的 Bean 即可覆盖——Starter 不往下游数据库建表。
 */
public interface ExcelTaskStore {

    void save(ExcelTask task);

    Optional<ExcelTask> find(String id);
}
