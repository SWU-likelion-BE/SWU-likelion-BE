package com.afterme.backend.global.security;

import com.afterme.backend.user.domain.User;
import com.afterme.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class TokenProviderTest {

    final JwtProperties properties = new JwtProperties(
            "test-secret-key-must-be-at-least-32-bytes!!", "afterme",
            Duration.ofMinutes(30), Duration.ofDays(14));
    final JwtConfig config = new JwtConfig();
    final SecretKey key = config.jwtSecretKey(properties);
    final TokenProvider tokenProvider = new TokenProvider(config.jwtEncoder(key), properties);
    final UserRepository userRepository = mock(UserRepository.class);
    final JwtDecoder decoder = config.jwtDecoder(key, properties, userRepository);

    @Test
    void 발급한_액세스_토큰을_검증하면_subject에_사용자_id가_있다() {
        given(userRepository.findById(42L))
                .willReturn(Optional.of(User.create("user@afterme.com", "hashed", "User")));
        String token = tokenProvider.createAccessToken(42L, Instant.now());

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString(TokenProvider.CLAIM_TYPE)).isEqualTo(TokenProvider.TYPE_ACCESS);
    }

    @Test
    void 만료된_액세스_토큰은_거부한다() {
        given(userRepository.findById(42L))
                .willReturn(Optional.of(User.create("user@afterme.com", "hashed", "User")));
        String token = tokenProvider.createAccessToken(42L, Instant.now().minus(Duration.ofHours(1)));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void 다른_키로_서명한_토큰은_거부한다() {
        JwtProperties other = new JwtProperties(
                "another-secret-key-must-be-at-least-32-bytes", "afterme",
                Duration.ofMinutes(30), Duration.ofDays(14));
        String token = new TokenProvider(config.jwtEncoder(config.jwtSecretKey(other)), other)
                .createAccessToken(42L, Instant.now());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void 리프레시_토큰은_매번_다르고_해시는_결정적이다() {
        String a = tokenProvider.createRefreshToken();
        String b = tokenProvider.createRefreshToken();

        assertThat(a).isNotEqualTo(b);
        assertThat(tokenProvider.hashRefreshToken(a))
                .hasSize(64)
                .isEqualTo(tokenProvider.hashRefreshToken(a));
    }

    @Test
    void 짧은_시크릿은_시작할_때_거부한다() {
        JwtProperties weak = new JwtProperties("short", "afterme",
                Duration.ofMinutes(30), Duration.ofDays(14));

        assertThatThrownBy(() -> config.jwtSecretKey(weak)).isInstanceOf(IllegalStateException.class);
    }
}
