package com.afterme.backend.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application-prod.properties의 {@code cors.allowed-origins=${CORS_ALLOWED_ORIGINS:}} 설정이
 * 환경변수가 없거나 비어 있어도 컨텍스트가 정상 시작되는지 검증한다.
 */
class CorsPropertyBindingTest {

    // prod 프로파일과 같은 플레이스홀더를 사용한다.
    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CorsConfig.class)
            .withPropertyValues("cors.allowed-origins=${CORS_ALLOWED_ORIGINS:}");

    @Test
    void CORS_ALLOWED_ORIGINS가_없으면_빈_목록으로_시작한다() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(originsOf(context.getBean(CorsConfigurationSource.class))).isEmpty();
        });
    }

    @Test
    void CORS_ALLOWED_ORIGINS가_빈_문자열이면_빈_목록으로_시작한다() {
        runner.withPropertyValues("CORS_ALLOWED_ORIGINS=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(originsOf(context.getBean(CorsConfigurationSource.class))).isEmpty();
                });
    }

    @Test
    void 공백과_쉼표만_있어도_빈_목록으로_시작한다() {
        runner.withPropertyValues("CORS_ALLOWED_ORIGINS= , ,")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(originsOf(context.getBean(CorsConfigurationSource.class))).isEmpty();
                });
    }

    @Test
    void 값이_있으면_그_Origin만_허용한다() {
        runner.withPropertyValues("CORS_ALLOWED_ORIGINS=https://a.example.com,https://b.example.com")
                .run(context -> assertThat(originsOf(context.getBean(CorsConfigurationSource.class)))
                        .containsExactly("https://a.example.com", "https://b.example.com"));
    }

    private static java.util.List<String> originsOf(CorsConfigurationSource source) {
        CorsConfiguration config =
                source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/users/me"));
        return config.getAllowedOrigins();
    }
}
