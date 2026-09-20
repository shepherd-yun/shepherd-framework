package com.shepherd.boot.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectKeysTest {

    @Test
    void strips_leading_slashes() {
        assertThat(ObjectKeys.normalize("///a/b.png")).isEqualTo("a/b.png");
    }

    @Test
    void normalizes_backslashes() {
        assertThat(ObjectKeys.normalize("a\\b.png")).isEqualTo("a/b.png");
    }

    @Test
    void rejects_blank_key() {
        assertThatThrownBy(() -> ObjectKeys.normalize("  "))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void rejects_path_traversal() {
        assertThatThrownBy(() -> ObjectKeys.normalize("a/../../etc/passwd"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("..");
    }

    @Test
    void keeps_dots_that_are_not_a_whole_segment() {
        assertThat(ObjectKeys.normalize("a/..b/c..d.png")).isEqualTo("a/..b/c..d.png");
    }
}
