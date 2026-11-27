package com.afterme.backend.global.config;

import com.afterme.backend.global.security.JwtConfig;
import com.afterme.backend.global.security.JwtProperties;
import com.afterme.backend.global.security.SecurityConfig;
import com.afterme.backend.global.security.SecurityErrorResponder;
import com.afterme.backend.global.security.TokenProvider;
import com.afterme.backend.user.controller.UserController;
import com.afterme.backend.user.repository.UserRepository;
import com.afterme.backend.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS 설정이 Security 필터 체인에서 인증보다 먼저 적용되는지 검증한다.
 */
@WebMvcTest(UserController.class)
@Import({CorsConfig.class, SecurityConfig.class, JwtConfig.class, TokenProvider.class,
        SecurityErrorResponder.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "cors.allowed-origins=https://front.example.com",
        "jwt.secret=test-secret-key-must-be-at-least-32-bytes!!",
        "jwt.issuer=afterme",
        "jwt.access-token-ttl=30m",
        "jwt.refresh-token-ttl=14d"
})
class CorsSecurityIntegrationTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean UserService userService;
    @MockitoBean UserRepository userRepository;

    @Test
    void 허용된_Origin의_preflight는_토큰_없이도_200() throws Exception {
        mockMvc.perform(options("/api/users/me")
                        .header("Origin", "https://front.example.com")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://front.example.com"));
    }

    @Test
    void 허용되지_않은_Origin의_preflight는_403() throws Exception {
        mockMvc.perform(options("/api/users/me")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void 인증_실패_응답에도_CORS_헤더가_붙어_브라우저가_401을_읽을_수_있다() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Origin", "https://front.example.com"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://front.example.com"));
    }
}
