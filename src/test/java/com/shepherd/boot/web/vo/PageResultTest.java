package com.shepherd.boot.web.vo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    @Test
    void of_maps_records_and_copies_paging_meta() {
        Page<Integer> page = new Page<>(2, 10);
        page.setTotal(23);
        page.setRecords(List.of(11, 12, 13));

        PageResult<String> result = PageResult.of(page, i -> "n" + i);

        assertThat(result.getTotal()).isEqualTo(23);
        assertThat(result.getPageNum()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getPages()).isEqualTo(3);
        assertThat(result.getRecords()).containsExactly("n11", "n12", "n13");
    }
}
