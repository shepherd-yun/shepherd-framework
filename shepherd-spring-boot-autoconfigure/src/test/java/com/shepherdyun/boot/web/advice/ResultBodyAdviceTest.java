package com.shepherdyun.boot.web.advice;

import com.shepherdyun.boot.web.ShepherdWebProperties;
import com.shepherdyun.boot.web.code.CommonResultCode;
import com.shepherdyun.boot.web.vo.Result;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResultBodyAdviceTest {

    record Dto(String name, int age) {
    }

    @RestController
    static class ProbeController {
        @GetMapping("/dto")        Dto dto()          { return new Dto("alice", 30); }
        @GetMapping("/text")       String text()      { return "hello"; }
        @GetMapping("/wrapped")    Result<Dto> wrapped() { return Result.success(new Dto("bob", 40)); }
        @GetMapping("/bytes")      byte[] bytes()     { return new byte[]{1, 2, 3}; }
        @GetMapping("/resource")   Resource res()     { return new ByteArrayResource(new byte[]{4, 5}); }
        @GetMapping("/nothing")    Dto nothing()      { return null; }

        @NoResultWrap
        @GetMapping("/raw")        Dto raw()          { return new Dto("carol", 50); }
    }

    @NoResultWrap
    @RestController
    static class RawController {
        @GetMapping("/class-raw")  Dto raw()          { return new Dto("dave", 60); }
    }

    private static ResultBodyAdvice advice(List<String> basePackages) {
        ShepherdWebProperties.WrapResult props = new ShepherdWebProperties.WrapResult();
        props.setBasePackages(basePackages);
        return new ResultBodyAdvice(props, JsonMapper.builder().build());
    }

    private static MockMvc mvc(Object controller, ResultBodyAdvice advice) {
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(advice).build();
    }

    @Test
    void plain_object_is_wrapped_into_result() throws Exception {
        mvc(new ProbeController(), advice(List.of()))
                .perform(get("/dto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CommonResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.name").value("alice"))
                .andExpect(jsonPath("$.data.age").value(30));
    }

    /** String 返回值走 StringHttpMessageConverter，直接返回 Result 对象会 ClassCastException。 */
    @Test
    void string_return_is_serialized_manually_as_json() throws Exception {
        mvc(new ProbeController(), advice(List.of()))
                .perform(get("/text"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("hello"));
    }

    @Test
    void already_wrapped_result_is_not_double_wrapped() throws Exception {
        mvc(new ProbeController(), advice(List.of()))
                .perform(get("/wrapped"))
                .andExpect(jsonPath("$.data.name").value("bob"))
                .andExpect(jsonPath("$.data.data").doesNotExist());
    }

    @Test
    void null_body_becomes_success_with_null_data() throws Exception {
        mvc(new ProbeController(), advice(List.of()))
                .perform(get("/nothing"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void byte_array_download_is_left_alone() throws Exception {
        mvc(new ProbeController(), advice(List.of()))
                .perform(get("/bytes"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void resource_download_is_left_alone() throws Exception {
        mvc(new ProbeController(), advice(List.of()))
                .perform(get("/resource"))
                .andExpect(content().bytes(new byte[]{4, 5}));
    }

    @Test
    void method_level_no_result_wrap_opts_out() throws Exception {
        mvc(new ProbeController(), advice(List.of()))
                .perform(get("/raw"))
                .andExpect(jsonPath("$.name").value("carol"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void class_level_no_result_wrap_opts_out() throws Exception {
        mvc(new RawController(), advice(List.of()))
                .perform(get("/class-raw"))
                .andExpect(jsonPath("$.name").value("dave"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    /** base-packages 非空时只包装匹配的包，其余放行。 */
    @Test
    void base_packages_narrows_the_scope() throws Exception {
        mvc(new ProbeController(), advice(List.of("com.example.other")))
                .perform(get("/dto"))
                .andExpect(jsonPath("$.name").value("alice"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void base_packages_matching_this_controller_still_wraps() throws Exception {
        mvc(new ProbeController(), advice(List.of("com.shepherdyun.boot")))
                .perform(get("/dto"))
                .andExpect(jsonPath("$.data.name").value("alice"));
    }

    /**
     * 钉死 Boot 4 下真正处理 actuator 请求的那个类：Boot 4 把 MVC 端点实现从
     * {@code org.springframework.boot.actuate} 挪到了 {@code org.springframework.boot.webmvc.actuate}，
     * 只按旧包名排除会让 /actuator/health 被套上 Result，监控按顶层 status 读不到值。
     * 用真实类而非自造同名包：Boot 再挪一次包，这里会立刻变红。
     */
    @Test
    void boot4_webmvc_actuator_endpoints_are_never_wrapped() throws Exception {
        Class<?> operationHandler = Class.forName(
                "org.springframework.boot.webmvc.actuate.endpoint.web.AbstractWebMvcEndpointHandlerMapping$OperationHandler");
        Method handle = operationHandler.getDeclaredMethod("handle", HttpServletRequest.class, Map.class);

        boolean wraps = advice(List.of()).supports(new MethodParameter(handle, -1), JacksonJsonHttpMessageConverter.class);

        assertThat(wraps).isFalse();
    }
}
