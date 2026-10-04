package com.aicit.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter for login endpoints.
 * Allows {@code app.rate-limit.login.capacity} attempts per IP per
 * {@code app.rate-limit.login.refill-minutes} minutes.
 * Defaults: 10 per 5 minutes.  Set capacity=1000 in test profile to disable effectively.
 * Exceeding the limit returns HTTP 429.
 */
@Configuration
@Slf4j
public class RateLimitFilter {

    @Value("${app.rate-limit.login.capacity:10}")
    private int capacity;

    @Value("${app.rate-limit.login.refill-minutes:5}")
    private int refillMinutes;

    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();

    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.classic(capacity,
                Refill.intervally(capacity, Duration.ofMinutes(refillMinutes)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket getBucket(String ip) {
        return loginBuckets.computeIfAbsent(ip, k -> createBucket());
    }

    @Bean
    public FilterRegistrationBean<Filter> loginRateLimitFilter() {
        FilterRegistrationBean<Filter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response,
                                 FilterChain chain) throws IOException, ServletException {
                HttpServletRequest  req = (HttpServletRequest) request;
                HttpServletResponse res = (HttpServletResponse) response;

                String path = req.getRequestURI();
                if ((path.contains("/api/auth/admin/login") ||
                     path.contains("/api/auth/institute/login"))
                     && "POST".equalsIgnoreCase(req.getMethod())) {

                    String ip = getClientIp(req);
                    Bucket bucket = getBucket(ip);

                    if (!bucket.tryConsume(1)) {
                        log.warn("Rate limit exceeded for IP {} on {}", ip, path);
                        res.setStatus(429);
                        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        res.getWriter().write(
                            "{\"success\":false,\"message\":\"Too many login attempts. " +
                            "Please wait 5 minutes before trying again.\",\"error\":\"RATE_LIMIT_EXCEEDED\"}");
                        return;
                    }
                }
                chain.doFilter(request, response);
            }

            private String getClientIp(HttpServletRequest req) {
                String xff = req.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
                return req.getRemoteAddr();
            }
        });
        reg.addUrlPatterns("/api/auth/*");
        reg.setOrder(2);
        return reg;
    }
}
