package com.aicit.repository;

import com.aicit.entity.Certificate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    Optional<Certificate> findByCertificateNumber(String number);
    Optional<Certificate> findByIdAndInstituteId(Long id, Long instituteId);
    long countByInstituteId(Long instituteId);
    long countByInstituteIdAndStatus(Long instituteId, Certificate.Status status);
    long countByStatus(Certificate.Status status);

    // Duplicate check — active certs for same student + course
    @Query("SELECT c FROM Certificate c WHERE c.student.id = :studentId AND c.course.id = :courseId " +
           "AND c.status NOT IN :excludedStatuses")
    List<Certificate> findActiveByStudentAndCourse(
            @Param("studentId")       Long studentId,
            @Param("courseId")        Long courseId,
            @Param("excludedStatuses") java.util.List<Certificate.Status> excludedStatuses);

    // Max cert number for year prefix (to generate next sequential number)
    @Query("SELECT c.certificateNumber FROM Certificate c WHERE c.certificateNumber LIKE :prefix%")
    List<String> findCertNumbersByPrefix(@Param("prefix") String prefix);

    @Query("""
        SELECT c FROM Certificate c
        WHERE c.institute.id = :instituteId
          AND (:status IS NULL OR c.status = :status)
        ORDER BY c.createdAt DESC
        """)
    Page<Certificate> findByInstituteIdFiltered(
            @Param("instituteId") Long instituteId,
            @Param("status") Certificate.Status status,
            Pageable pageable);

    @Query(value = """
        SELECT c.id, c.certificate_number, c.institute_id, c.student_id,
               c.enrollment_id, c.course_id, c.marks, c.grade, c.issue_date,
               c.status, c.rejection_reason, c.reviewed_by, c.reviewed_at,
               c.pdf_data, c.pdf_generated_at, c.revoked_at, c.revoke_reason,
               c.created_at, c.updated_at
        FROM certificates c
        JOIN students s ON s.id = c.student_id
        WHERE (:status IS NULL OR c.status = :status)
          AND (:search IS NULL
               OR c.certificate_number LIKE '%' || :search || '%'
               OR LOWER(s.first_name) LIKE LOWER('%' || :search || '%')
               OR LOWER(s.surname)    LIKE LOWER('%' || :search || '%'))
        ORDER BY c.created_at DESC
        """,
        countQuery = """
        SELECT COUNT(*) FROM certificates c
        JOIN students s ON s.id = c.student_id
        WHERE (:status IS NULL OR c.status = :status)
          AND (:search IS NULL
               OR c.certificate_number LIKE '%' || :search || '%'
               OR LOWER(s.first_name) LIKE LOWER('%' || :search || '%')
               OR LOWER(s.surname)    LIKE LOWER('%' || :search || '%'))
        """,
        nativeQuery = true)
    Page<Certificate> findAllFiltered(
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable);
}
