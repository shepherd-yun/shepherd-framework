package com.shepherdyun.boot.excel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExcelTaskControllerTest {

    private final AsyncExcelService service = mock(AsyncExcelService.class);
    private final ExcelTaskController controller = new ExcelTaskController(service);

    private static void authenticateAs(String name) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(name, "n/a", java.util.List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static ExcelTaskVo vo() {
        return new ExcelTaskVo("EX1", "EXPORT", "SUCCEEDED", "订单.xlsx", 10, 10,
                "https://cdn.example.com/excel/export/EX1.xlsx", null, Instant.now(), Instant.now());
    }

    @Test
    void returns_the_task_for_its_owner() {
        authenticateAs("alice");
        when(service.findForOwner(eq("EX1"), eq("alice"))).thenReturn(Optional.of(vo()));

        ExcelTaskVo result = controller.get("EX1");

        assertThat(result.id()).isEqualTo("EX1");
        assertThat(result.downloadUrl()).isNotNull();
    }

    @Test
    void someone_elses_task_reads_as_not_found_never_as_forbidden() {
        authenticateAs("bob");
        when(service.findForOwner(eq("EX1"), eq("bob"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.get("EX1"))
                .isInstanceOf(ExcelException.class)
                // 403 等于确认「该 id 存在但不属于你」，给了枚举探测的确认信号
                .hasFieldOrPropertyWithValue("errorCode", ExcelErrorCode.TASK_NOT_FOUND);
    }

    @Test
    void unauthenticated_request_also_reads_as_not_found() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> controller.get("EX1"))
                .isInstanceOf(ExcelException.class)
                .hasFieldOrPropertyWithValue("errorCode", ExcelErrorCode.TASK_NOT_FOUND);
    }

    /** 匿名 token 的 isAuthenticated() 是 true，若不单独排除，所有匿名请求会共用同一个 ownerId。 */
    @Test
    void anonymous_request_reads_as_not_found() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThatThrownBy(() -> controller.get("EX1"))
                .isInstanceOf(ExcelException.class)
                .hasFieldOrPropertyWithValue("errorCode", ExcelErrorCode.TASK_NOT_FOUND);
    }
}
