package com.afterme.backend.auth.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 로그인 실패 잠금 정책. 잠금 지속시간과 해제 기준은 회의에서 확정한 뒤 설정값만 바꾼다.
 *
 * @param maxFailedAttempts 잠금이 걸리는 연속 실패 횟수
 * @param lockDuration      잠금 지속시간
 */
@ConfigurationProperties(prefix = "auth.login")
public record LoginPolicyProperties(
        int maxFailedAttempts,
        Duration lockDuration
) {
}
