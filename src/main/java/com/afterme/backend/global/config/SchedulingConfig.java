package com.afterme.backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 활성화. 실제 실행 여부는 각 워커의 설정값(예: ai.worker.enabled)으로 제어한다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}