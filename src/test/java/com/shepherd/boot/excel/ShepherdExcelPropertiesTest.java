package com.shepherd.boot.excel;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ShepherdExcelPropertiesTest {

    @Test
    void defaults_match_the_documented_table() {
        ShepherdExcelProperties p = new ShepherdExcelProperties();

        assertThat(p.isEnabled()).isFalse();          // 与 storage 一致，显式开启才装配
        assertThat(p.getMaxRows()).isEqualTo(100_000);
        assertThat(p.getBatchSize()).isEqualTo(500);
        assertThat(p.getAsyncThresholdRows()).isEqualTo(5_000);
        assertThat(p.getInlineErrorLimit()).isEqualTo(200);
        assertThat(p.getExportKeyPrefix()).isEqualTo("excel/export/");
        assertThat(p.getErrorKeyPrefix()).isEqualTo("excel/error/");

        assertThat(p.getTemplate().getDefaultRows()).isEqualTo(500);
        assertThat(p.getTemplate().getHiddenSheetName()).isEqualTo("__options");

        assertThat(p.getTask().getTtl()).isEqualTo(Duration.ofHours(24));
        assertThat(p.getTask().isEndpointEnabled()).isTrue();
        assertThat(p.getTask().getEndpointPath()).isEqualTo("/excel/tasks");
    }
}
