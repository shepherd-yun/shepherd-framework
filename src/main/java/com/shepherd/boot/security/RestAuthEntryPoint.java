package com.shepherd.boot.security;

import com.shepherd.boot.core.result.CommonResultCode;
import com.shepherd.boot.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

public class RestAuthEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper objectMapper;

    public RestAuthEntryPoint(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.fail(CommonResultCode.UNAUTHORIZED));
    }
}
