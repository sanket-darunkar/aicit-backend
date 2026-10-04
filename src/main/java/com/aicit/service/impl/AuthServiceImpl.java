package com.aicit.service.impl;

import com.aicit.dto.request.LoginRequest;
import com.aicit.dto.response.LoginResponse;
import com.aicit.entity.AdminUser;
import com.aicit.entity.InstituteUser;
import com.aicit.exception.BadCredentialsException;
import com.aicit.repository.AdminUserRepository;
import com.aicit.repository.InstituteUserRepository;
import com.aicit.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements com.aicit.service.AuthService {

    private final AdminUserRepository     adminUserRepository;
    private final InstituteUserRepository instituteUserRepository;
    private final PasswordEncoder         passwordEncoder;
    private final JwtUtil                 jwtUtil;

    @Value("${app.jwt.expiration-ms:86400000}")
    private long expirationMs;

    @Override
    @Transactional
    public LoginResponse adminLogin(LoginRequest request) {
        AdminUser admin = adminUserRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password."));

        if (!admin.isActive()) {
            throw new BadCredentialsException("This account has been deactivated.");
        }

        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new BadCredentialsException("Invalid email or password.");
        }

        admin.setLastLoginAt(LocalDateTime.now());
        adminUserRepository.save(admin);

        String token = jwtUtil.generateAdminToken(admin.getEmail(), admin.getRole().name());

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .role(admin.getRole().name())
                .email(admin.getEmail())
                .fullName(admin.getFullName())
                .instituteId(null)
                .instituteName(null)
                .expiresIn(expirationMs / 1000)
                .build();
    }

    @Override
    @Transactional
    public LoginResponse instituteLogin(LoginRequest request) {
        InstituteUser user = instituteUserRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password."));

        if (!user.isActive()) {
            throw new BadCredentialsException("This account has been deactivated.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password.");
        }

        // Check institute status
        var institute = user.getInstitute();
        if (institute.getStatus() != com.aicit.entity.Institute.Status.APPROVED) {
            throw new BadCredentialsException("Your institute is not currently active. Status: "
                    + institute.getStatus().name());
        }

        user.setLastLoginAt(LocalDateTime.now());
        instituteUserRepository.save(user);

        String token = jwtUtil.generateInstituteToken(
                user.getEmail(),
                user.getRole().name(),
                institute.getId(),
                institute.getName());

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .role(user.getRole().name())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .instituteId(institute.getId())
                .instituteName(institute.getName())
                .expiresIn(expirationMs / 1000)
                .build();
    }
}
