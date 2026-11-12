package com.afterme.backend.global.security;

import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.global.response.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Security 필터 단계에서 발생한 인증/인가 오류를 공통 오류 형식으로 응답한다.
 * 필터 단계 예외는 GlobalExceptionHandler까지 전달되지 않기 때문에 따로 처리한다.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorResponder {

    private final JsonMapper jsonMapper;

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(
                response.getWriter(),
                ErrorResponse.of(errorCode.getCode(), errorCode.getMessage())
        );
    }
}
