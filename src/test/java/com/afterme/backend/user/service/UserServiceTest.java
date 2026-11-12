package com.afterme.backend.user.service;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.user.domain.User;
import com.afterme.backend.user.dto.UpdateProfileRequest;
import com.afterme.backend.user.dto.UserProfileResponse;
import com.afterme.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;

    @InjectMocks UserService userService;

    User user;

    @BeforeEach
    void setUp() {
        user = User.create("user@afterme.com", "hashed", "홍길동");
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    void 내_프로필을_조회한다() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        UserProfileResponse response = userService.getProfile(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("user@afterme.com");
        assertThat(response.name()).isEqualTo("홍길동");
    }

    @Test
    void 이름을_앞뒤_공백을_제거해_수정한다() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        UserProfileResponse response =
                userService.updateProfile(1L, new UpdateProfileRequest("  김철수 "));

        assertThat(user.getName()).isEqualTo("김철수");
        assertThat(response.name()).isEqualTo("김철수");
    }

    @Test
    void 없는_회원의_프로필은_USER_NOT_FOUND() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
