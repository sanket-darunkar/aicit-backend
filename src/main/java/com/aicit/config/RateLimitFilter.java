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

    // Default raised to 30/5min: behind a shared proxy (Render) several real users
    // can present the same forwarded IP, so a low cap locked out unrelated users.
    @Value("${app.rate-limit.login.capacity:30}")
    private int capacity;

    @Value("${app.rate-limit.login.refill-minutes:5}")
    private int refillMinutes;

    // Safety cap so the in-memory map can't grow unbounded under IP/header rotation.
    private static final int MAX_BUCKETS = 50_000;

    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();

    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.classic(capacity,
                Refill.intervally(capacity, Duration.ofMinutes(refillMinutes)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket getBucket(String key) {
        // Defensive eviction: if the map grows too large, clear it rather than leak memory.
        if (loginBuckets.size() > MAX_BUCKETS) {
            loginBuckets.clear();
        }
        return loginBuckets.computeIfAbsent(key, k -> createBucket());
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

                    String key = getClientKey(req);
                    Bucket bucket = getBucket(key);

                    if (!bucket.tryConsume(1)) {
                        log.warn("Rate limit exceeded for key {} on {}", key, path);
                        // Echo CORS headers so the browser surfaces the real 429 instead of
                        // reporting a misleading CORS/403 failure (this filter runs outside
                        // Spring's CORS handling).
                        String origin = req.getHeader("Origin");
                        if (origin != null && !origin.isBlank()) {
                            res.setHeader("Access-Control-Allow-Origin", origin);
                            res.setHeader("Access-Control-Allow-Credentials", "true");
                            res.setHeader("Vary", "Origin");
                        }
                        res.setStatus(429);
                        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        res.getWriter().write(
                            "{\"success\":false,\"message\":\"Too many login attempts. " +
                            "Please wait a few minutes before trying again.\",\"error\":\"RATE_LIMIT_EXCEEDED\"}");
                        return;
                    }
                }
                chain.doFilter(request, response);
            }

            /**
             * Builds a best-effort per-client key for rate limiting.
             *
             * <p>Behind a shared proxy (Render) the first X-Forwarded-For hop is the
             * real client IP, but many clients can still collapse to the same value.
             * To avoid locking unrelated users out of login, we combine the full
             * forwarded chain with the User-Agent so distinct devices get distinct
             * buckets while still throttling a single abusive client.
             */
            private String getClientKey(HttpServletRequest req) {
                String xff = req.getHeader("X-Forwarded-For");
                String ip  = (xff != null && !xff.isBlank())
                        ? xff.split(",")[0].trim()
                        : req.getRemoteAddr();
                String ua = req.getHeader("User-Agent");
                String uaPart = (ua == null || ua.isBlank())
                        ? "na"
                        : Integer.toHexString(ua.hashCode());
                return ip + "|" + uaPart;
            }
        });
        reg.addUrlPatterns("/api/auth/*");
        reg.setOrder(2);
        return reg;
    }
}
