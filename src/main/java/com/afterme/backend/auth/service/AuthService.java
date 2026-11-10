package com.afterme.backend.auth.service;

import com.afterme.backend.auth.domain.RefreshToken;
import com.afterme.backend.auth.dto.LoginRequest;
import com.afterme.backend.auth.dto.SignupRequest;
import com.afterme.backend.auth.dto.SignupResponse;
import com.afterme.backend.auth.dto.TokenResponse;
import com.afterme.backend.auth.repository.RefreshTokenRepository;
import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.global.security.TokenProvider;
import com.afterme.backend.user.domain.User;
import com.afterme.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final Clock clock;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.create(
                email,
                passwordEncoder.encode(request.password()),
                request.name().trim()
        );

        try {
            return SignupResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            // 동시에 같은 이메일로 가입한 경우 unique 제약으로 막힌다.
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Instant now = clock.instant();
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        return issueTokens(user.getId(), now);
    }

    private TokenResponse issueTokens(Long userId, Instant now) {
        String accessToken = tokenProvider.createAccessToken(userId, now);
        String refreshToken = tokenProvider.createRefreshToken();

        refreshTokenRepository.save(RefreshToken.create(
                userId,
                tokenProvider.hashRefreshToken(refreshToken),
                tokenProvider.refreshTokenExpiresAt(now),
                now
        ));

        return TokenResponse.bearer(accessToken, refreshToken, tokenProvider.accessTokenTtlSeconds());
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
