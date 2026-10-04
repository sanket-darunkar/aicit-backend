package com.aicit.repository;

import com.aicit.entity.InstituteUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InstituteUserRepository extends JpaRepository<InstituteUser, Long> {
    Optional<InstituteUser> findByEmail(String email);
    boolean existsByEmail(String email);
    List<InstituteUser> findByInstituteId(Long instituteId);
    List<InstituteUser> findByInstituteIdAndRole(Long instituteId, InstituteUser.Role role);
    void deleteByInstituteId(Long instituteId);
}
