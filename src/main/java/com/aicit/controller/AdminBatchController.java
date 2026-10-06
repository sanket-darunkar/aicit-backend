package com.aicit.controller;

import com.aicit.dto.request.PaymentActionRequest;
import com.aicit.dto.response.ApiResponse;
import com.aicit.dto.response.BatchResponse;
import com.aicit.dto.response.PagedResponse;
import com.aicit.entity.AdminUser;
import com.aicit.repository.AdminUserRepository;
import com.aicit.service.BatchCertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/batches")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MCA_ADMIN')")
@Tag(name = "Admin – Certificate Batches",
     description = "Admin payment verification + batch processing")
@SecurityRequirement(name = "bearerAuth")
public class AdminBatchController {

    private final BatchCertificateService batchService;
    private final AdminUserRepository     adminUserRepository;

    @GetMapping
    @Operation(summary = "List all batches (filterable by payment status)")
    public ResponseEntity<ApiResponse<PagedResponse<BatchResponse>>> list(
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Batches retrieved",
                batchService.listAll(paymentStatus, page, Math.min(size, 100))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get batch details including its certificates")
    public ResponseEntity<ApiResponse<BatchResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Batch retrieved", batchService.adminGet(id)));
    }

    @PostMapping("/{id}/verify-payment")
    @Operation(summary = "Verify the batch's UPI payment (marks PAID)")
    public ResponseEntity<ApiResponse<BatchResponse>> verifyPayment(
            @PathVariable Long id,
            @RequestBody(required = false) PaymentActionRequest req,
            Authentication auth) {
        if (req == null) req = new PaymentActionRequest();
        return ResponseEntity.ok(ApiResponse.success("Payment verified",
                batchService.verifyPayment(id, getAdminId(auth), req)));
    }

    @PostMapping("/{id}/reject-payment")
    @Operation(summary = "Reject the batch's payment")
    public ResponseEntity<ApiResponse<BatchResponse>> rejectPayment(
            @PathVariable Long id,
            @RequestBody(required = false) PaymentActionRequest req,
            Authentication auth) {
        if (req == null) req = new PaymentActionRequest();
        return ResponseEntity.ok(ApiResponse.success("Payment rejected",
                batchService.rejectPayment(id, getAdminId(auth), req)));
    }

    @PostMapping("/{id}/process")
    @Operation(summary = "Process a PAID batch — generates certificate numbers and PDFs")
    public ResponseEntity<ApiResponse<BatchResponse>> process(
            @PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Batch processed",
                batchService.processBatch(id, getAdminId(auth))));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download all issued certificates in the batch as a ZIP")
    public ResponseEntity<byte[]> downloadZip(@PathVariable Long id) {
        byte[] zip = batchService.downloadBatchZip(id);
        if (zip == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"batch-" + id + "-certificates.zip\"")
                .body(zip);
    }

    private Long getAdminId(Authentication auth) {
        return adminUserRepository.findByEmail(auth.getName())
                .map(AdminUser::getId).orElse(0L);
    }
}
