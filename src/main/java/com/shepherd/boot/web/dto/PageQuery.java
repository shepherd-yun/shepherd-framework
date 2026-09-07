package com.shepherd.boot.web.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

/**
 * 通用分页入参。纯 POJO，不依赖任何持久层类型。
 * 转 MyBatis-Plus Page 见 {@code com.shepherd.boot.mybatis.MpPages}。
 */
@Data
public class PageQuery {
    private long pageNum = 1;
    private long pageSize = 10;

    /** 排序字段（驼峰）。必须经 MpPages 的白名单校验，禁止直接透传到 SQL。 */
    private @Nullable String sortField;

    /** asc / desc，大小写不敏感；非 desc 一律按 asc 处理。 */
    private @Nullable String sortOrder;
}
