package com.afterme.backend.auth.service;

import com.afterme.backend.auth.dto.SignupRequest;
import com.afterme.backend.auth.dto.SignupResponse;
import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.user.domain.User;
import com.afterme.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
