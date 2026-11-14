package com.afterme.backend.auth.controller;

import com.afterme.backend.auth.dto.*;
import com.afterme.backend.auth.service.AuthService;
import com.afterme.backend.global.response.ApiResponse;
import com.afterme.backend.global.security.LoginUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.of(authService.signup(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(authService.login(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ){
        TokenResponse tokens = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.of(tokens));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @LoginUserId Long userId,
            @Valid @RequestBody RefreshTokenRequest request
    ){
        authService.logout(userId, request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
