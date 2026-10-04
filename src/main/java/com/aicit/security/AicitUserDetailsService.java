package com.aicit.security;

import com.aicit.entity.AdminUser;
import com.aicit.entity.InstituteUser;
import com.aicit.repository.AdminUserRepository;
import com.aicit.repository.InstituteUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Unified UserDetailsService that handles both admin and institute users.
 * Role prefix determines which table to query.
 */
@Service
@RequiredArgsConstructor
public class AicitUserDetailsService implements UserDetailsService {

    private final AdminUserRepository      adminUserRepository;
    private final InstituteUserRepository  instituteUserRepository;

    /** Called by Spring Security's DaoAuthenticationProvider (uses email only) */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Try admin first, then institute user
        var adminOpt = adminUserRepository.findByEmail(email);
        if (adminOpt.isPresent()) {
            return buildAdminDetails(adminOpt.get());
        }
        var instOpt = instituteUserRepository.findByEmail(email);
        if (instOpt.isPresent()) {
            return buildInstituteDetails(instOpt.get());
        }
        throw new UsernameNotFoundException("User not found: " + email);
    }

    /** Called by JwtAuthenticationFilter — uses role to pick the right table */
    public UserDetails loadUserByUsernameAndRole(String email, String role) {
        if (role != null && (role.equals("SUPER_ADMIN") || role.equals("MCA_ADMIN"))) {
            return adminUserRepository.findByEmail(email)
                    .filter(AdminUser::isActive)
                    .map(this::buildAdminDetails)
                    .orElse(null);
        }
        return instituteUserRepository.findByEmail(email)
                .filter(InstituteUser::isActive)
                .map(this::buildInstituteDetails)
                .orElse(null);
    }

    private UserDetails buildAdminDetails(AdminUser admin) {
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + admin.getRole().name())
        );
        return User.builder()
                .username(admin.getEmail())
                .password(admin.getPassword())
                .authorities(authorities)
                .accountLocked(!admin.isActive())
                .build();
    }

    private UserDetails buildInstituteDetails(InstituteUser user) {
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );
        return User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(authorities)
                .accountLocked(!user.isActive())
                .build();
    }
}
