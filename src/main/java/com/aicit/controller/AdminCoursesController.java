package com.aicit.controller;

import com.aicit.dto.response.ApiResponse;
import com.aicit.dto.response.CourseResponse;
import com.aicit.dto.response.PagedResponse;
import com.aicit.entity.Course;
import com.aicit.exception.DataConflictException;
import com.aicit.exception.ResourceNotFoundException;
import com.aicit.repository.CourseRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/courses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MCA_ADMIN')")
@Tag(name = "Admin – Courses", description = "Course management")
@SecurityRequirement(name = "bearerAuth")
public class AdminCoursesController {

    private final CourseRepository courseRepository;

    @GetMapping
    @Operation(summary = "List all courses")
    public ResponseEntity<ApiResponse<List<CourseResponse>>> list() {
        List<CourseResponse> courses = courseRepository.findAll().stream()
                .map(CourseResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success("Courses retrieved", courses));
    }

    @PostMapping
    @Operation(summary = "Create a new course")
    public ResponseEntity<ApiResponse<CourseResponse>> create(@Valid @RequestBody CourseRequest req) {
        if (courseRepository.existsByCode(req.getCode())) {
            throw new DataConflictException("A course with code '" + req.getCode() + "' already exists.");
        }
        Course course = Course.builder()
                .name(req.getName())
                .code(req.getCode().toUpperCase())
                .description(req.getDescription())
                .durationMonths(req.getDurationMonths())
                .category(req.getCategory())
                .isActive(true)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Course created", CourseResponse.from(courseRepository.save(course))));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a course")
    public ResponseEntity<ApiResponse<CourseResponse>> update(
            @PathVariable Long id, @RequestBody CourseRequest req) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
        if (req.getName()          != null) course.setName(req.getName());
        if (req.getDescription()   != null) course.setDescription(req.getDescription());
        if (req.getDurationMonths()!= null) course.setDurationMonths(req.getDurationMonths());
        if (req.getCategory()      != null) course.setCategory(req.getCategory());
        return ResponseEntity.ok(ApiResponse.success("Course updated",
                CourseResponse.from(courseRepository.save(course))));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activate or deactivate a course")
    public ResponseEntity<ApiResponse<CourseResponse>> toggleStatus(
            @PathVariable Long id, @RequestParam boolean active) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
        course.setActive(active);
        return ResponseEntity.ok(ApiResponse.success(
                active ? "Course activated" : "Course deactivated",
                CourseResponse.from(courseRepository.save(course))));
    }

    @Getter @NoArgsConstructor
    public static class CourseRequest {
        private String  name;
        private String  code;
        private String  description;
        private Integer durationMonths;
        private String  category;
    }
}
