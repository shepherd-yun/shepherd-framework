package com.shepherd.boot.web.advice;

import com.shepherd.boot.web.ShepherdWebProperties;
import com.shepherd.boot.web.code.CommonResultCode;
import com.shepherd.boot.web.vo.Result;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

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
        mvc(new ProbeController(), advice(List.of("com.shepherd.boot")))
                .perform(get("/dto"))
                .andExpect(jsonPath("$.data.name").value("alice"));
    }
}
