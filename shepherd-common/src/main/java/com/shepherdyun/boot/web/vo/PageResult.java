package com.shepherdyun.boot.web.vo;

import lombok.Getter;

import java.util.List;

@Getter
public class PageResult<T> {
    private final long total;
    private final long pages;
    private final long pageNum;
    private final long pageSize;
    private final List<T> records;

    private PageResult(long total, long pages, long pageNum, long pageSize, List<T> records) {
        this.total = total;
        this.pages = pages;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.records = records;
    }

    /**
     * 纯工厂：不依赖任何持久层类型。
     * MyBatis-Plus 的 IPage 转换见 {@code com.shepherdyun.boot.mybatis.MpPages#toResult}。
     */
    public static <T> PageResult<T> of(long total, long pages, long pageNum, long pageSize, List<T> records) {
        return new PageResult<>(total, pages, pageNum, pageSize, records);
    }
}
