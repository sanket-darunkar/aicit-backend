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
    long countByStatus(Institute.Status status);

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
