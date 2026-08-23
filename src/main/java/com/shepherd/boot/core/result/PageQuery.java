package com.shepherd.boot.core.result;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class PageQuery {
    private long pageNum = 1;
    private long pageSize = 10;
    /** 排序字段，需由 service 用白名单校验，禁止直接透传 */
    private @Nullable String sortField;
    /** asc / desc */
    private @Nullable String sortOrder;

    public <T> Page<T> toMpPage() {
        return new Page<>(pageNum, pageSize);
    }
}
