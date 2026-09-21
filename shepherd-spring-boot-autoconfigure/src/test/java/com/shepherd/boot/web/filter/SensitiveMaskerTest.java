package com.shepherd.boot.web.filter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveMaskerTest {

    private final SensitiveMasker masker =
            new SensitiveMasker(List.of("password", "accessToken"), 60);

    @Test
    void masks_configured_keys_in_json() {
        String masked = masker.mask("{\"username\":\"admin\",\"password\":\"admin123\"}");

        assertThat(masked).contains("\"username\":\"admin\"");
        assertThat(masked).contains("\"password\":\"***\"");
        assertThat(masked).doesNotContain("admin123");
    }

    @Test
    void masks_multiple_keys_and_tolerates_whitespace() {
        String masked = masker.mask("{ \"accessToken\" : \"eyJhbGciOi\", \"password\": \"p@ss\" }");

        assertThat(masked).doesNotContain("eyJhbGciOi");
        assertThat(masked).doesNotContain("p@ss");
    }

    @Test
    void leaves_unconfigured_keys_alone() {
        String masked = masker.mask("{\"nickname\":\"bob\"}");

        assertThat(masked).isEqualTo("{\"nickname\":\"bob\"}");
    }

    @Test
    void truncates_beyond_max_length() {
        String masked = masker.mask("x".repeat(200));

        assertThat(masked).hasSizeLessThanOrEqualTo(60 + "...(truncated)".length());
        assertThat(masked).endsWith("...(truncated)");
    }

    @Test
    void non_json_body_is_passed_through_and_truncated_only() {
        String masked = masker.mask("plain text body");

        assertThat(masked).isEqualTo("plain text body");
    }

    @Test
    void null_body_becomes_empty_string() {
        assertThat(masker.mask(null)).isEmpty();
    }
}
