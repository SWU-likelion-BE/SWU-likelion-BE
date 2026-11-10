package com.afterme.backend.user.controller;

import com.afterme.backend.global.security.JwtConfig;
import com.afterme.backend.global.security.JwtProperties;
import com.afterme.backend.global.security.SecurityConfig;
import com.afterme.backend.global.security.SecurityErrorResponder;
import com.afterme.backend.global.security.TokenProvider;
import com.afterme.backend.user.dto.UpdateProfileRequest;
import com.afterme.backend.user.dto.UserProfileResponse;
import com.afterme.backend.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtConfig.class, TokenProvider.class, SecurityErrorResponder.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-must-be-at-least-32-bytes!!",
        "jwt.issuer=afterme",
        "jwt.access-token-ttl=30m",
        "jwt.refresh-token-ttl=14d"
})
class UserControllerTest {

    static final UserProfileResponse PROFILE = new UserProfileResponse(
            42L, "user@afterme.com", "홍길동", Instant.parse("2026-11-11T00:00:00Z"));

    @Autowired MockMvc mockMvc;
    @Autowired TokenProvider tokenProvider;

    @MockitoBean UserService userService;

    @Test
    void 토큰_없이_요청하면_401과_공통_오류_형식을_반환한다() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.errors").isArray());

        verify(userService, never()).getProfile(anyLong());
    }

    @Test
    void 잘못된_토큰이면_401() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    @Test
    void 내_프로필_조회는_토큰의_사용자_id로_조회해_data로_감싸서_반환한다() throws Exception {
        given(userService.getProfile(42L)).willReturn(PROFILE);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.email").value("user@afterme.com"))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void 프로필_수정() throws Exception {
        given(userService.updateProfile(eq(42L), any())).willReturn(PROFILE);

        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"홍길동\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("홍길동"));

        verify(userService).updateProfile(42L, new UpdateProfileRequest("홍길동"));
    }

    @Test
    void 이름이_50자를_넘으면_프로필_수정은_400() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "가".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));

        verify(userService, never()).updateProfile(any(), any());
    }

    private String token() {
        return tokenProvider.createAccessToken(42L, Instant.now());
    }
}
