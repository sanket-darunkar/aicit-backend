package com.aicit.repository;

import com.aicit.entity.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    boolean existsByInstituteIdAndStudentId(Long instituteId, String studentId);

    /** Institute-scoped lookup by PK — data fence enforced */
    Optional<Student> findByIdAndInstituteId(Long id, Long instituteId);

    long countByInstituteId(Long instituteId);

    /** Institute-scoped search */
    @Query(value = """
        SELECT s.id, s.aadhaar_number, s.address_line1, s.city, s.created_at,
               s.created_by, s.date_of_birth, s.district, s.first_name, s.gender,
               s.institute_id, s.middle_name, s.notes, s.own_mobile, s.photo_data,
               s.photo_mime_type, s.pin_code, s.qualification, s.state, s.status,
               s.student_id, s.surname, s.updated_at
        FROM students s
        WHERE s.institute_id = :instituteId
          AND (:status IS NULL OR s.status = :status)
          AND (:search IS NULL
               OR LOWER(s.first_name) LIKE LOWER('%' || :search || '%')
               OR LOWER(s.surname)    LIKE LOWER('%' || :search || '%')
               OR LOWER(s.student_id) LIKE LOWER('%' || :search || '%')
               OR s.own_mobile        LIKE '%' || :search || '%')
        ORDER BY s.created_at DESC
        """,
        countQuery = """
        SELECT COUNT(*) FROM students s
        WHERE s.institute_id = :instituteId
          AND (:status IS NULL OR s.status = :status)
          AND (:search IS NULL
               OR LOWER(s.first_name) LIKE LOWER('%' || :search || '%')
               OR LOWER(s.surname)    LIKE LOWER('%' || :search || '%')
               OR LOWER(s.student_id) LIKE LOWER('%' || :search || '%')
               OR s.own_mobile        LIKE '%' || :search || '%')
        """,
        nativeQuery = true)
    Page<Student> findByInstituteIdFiltered(
            @Param("instituteId") Long   instituteId,
            @Param("search")      String search,
            @Param("status")      String status,
            Pageable pageable);

    /** Admin platform-wide search */
    @Query(value = """
        SELECT s.id, s.aadhaar_number, s.address_line1, s.city, s.created_at,
               s.created_by, s.date_of_birth, s.district, s.first_name, s.gender,
               s.institute_id, s.middle_name, s.notes, s.own_mobile, s.photo_data,
               s.photo_mime_type, s.pin_code, s.qualification, s.state, s.status,
               s.student_id, s.surname, s.updated_at
        FROM students s
        WHERE (:instituteId IS NULL OR s.institute_id = :instituteId)
          AND (:search IS NULL
               OR LOWER(s.first_name) LIKE LOWER('%' || :search || '%')
               OR LOWER(s.surname)    LIKE LOWER('%' || :search || '%')
               OR LOWER(s.student_id) LIKE LOWER('%' || :search || '%'))
        ORDER BY s.created_at DESC
        """,
        countQuery = """
        SELECT COUNT(*) FROM students s
        WHERE (:instituteId IS NULL OR s.institute_id = :instituteId)
          AND (:search IS NULL
               OR LOWER(s.first_name) LIKE LOWER('%' || :search || '%')
               OR LOWER(s.surname)    LIKE LOWER('%' || :search || '%')
               OR LOWER(s.student_id) LIKE LOWER('%' || :search || '%'))
        """,
        nativeQuery = true)
    Page<Student> findAllFiltered(
            @Param("search")      String search,
            @Param("instituteId") Long   instituteId,
            Pageable pageable);
}
