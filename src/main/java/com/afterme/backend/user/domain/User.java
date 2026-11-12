package com.afterme.backend.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private User(String email, String passwordHash, String name) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.failedLoginCount = 0;
    }

    public static User create(String email, String passwordHash, String name) {
        return new User(email, passwordHash, name);
    }

    public void updateName(String name) {
        this.name = name;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * 로그인 실패를 기록한다. 이전 잠금이 끝났다면 실패 횟수를 새로 센다.
     *
     * @return 이번 실패로 계정이 잠겼으면 true
     */
    public boolean recordLoginFailure(int maxFailedAttempts, Duration lockDuration, Instant now) {
        if (lockedUntil != null && !isLocked(now)) {
            failedLoginCount = 0;
            lockedUntil = null;
        }

        failedLoginCount++;
        if (failedLoginCount >= maxFailedAttempts) {
            lockedUntil = now.plus(lockDuration);
            return true;
        }
        return false;
    }

    public void recordLoginSuccess() {
        failedLoginCount = 0;
        lockedUntil = null;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
