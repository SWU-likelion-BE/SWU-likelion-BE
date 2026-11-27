package com.afterme.backend.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    final CorsConfig corsConfig = new CorsConfig();

    @Test
    void 쉼표로_구분한_Origin을_공백_제거해_허용한다() {
        CorsConfiguration config = configFor(" https://a.example.com , https://b.example.com ");

        assertThat(config.getAllowedOrigins())
                .containsExactly("https://a.example.com", "https://b.example.com");
        assertThat(config.checkOrigin("https://a.example.com")).isEqualTo("https://a.example.com");
        assertThat(config.checkOrigin("https://evil.example.com")).isNull();
    }

    @Test
    void 값이_비어있으면_어떤_Origin도_허용하지_않는다() {
        CorsConfiguration config = configFor("");

        assertThat(config.getAllowedOrigins()).isEmpty();
        assertThat(config.checkOrigin("https://a.example.com")).isNull();
    }

    @Test
    void API_경로에만_적용하고_쿠키_자격증명은_허용하지_않는다() {
        CorsConfigurationSource source = corsConfig.corsConfigurationSource("https://a.example.com");

        assertThat(source.getCorsConfiguration(new MockHttpServletRequest("GET", "/actuator/health")))
                .isNull();
        assertThat(configFor("https://a.example.com").getAllowCredentials()).isFalse();
    }

    private CorsConfiguration configFor(String origins) {
        return corsConfig.corsConfigurationSource(origins)
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/users/me"));
    }
}
