package com.shepherd.boot.mybatis;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shepherd.boot.web.code.CommonResultCode;
import com.shepherd.boot.web.dto.PageQuery;
import com.shepherd.boot.web.exception.BizException;
import com.shepherd.boot.web.vo.PageResult;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * PageQuery / PageResult 与 MyBatis-Plus 分页类型之间的桥接。
 * 刻意放在 mybatis 包：让 web 包不依赖 optional 的 MyBatis-Plus。
 */
public final class MpPages {

    private MpPages() {
    }

    /** 只搬分页参数，不带排序。 */
    public static <T> Page<T> of(PageQuery query) {
        return new Page<>(query.getPageNum(), query.getPageSize());
    }

    /**
     * 带排序白名单。sortField 必须命中 allowedFields（驼峰形式），
     * 命中后转下划线拼进 OrderItem；未命中抛 BizException(BAD_REQUEST)。
     * sortField 为 null 或空白时视为不排序，不报错。
     */
    public static <T> Page<T> of(PageQuery query, Set<String> allowedFields) {
        Page<T> page = of(query);
        String field = query.getSortField();
        if (field == null || field.isBlank()) {
            return page;
        }
        if (!allowedFields.contains(field)) {
            throw new BizException(CommonResultCode.BAD_REQUEST, "不支持的排序字段：" + field);
        }
        String column = camelToSnake(field);
        boolean asc = !"desc".equalsIgnoreCase(query.getSortOrder());
        page.addOrder(asc ? OrderItem.asc(column) : OrderItem.desc(column));
        return page;
    }

    /** IPage → PageResult。 */
    public static <E, T> PageResult<T> toResult(IPage<E> page, Function<E, T> mapper) {
        List<T> records = page.getRecords().stream().map(mapper).toList();
        return PageResult.of(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), records);
    }

    /**
     * 自己实现而不用 MP 的 StringUtils.camelToUnderline：
     * 少一个对第三方内部工具类 API 稳定性的依赖，逻辑本身只有几行。
     */
    private static String camelToSnake(String camel) {
        StringBuilder sb = new StringBuilder(camel.length() + 4);
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
