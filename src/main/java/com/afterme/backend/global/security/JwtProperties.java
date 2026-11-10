package com.afterme.backend.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param secret          HS256 서명 키. 32바이트(256bit) 이상이어야 한다.
 * @param issuer          토큰 발급자(iss)
 * @param accessTokenTtl  액세스 토큰 유효기간
 * @param refreshTokenTtl 리프레시 토큰 유효기간
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
}
