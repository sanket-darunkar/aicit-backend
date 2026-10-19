package com.aicit.repository;
import com.aicit.entity.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByIsActiveTrueOrderByNameAsc();
    Page<Course> findByIsActiveTrue(Pageable pageable);
    boolean existsByCode(String code);
    java.util.Optional<Course> findByCode(String code);

    long countByIsActiveTrue();
    long countByIsActiveFalse();

    /**
     * Server-side course search with optional filters, driven by the reusable
     * CourseFinder on the frontend. All params are optional (null = ignore).
     *   search   — matches course name OR code (case-insensitive, partial)
     *   category — exact category match
     *   active   — true/false to restrict by status (null = any)
     * Sorting/pagination come from the Pageable.
     */
    /**
     * Server-side course search with optional filters.
     *
     * NOTE on :pattern — the caller passes either NULL (no text filter) or a
     * ready-made lowercase LIKE pattern like "%tally%". We must NOT build the
     * pattern with CONCAT('%', :search, '%') inside the query: when :search is
     * null, Hibernate binds an untyped/bytea NULL and the concatenation makes
     * the whole expression bytea, producing "function lower(bytea) does not
     * exist". Passing a fully-formed text pattern (or null) keeps the bind
     * firmly typed as text and avoids that entirely.
     */
    @Query("""
        SELECT c FROM Course c
        WHERE (:pattern IS NULL
               OR LOWER(c.name) LIKE :pattern
               OR LOWER(c.code) LIKE :pattern)
          AND (:category IS NULL OR c.category = :category)
          AND (:active   IS NULL OR c.isActive = :active)
        """)
    Page<Course> search(@Param("pattern")  String pattern,
                        @Param("category") String category,
                        @Param("active")   Boolean active,
                        Pageable pageable);

    @Query("SELECT DISTINCT c.category FROM Course c WHERE c.category IS NOT NULL ORDER BY c.category")
    List<String> findDistinctCategories();
}
