package com.afterme.backend.auth.service;

import com.afterme.backend.auth.domain.RefreshToken;
import com.afterme.backend.auth.dto.LoginRequest;
import com.afterme.backend.auth.dto.TokenResponse;
import com.afterme.backend.auth.repository.RefreshTokenRepository;
import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.global.security.TokenProvider;
import com.afterme.backend.user.domain.User;
import com.afterme.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock TokenProvider tokenProvider;

    AuthService authService;
    User user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, refreshTokenRepository, passwordEncoder, tokenProvider,
                new LoginPolicyProperties(5, Duration.ofMinutes(30)),
                Clock.fixed(NOW, ZoneOffset.UTC));
        user = User.create("user@afterme.com", "hashed", "홍길동");
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    void 로그인_성공시_토큰을_발급하고_리프레시_토큰_해시를_저장한다() {
        given(userRepository.findByEmail("user@afterme.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password1", "hashed")).willReturn(true);
        given(tokenProvider.createAccessToken(1L, NOW)).willReturn("access");
        given(tokenProvider.createRefreshToken()).willReturn("refresh");
        given(tokenProvider.hashRefreshToken("refresh")).willReturn("refresh-hash");
        given(tokenProvider.refreshTokenExpiresAt(NOW)).willReturn(NOW.plus(Duration.ofDays(14)));
        given(tokenProvider.accessTokenTtlSeconds()).willReturn(1800L);

        TokenResponse response = authService.login(new LoginRequest("User@AfterMe.com", "password1"));

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void 가입되지_않은_이메일이면_INVALID_CREDENTIALS() {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

        assertError(() -> authService.login(new LoginRequest("none@afterme.com", "password1")),
                ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void 비밀번호를_5회_틀리면_계정이_잠긴다() {
        given(userRepository.findByEmail("user@afterme.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "hashed")).willReturn(false);
        LoginRequest wrong = new LoginRequest("user@afterme.com", "wrong");

        for (int i = 0; i < 4; i++) {
            assertError(() -> authService.login(wrong), ErrorCode.INVALID_CREDENTIALS);
        }
        assertError(() -> authService.login(wrong), ErrorCode.ACCOUNT_LOCKED);

        assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    void 잠긴_계정은_비밀번호가_맞아도_로그인할_수_없다() {
        ReflectionTestUtils.setField(user, "lockedUntil", NOW.plusSeconds(60));
        given(userRepository.findByEmail("user@afterme.com")).willReturn(Optional.of(user));

        assertError(() -> authService.login(new LoginRequest("user@afterme.com", "password1")),
                ErrorCode.ACCOUNT_LOCKED);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void 로그인_성공시_실패_횟수가_초기화된다() {
        ReflectionTestUtils.setField(user, "failedLoginCount", 3);
        given(userRepository.findByEmail("user@afterme.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password1", "hashed")).willReturn(true);
        given(tokenProvider.createRefreshToken()).willReturn("refresh");

        authService.login(new LoginRequest("user@afterme.com", "password1"));

        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
