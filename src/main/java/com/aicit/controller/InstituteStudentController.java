package com.aicit.controller;

import com.aicit.dto.request.CreateStudentRequest;
import com.aicit.dto.request.UpdateStudentRequest;
import com.aicit.dto.response.ApiResponse;
import com.aicit.dto.response.PagedResponse;
import com.aicit.dto.response.StudentResponse;
import com.aicit.repository.InstituteUserRepository;
import com.aicit.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/institute/students")
@RequiredArgsConstructor
@Tag(name = "Institute – Students", description = "Student management (institute-scoped, JWT required)")
@SecurityRequirement(name = "bearerAuth")
public class InstituteStudentController {

    private final StudentService          studentService;
    private final InstituteUserRepository instituteUserRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('INSTITUTE_ADMIN','INSTITUTE_STAFF')")
    @Operation(summary = "List own institute students")
    public ResponseEntity<ApiResponse<PagedResponse<StudentResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        Long instituteId = getInstituteId(request);
        return ResponseEntity.ok(ApiResponse.success("Students retrieved",
                studentService.list(instituteId, search, status, page, Math.min(size, 100))));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('INSTITUTE_ADMIN','INSTITUTE_STAFF')")
    @Operation(summary = "Add a new student",
               description = "Send as multipart/form-data. `data` = JSON CreateStudentRequest. `photo` = optional JPG/PNG (max 2 MB).")
    public ResponseEntity<ApiResponse<StudentResponse>> create(
            @RequestPart("data") @Valid CreateStudentRequest req,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            HttpServletRequest request,
            Authentication auth) {

        Long instituteId = getInstituteId(request);
        Long userId = getUserId(auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("Student created",
                        studentService.create(instituteId, userId, req, photo)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('INSTITUTE_ADMIN','INSTITUTE_STAFF')")
    @Operation(summary = "Get student by ID (institute-scoped)")
    public ResponseEntity<ApiResponse<StudentResponse>> getById(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long instituteId = getInstituteId(request);
        return ResponseEntity.ok(ApiResponse.success("Student retrieved",
                studentService.getById(instituteId, id)));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('INSTITUTE_ADMIN','INSTITUTE_STAFF')")
    @Operation(summary = "Update student (institute-scoped)")
    public ResponseEntity<ApiResponse<StudentResponse>> update(
            @PathVariable Long id,
            @RequestPart("data") @Valid UpdateStudentRequest req,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            HttpServletRequest request) {
        Long instituteId = getInstituteId(request);
        return ResponseEntity.ok(ApiResponse.success("Student updated",
                studentService.update(instituteId, id, req, photo)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('INSTITUTE_ADMIN')")
    @Operation(summary = "Delete student (INSTITUTE_ADMIN only)")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long instituteId = getInstituteId(request);
        studentService.delete(instituteId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/photo")
    @PreAuthorize("hasAnyRole('INSTITUTE_ADMIN','INSTITUTE_STAFF')")
    @Operation(summary = "Get student photo")
    public ResponseEntity<byte[]> getPhoto(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long instituteId = getInstituteId(request);
        byte[] photo = studentService.getPhoto(instituteId, id);
        if (photo == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(photo);
    }

    // ── Helpers ───────────────────────────────────────────────

    private Long getInstituteId(HttpServletRequest request) {
        Object attr = request.getAttribute("authenticatedInstituteId");
        if (attr == null) throw new IllegalStateException("Institute context not found in token.");
        return (Long) attr;
    }

    private Long getUserId(String email) {
        return instituteUserRepository.findByEmail(email)
                .map(u -> u.getId())
                .orElse(null);
    }
}
