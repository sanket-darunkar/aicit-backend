package com.aicit.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Adds security headers to every HTTP response.
 */
@Configuration
public class SecurityHeadersConfig {

    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
        FilterRegistrationBean<SecurityHeadersFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new SecurityHeadersFilter());
        reg.addUrlPatterns("/*");
        reg.setOrder(1);
        return reg;
    }

    public static class SecurityHeadersFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response,
                             FilterChain chain) throws IOException, ServletException {
            HttpServletResponse res = (HttpServletResponse) response;
            res.setHeader("X-Content-Type-Options",    "nosniff");
            res.setHeader("X-Frame-Options",            "DENY");
            res.setHeader("X-XSS-Protection",           "1; mode=block");
            res.setHeader("Referrer-Policy",            "strict-origin-when-cross-origin");
            res.setHeader("Permissions-Policy",         "geolocation=(), microphone=(), camera=()");
            // HSTS – only in production (HTTP in dev)
            // res.setHeader("Strict-Transport-Security","max-age=31536000; includeSubDomains");
            chain.doFilter(request, response);
        }
    }
}
