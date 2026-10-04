package com.aicit.repository;

import com.aicit.entity.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByIsActiveTrueOrderByNameAsc();
    Page<Course> findByIsActiveTrue(Pageable pageable);
    boolean existsByCode(String code);
}
