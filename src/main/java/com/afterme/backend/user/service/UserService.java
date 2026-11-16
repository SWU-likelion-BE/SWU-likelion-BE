package com.afterme.backend.user.service;

import com.afterme.backend.auth.repository.RefreshTokenRepository;
import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.user.domain.User;
import com.afterme.backend.user.dto.UpdateProfileRequest;
import com.afterme.backend.user.dto.UserProfileResponse;
import com.afterme.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final Clock clock;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        return UserProfileResponse.from(getUser(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getUser(userId);
        user.updateName(request.name().trim());
        return UserProfileResponse.from(user);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public void withdraw(Long userId){
        User user = getUser(userId);
        user.withdraw(clock.instant());
        refreshTokenRepository.deleteAllByUserId(userId);
    }
}
