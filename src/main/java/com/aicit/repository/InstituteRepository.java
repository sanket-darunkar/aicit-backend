package com.aicit.repository;

import com.aicit.entity.Institute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InstituteRepository extends JpaRepository<Institute, Long> {

    Optional<Institute> findByInstituteCode(String code);
    boolean existsByContactEmail(String email);
    boolean existsByInstituteCode(String code);
    long countByStatus(Institute.Status status);

    /**
     * Highest numeric suffix currently used by any AICIT-INST-#### code.
     * Used to generate the next code safely — count() is unsafe because it
     * breaks once any institute has been deleted (rows < max suffix), which
     * caused duplicate-key errors on registration. Returns 0 when none exist.
     */
    @Query(value = """
        SELECT COALESCE(MAX(CAST(SUBSTRING(institute_code FROM '[0-9]+$') AS INTEGER)), 0)
        FROM institutes
        WHERE institute_code ~ '^AICIT-INST-[0-9]+$'
        """, nativeQuery = true)
    int findMaxInstituteCodeSuffix();

    @Query(value = """
        SELECT * FROM institutes i
        WHERE (:status IS NULL OR i.status = :status)
          AND (:search IS NULL
               OR LOWER(i.name) LIKE LOWER('%' || :search || '%')
               OR LOWER(i.contact_email) LIKE LOWER('%' || :search || '%')
               OR LOWER(COALESCE(i.city,'')) LIKE LOWER('%' || :search || '%'))
        ORDER BY i.created_at DESC
        """,
        countQuery = """
        SELECT COUNT(*) FROM institutes i
        WHERE (:status IS NULL OR i.status = :status)
          AND (:search IS NULL
               OR LOWER(i.name) LIKE LOWER('%' || :search || '%')
               OR LOWER(i.contact_email) LIKE LOWER('%' || :search || '%')
               OR LOWER(COALESCE(i.city,'')) LIKE LOWER('%' || :search || '%'))
        """,
        nativeQuery = true)
    Page<Institute> findByFilters(
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable);
}
