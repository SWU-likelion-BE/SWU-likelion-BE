package com.afterme.backend.auth.service;

import com.afterme.backend.auth.dto.SignupRequest;
import com.afterme.backend.auth.dto.SignupResponse;
import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.user.domain.User;
import com.afterme.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceSignupTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    AuthService authService;

    @Test
    void 이메일을_소문자로_정규화하고_비밀번호를_해시하여_저장한다() {
        given(userRepository.existsByEmail("user@afterme.com")).willReturn(false);
        given(passwordEncoder.encode("password1")).willReturn("hashed");
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        SignupResponse response = authService.signup(
                new SignupRequest("  User@AfterMe.com ", "password1", " 홍길동 "));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("user@afterme.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getName()).isEqualTo("홍길동");
        assertThat(response.email()).isEqualTo("user@afterme.com");
    }

    @Test
    void 이미_가입된_이메일이면_DUPLICATE_EMAIL() {
        given(userRepository.existsByEmail("user@afterme.com")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("user@afterme.com", "password1", "홍길동")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시_가입으로_unique_제약에_걸리면_DUPLICATE_EMAIL() {
        given(userRepository.existsByEmail("user@afterme.com")).willReturn(false);
        given(passwordEncoder.encode(any())).willReturn("hashed");
        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("user@afterme.com", "password1", "홍길동")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }
}
