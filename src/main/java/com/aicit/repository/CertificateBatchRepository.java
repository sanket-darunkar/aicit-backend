package com.aicit.repository;

import com.aicit.entity.CertificateBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateBatchRepository extends JpaRepository<CertificateBatch, Long> {

    Optional<CertificateBatch> findByBatchCode(String batchCode);

    /** Institute-scoped lookups — data fence enforced */
    Optional<CertificateBatch> findByIdAndInstituteId(Long id, Long instituteId);

    /** Max batch code for a year prefix, to generate the next sequential code */
    @Query("SELECT b.batchCode FROM CertificateBatch b WHERE b.batchCode LIKE :prefix%")
    List<String> findBatchCodesByPrefix(@Param("prefix") String prefix);

    // ── Institute-scoped list ─────────────────────────────────

    /** All batches for an institute, newest first (no payment filter). */
    @Query("SELECT b FROM CertificateBatch b WHERE b.institute.id = :instituteId ORDER BY b.createdAt DESC")
    Page<CertificateBatch> findByInstituteId(
            @Param("instituteId") Long instituteId,
            Pageable pageable);

    /**
     * Institute batches filtered by a specific payment status.
     * NOTE: never pass null here — Hibernate cannot resolve the type of a null
     * enum parameter in JPQL, causing a PSQLException.  Call findByInstituteId
     * instead when no filter is needed.
     */
    @Query("""
        SELECT b FROM CertificateBatch b
        WHERE b.institute.id = :instituteId
          AND b.paymentStatus = :paymentStatus
        ORDER BY b.createdAt DESC
        """)
    Page<CertificateBatch> findByInstituteFiltered(
            @Param("instituteId")   Long instituteId,
            @Param("paymentStatus") com.aicit.entity.PaymentStatus paymentStatus,
            Pageable pageable);

    // ── Admin (unscoped) list ─────────────────────────────────

    /** All batches, newest first (no payment filter). */
    @Query("SELECT b FROM CertificateBatch b ORDER BY b.createdAt DESC")
    Page<CertificateBatch> findAllBatches(Pageable pageable);

    /**
     * All batches filtered by a specific payment status.
     * NOTE: never pass null here — see findByInstituteFiltered note above.
     */
    @Query("""
        SELECT b FROM CertificateBatch b
        WHERE b.paymentStatus = :paymentStatus
        ORDER BY b.createdAt DESC
        """)
    Page<CertificateBatch> findAllFiltered(
            @Param("paymentStatus") com.aicit.entity.PaymentStatus paymentStatus,
            Pageable pageable);
}
