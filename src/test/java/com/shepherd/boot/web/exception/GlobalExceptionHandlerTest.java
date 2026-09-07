package com.shepherd.boot.web.exception;

import com.shepherd.boot.web.code.CommonResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @RestController
    static class BoomController {
        @GetMapping("/boom")
        String boom() { throw new BizException(CommonResultCode.NOT_FOUND); }
    }

    @Test
    void biz_exception_is_translated_to_result_json() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new BoomController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/boom"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(40400));
    }
}
