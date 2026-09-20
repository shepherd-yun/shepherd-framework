package com.shepherd.boot.mybatis;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shepherd.boot.web.code.CommonResultCode;
import com.shepherd.boot.web.dto.PageQuery;
import com.shepherd.boot.web.exception.BizException;
import com.shepherd.boot.web.vo.PageResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MpPagesTest {

    private static PageQuery query(long num, long size, String field, String order) {
        PageQuery q = new PageQuery();
        q.setPageNum(num);
        q.setPageSize(size);
        q.setSortField(field);
        q.setSortOrder(order);
        return q;
    }

    @Test
    void of_copies_paging_meta_without_sorting() {
        Page<String> page = MpPages.of(query(3, 25, null, null));

        assertThat(page.getCurrent()).isEqualTo(3);
        assertThat(page.getSize()).isEqualTo(25);
        assertThat(page.orders()).isEmpty();
    }

    @Test
    void whitelisted_sort_field_is_converted_to_snake_case_order_item() {
        Page<String> page = MpPages.of(query(1, 10, "createTime", "desc"),
                                       Set.of("createTime", "username"));

        assertThat(page.orders()).hasSize(1);
        OrderItem item = page.orders().get(0);
        assertThat(item.getColumn()).isEqualTo("create_time");
        assertThat(item.isAsc()).isFalse();
    }

    @Test
    void sort_order_defaults_to_asc_when_not_desc() {
        Page<String> page = MpPages.of(query(1, 10, "username", "GARBAGE"), Set.of("username"));

        assertThat(page.orders().get(0).isAsc()).isTrue();
    }

    @Test
    void sort_order_is_case_insensitive() {
        Page<String> page = MpPages.of(query(1, 10, "username", "DESC"), Set.of("username"));

        assertThat(page.orders().get(0).isAsc()).isFalse();
    }

    @Test
    void sort_field_not_in_whitelist_is_rejected() {
        assertThatThrownBy(() -> MpPages.of(query(1, 10, "password", "asc"), Set.of("username")))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode().getCode())
                        .isEqualTo(CommonResultCode.BAD_REQUEST.getCode()));
    }

    @Test
    void blank_sort_field_is_ignored_not_rejected() {
        Page<String> page = MpPages.of(query(1, 10, "  ", "asc"), Set.of("username"));

        assertThat(page.orders()).isEmpty();
    }

    @Test
    void to_result_maps_records_and_copies_paging_meta() {
        Page<Integer> page = new Page<>(2, 10);
        page.setTotal(23);
        page.setRecords(List.of(11, 12, 13));

        PageResult<String> result = MpPages.toResult((IPage<Integer>) page, i -> "n" + i);

        assertThat(result.getTotal()).isEqualTo(23);
        assertThat(result.getPages()).isEqualTo(3);
        assertThat(result.getPageNum()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getRecords()).containsExactly("n11", "n12", "n13");
    }
}
