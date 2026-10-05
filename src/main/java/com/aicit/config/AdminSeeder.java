package com.aicit.config;

import com.aicit.entity.AdminUser;
import com.aicit.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "test", "prod"})
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder     passwordEncoder;

    @Value("${app.seed.admin.email}")
    private String adminEmail;

    @Value("${app.seed.admin.password}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        adminUserRepository.findByEmail(adminEmail).ifPresentOrElse(
            existing -> {
                // Keep the seed admin's password in sync with the configured
                // value so updating AICIT_ADMIN_PASSWORD + redeploy resets it.
                if (!passwordEncoder.matches(adminPassword, existing.getPassword())) {
                    existing.setPassword(passwordEncoder.encode(adminPassword));
                    adminUserRepository.save(existing);
                    log.info("Admin user password re-synced from configuration: {}", adminEmail);
                } else {
                    log.info("Admin user already exists: {}", adminEmail);
                }
            },
            () -> {
                AdminUser admin = AdminUser.builder()
                        .email(adminEmail)
                        .password(passwordEncoder.encode(adminPassword))
                        .fullName("AICIT Super Admin")
                        .role(AdminUser.Role.SUPER_ADMIN)
                        .isActive(true)
                        .build();
                adminUserRepository.save(admin);
                log.info("Seeded admin user: {}", adminEmail);
            }
        );
    }
}
