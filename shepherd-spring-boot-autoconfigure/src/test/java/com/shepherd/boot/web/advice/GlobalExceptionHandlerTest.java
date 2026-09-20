package com.shepherd.boot.web.advice;

import com.shepherd.boot.web.code.CommonResultCode;
import com.shepherd.boot.web.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    record Payload(String name) {
    }

    @RestController
    static class ProbeController {
        @GetMapping("/boom")
        String boom() { throw new BizException(CommonResultCode.NOT_FOUND); }

        @GetMapping("/typed")
        String typed(@RequestParam int size) { return "ok" + size; }

        @GetMapping("/required")
        String required(@RequestParam String name) { return "ok" + name; }

        @PostMapping("/body")
        String body(@RequestBody Payload payload) { return "ok" + payload.name(); }

        @GetMapping("/unknown")
        String unknown() { throw new IllegalStateException("未预期的内部错误"); }
    }

    private MockMvc mvc() {
        return MockMvcBuilders
                .standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void biz_exception_keeps_its_own_code() throws Exception {
        mvc().perform(get("/boom"))
             .andExpect(status().isOk())
             .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void type_mismatch_is_reported_as_param_type_mismatch() throws Exception {
        mvc().perform(get("/typed").param("size", "not-a-number"))
             .andExpect(status().isOk())
             .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    void missing_required_param_is_reported_as_bad_request() throws Exception {
        mvc().perform(get("/required"))
             .andExpect(status().isOk())
             .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void unreadable_body_is_reported_as_body_unreadable() throws Exception {
        mvc().perform(post("/body")
                     .contentType(MediaType.APPLICATION_JSON)
                     .content("{ this is not json "))
             .andExpect(status().isOk())
             .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void method_not_supported_is_reported_as_method_not_allowed() throws Exception {
        mvc().perform(post("/boom"))
             .andExpect(status().isOk())
             .andExpect(jsonPath("$.code").value(40500));
    }

    @Test
    void unknown_exception_falls_back_to_system_error() throws Exception {
        mvc().perform(get("/unknown"))
             .andExpect(status().isOk())
             .andExpect(jsonPath("$.code").value(50000));
    }

    @Test
    void unknown_exception_does_not_leak_internal_message() throws Exception {
        // 兜底分支必须回固定文案，不能把内部异常 message 透给调用方
        mvc().perform(get("/unknown"))
             .andExpect(jsonPath("$.message").value(CommonResultCode.SYSTEM_ERROR.getMessage()));
    }
}
