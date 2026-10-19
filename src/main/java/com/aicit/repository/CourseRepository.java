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
    @Query("""
        SELECT c FROM Course c
        WHERE (:search   IS NULL
               OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(c.code) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:category IS NULL OR c.category = :category)
          AND (:active   IS NULL OR c.isActive = :active)
        """)
    Page<Course> search(@Param("search")   String search,
                        @Param("category") String category,
                        @Param("active")   Boolean active,
                        Pageable pageable);

    @Query("SELECT DISTINCT c.category FROM Course c WHERE c.category IS NOT NULL ORDER BY c.category")
    List<String> findDistinctCategories();
}
