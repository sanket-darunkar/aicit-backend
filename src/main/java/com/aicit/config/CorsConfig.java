package com.aicit.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@Slf4j
public class CorsConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Parse the comma-separated origin list. Each entry is trimmed and any
        // trailing slash is removed, because a browser Origin header never has a
        // trailing slash — so a configured "https://aicit.org.in/" would fail to
        // match "https://aicit.org.in" and the request would be CORS-blocked.
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .map(o -> o.endsWith("/") ? o.substring(0, o.length() - 1) : o)
                .filter(s -> !s.isEmpty())
                .toList();

        // Use allowedOriginPatterns (not allowedOrigins): it is the credential-safe
        // mechanism and matches the exact origins listed. This avoids the strict
        // exact-string pitfalls of setAllowedOrigins when credentials are enabled.
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        log.info("CORS allowed origins: {}", origins);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
