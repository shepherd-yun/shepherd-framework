package com.shepherd.boot.excel;

import cn.idev.excel.annotation.ExcelProperty;
import com.shepherd.boot.web.ShepherdWebProperties;
import com.shepherd.boot.web.advice.ResultBodyAdvice;
import jakarta.servlet.http.HttpServletResponse;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 锁定隐式行为：{@code void} + {@link HttpServletResponse} 的方法会让 Spring 置
 * {@code requestHandled=true}，根本不进入 ResponseBodyAdvice，因此导出接口不需要标
 * {@code @NoResultWrap}。这个测试没有新增行为，价值在于日后 ResultBodyAdvice 改动时会立刻变红。
 */
class ExcelResponsesWrapBypassTest {

    static class Row {
        @ExcelProperty("姓名") private String name;

        Row() {
        }

        Row(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @RestController
    static class ExportController {
        @GetMapping("/users/export")
        public void export(HttpServletResponse response) {
            ExcelResponses.write(response, "用户列表.xlsx", Row.class, List.of(new Row("张三")));
        }
    }

    private static MockMvc mvc() {
        ShepherdWebProperties.WrapResult props = new ShepherdWebProperties.WrapResult();
        props.setBasePackages(List.of());
        ResultBodyAdvice advice = new ResultBodyAdvice(props, JsonMapper.builder().build());
        return MockMvcBuilders.standaloneSetup(new ExportController()).setControllerAdvice(advice).build();
    }

    @Test
    void void_export_endpoint_is_not_wrapped_into_result_json() throws Exception {
        mvc().perform(get("/users/export"))
                .andExpect(status().isOk())
                // 若被 ResultBodyAdvice 包装，content-type 会变成 application/json
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .andExpect(header().string("Content-Disposition",
                        Matchers.startsWith("attachment;filename*=utf-8''")))
                // xlsx 是 zip，魔数 PK
                .andExpect(result -> {
                    byte[] body = result.getResponse().getContentAsByteArray();
                    assertThat(body).isNotEmpty();
                    assertThat(new String(body, 0, 2))
                            .as("响应体应是 xlsx（zip 魔数 PK），而不是被包装的 JSON")
                            .isEqualTo("PK");
                });
    }
}
