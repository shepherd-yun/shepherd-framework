package com.shepherd.boot.security;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginUserTest {

    @Test
    void four_arg_constructor_keeps_existing_callers_compiling_with_empty_attributes() {
        LoginUser user = new LoginUser(1L, "alice", List.of("ADMIN"), "jti");

        assertThat(user.attributes()).isEmpty();
    }

    @Test
    void attributes_are_defensively_copied() {
        Map<String, Object> source = new HashMap<>();
        source.put("orgId", 7L);
        LoginUser user = new LoginUser(1L, "alice", List.of(), "jti", source);

        source.put("orgId", 8L);

        assertThat(user.attributeLong("orgId")).isEqualTo(7L);
        assertThatThrownBy(() -> user.attributes().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    /** JSON 往返后 null 与「键不存在」无法区分，构造期直接拒绝，而不是让下游读到语义含糊的值。 */
    @Test
    void null_attribute_value_is_rejected() {
        Map<String, Object> source = new HashMap<>();
        source.put("orgId", null);

        assertThatThrownBy(() -> new LoginUser(1L, "alice", List.of(), "jti", source))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void attribute_long_rejects_non_numeric_value() {
        LoginUser user = new LoginUser(1L, "alice", List.of(), "jti", Map.of("orgId", "abc"));

        assertThatThrownBy(() -> user.attributeLong("orgId"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("orgId");
    }
}
