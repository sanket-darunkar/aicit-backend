package com.aicit.controller;

import com.aicit.dto.request.CertificateRequestDto;
import com.aicit.dto.request.UtrSubmissionRequest;
import com.aicit.dto.response.ApiResponse;
import com.aicit.dto.response.BatchResponse;
import com.aicit.dto.response.CsvValidationResponse;
import com.aicit.dto.response.PagedResponse;
import com.aicit.repository.InstituteUserRepository;
import com.aicit.service.BatchCertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/institute/batches")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('INSTITUTE_ADMIN','INSTITUTE_STAFF')")
@Tag(name = "Institute – Certificate Batches",
     description = "Single + bulk certificate requests with manual UPI payment")
@SecurityRequirement(name = "bearerAuth")
public class InstituteBatchController {

    private final BatchCertificateService batchService;
    private final InstituteUserRepository instituteUserRepository;

    // ── Create ────────────────────────────────────────────────

    @PostMapping("/single")
    @Operation(summary = "Create a SINGLE certificate request (₹250). Payment required before processing.")
    public ResponseEntity<ApiResponse<BatchResponse>> createSingle(
            @Valid @RequestBody CertificateRequestDto req,
            Authentication auth, HttpServletRequest request) {
        Long instituteId = getInstituteId(request);
        Long userId      = getUserId(auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("Single certificate request created",
                        batchService.createSingle(instituteId, userId, req)));
    }

    @PostMapping(value = "/bulk/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Validate a bulk CSV and preview valid/invalid rows. Creates nothing.")
    public ResponseEntity<ApiResponse<CsvValidationResponse>> validateCsv(
            @RequestParam("file") MultipartFile file, HttpServletRequest request) {
        Long instituteId = getInstituteId(request);
        return ResponseEntity.ok(ApiResponse.success("CSV validated",
                batchService.validateCsv(instituteId, file)));
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create a BULK batch from a CSV (valid rows only). Amount = valid × ₹250.")
    public ResponseEntity<ApiResponse<BatchResponse>> createBulk(
            @RequestParam("file") MultipartFile file,
            Authentication auth, HttpServletRequest request) {
        Long instituteId = getInstituteId(request);
        Long userId      = getUserId(auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("Bulk batch created",
                        batchService.createBulk(instituteId, userId, file)));
    }

    @GetMapping("/template")
    @Operation(summary = "Download the bulk CSV template")
    public ResponseEntity<byte[]> csvTemplate() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"aicit-bulk-certificate-template.csv\"")
                .body(batchService.csvTemplate());
    }

    // ── Payment ───────────────────────────────────────────────

    @PostMapping("/{id}/utr")
    @Operation(summary = "Submit UTR / transaction id for a batch's UPI payment")
    public ResponseEntity<ApiResponse<BatchResponse>> submitUtr(
            @PathVariable Long id,
            @Valid @RequestBody UtrSubmissionRequest req,
            HttpServletRequest request) {
        Long instituteId = getInstituteId(request);
        return ResponseEntity.ok(ApiResponse.success("UTR submitted — awaiting verification",
                batchService.submitUtr(instituteId, id, req)));
    }

    // ── Read ──────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List own institute batches")
    public ResponseEntity<ApiResponse<PagedResponse<BatchResponse>>> list(
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Long instituteId = getInstituteId(request);
        return ResponseEntity.ok(ApiResponse.success("Batches retrieved",
                batchService.listByInstitute(instituteId, paymentStatus, page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get batch details (institute-scoped) including its certificates")
    public ResponseEntity<ApiResponse<BatchResponse>> getById(
            @PathVariable Long id, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Batch retrieved",
                batchService.getByInstitute(getInstituteId(request), id)));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download all issued certificates in the batch as a ZIP")
    public ResponseEntity<byte[]> downloadZip(
            @PathVariable Long id, HttpServletRequest request) {
        Long instituteId = getInstituteId(request);
        byte[] zip = batchService.downloadBatchZipForInstitute(instituteId, id);
        if (zip == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"batch-" + id + "-certificates.zip\"")
                .body(zip);
    }

    // ── Helpers ───────────────────────────────────────────────

    private Long getInstituteId(HttpServletRequest request) {
        Object attr = request.getAttribute("authenticatedInstituteId");
        if (attr == null) throw new IllegalStateException("Institute context not found in token.");
        return (Long) attr;
    }

    private Long getUserId(String email) {
        return instituteUserRepository.findByEmail(email).map(u -> u.getId()).orElse(null);
    }
}
