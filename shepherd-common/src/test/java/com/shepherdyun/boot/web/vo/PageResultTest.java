package com.shepherdyun.boot.web.vo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    @Test
    void of_exposes_all_paging_meta_and_records() {
        PageResult<String> result = PageResult.of(23, 3, 2, 10, List.of("a", "b"));

        assertThat(result.getTotal()).isEqualTo(23);
        assertThat(result.getPages()).isEqualTo(3);
        assertThat(result.getPageNum()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getRecords()).containsExactly("a", "b");
    }
}
