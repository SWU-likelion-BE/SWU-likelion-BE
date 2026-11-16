package com.afterme.backend.global.security;

import com.afterme.backend.user.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;

@Configuration
public class JwtConfig {

    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        String secret = properties.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret(JWT_SECRET 환경변수)은 32바이트 이상이어야 합니다.");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties properties, UserRepository userRepository) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new JwtClaimValidator<String>(
                        TokenProvider.CLAIM_TYPE,
                        type -> Objects.equals(type, TokenProvider.TYPE_ACCESS))
        ));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new JwtClaimValidator<String>(
                        TokenProvider.CLAIM_TYPE,
                        type -> Objects.equals(type, TokenProvider.TYPE_ACCESS)
                ),
                new JwtClaimValidator<String>(
                        JwtClaimNames.SUB,
                        subject -> isActiveUser(subject, userRepository)
                )
        ));
        return decoder;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    private boolean isActiveUser(String subject, UserRepository userRepository){
        final long userId;

        try{
            userId = Long.parseLong(subject);
        }catch(NumberFormatException e){
            return false;
        }

        return userRepository.findById(userId)
                .filter(user -> !user.isWithdrawn())
                .isPresent();
    }
}
