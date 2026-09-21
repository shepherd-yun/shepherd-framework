package com.shepherd.boot.serialno;

import java.util.List;

/**
 * 单号生成器：按 {@link SerialNoRule} 产出「前缀 + 日期段 + 补零序号」的单号。
 *
 * <p><b>唯一性前提</b>：计数器在 Redis 里，唯一性以 Redis 持久化为前提。Redis 丢掉当天计数器
 * （重启无持久化 / FLUSHDB / 主从切换丢写）会让序号从 1 重数、发出重复单号——
 * 业务表必须对单号建唯一索引。
 *
 * <p><b>序号连续不等于无空洞</b>：{@link #nextBatch} 未用完的号、以及「脚本已执行成功但响应丢失」
 * 后的重试，都会留下空洞。
 */
public interface SerialNoGenerator {

    /** 取一个单号。 */
    String next(SerialNoRule rule);

    /**
     * 一次取 count 个连续单号（单次 Redis 往返）。
     *
     * @param count 必须 >= 1
     */
    List<String> nextBatch(SerialNoRule rule, int count);
}
