package com.aicit.repository;

import com.aicit.entity.CertificateVerificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CertificateVerificationLogRepository extends JpaRepository<CertificateVerificationLog, Long> {
    Page<CertificateVerificationLog> findByCertificateId(Long certId, Pageable pageable);
    long countByCertificateId(Long certId);
}
