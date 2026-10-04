package com.aicit.config;

import com.aicit.security.AicitUserDetailsService;
import com.aicit.security.JwtAuthenticationEntryPoint;
import com.aicit.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter     jwtAuthFilter;
    private final JwtAuthenticationEntryPoint jwtEntryPoint;
    private final AicitUserDetailsService     userDetailsService;
    private final CorsConfigurationSource     corsConfigurationSource;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        var provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtEntryPoint))
            .authorizeHttpRequests(auth -> auth
                // CORS pre-flight
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Public endpoints
                .requestMatchers(HttpMethod.GET,  "/api/public/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/public/**").permitAll()
                // Auth endpoints
                .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                // Swagger
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                 "/api-docs", "/api-docs/**",
                                 "/v3/api-docs", "/v3/api-docs/**").permitAll()
                // Admin — requires SUPER_ADMIN or MCA_ADMIN
                .requestMatchers("/api/admin/**")
                    .hasAnyRole("SUPER_ADMIN", "MCA_ADMIN")
                // Institute — requires INSTITUTE_ADMIN or INSTITUTE_STAFF
                .requestMatchers("/api/institute/**")
                    .hasAnyRole("INSTITUTE_ADMIN", "INSTITUTE_STAFF")
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
