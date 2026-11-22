package com.aicit.controller;

import com.aicit.dto.request.CertificateRequestDto;
import com.aicit.dto.response.ApiResponse;
import com.aicit.dto.response.CertificateResponse;
import com.aicit.dto.response.PagedResponse;
import com.aicit.service.CertificateService;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/institute/certificates")
@RequiredArgsConstructor
@Tag(name = "Institute – Certificates", description = "Certificate management for institutes")
@SecurityRequirement(name = "bearerAuth")
public class InstituteCertificateController {

    private final CertificateService certService;

    @GetMapping
    @PreAuthorize("hasAnyRole('INSTITUTE_ADMIN','INSTITUTE_STAFF')")
    @Operation(summary = "List own institute certificates")
    public ResponseEntity<ApiResponse<PagedResponse<CertificateResponse>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Long instituteId = getInstituteId(request);
        return ResponseEntity.ok(ApiResponse.success("Certificates retrieved",
                certService.listByInstitute(instituteId, status, page, size)));
    }

    @PostMapping("/request")
    @PreAuthorize("hasAnyRole('INSTITUTE_ADMIN','INSTITUTE_STAFF')")
    @Operation(summary = "Submit a certificate request")
    public ResponseEntity<ApiResponse<CertificateResponse>> request(
            @Valid @RequestBody CertificateRequestDto req,
            HttpServletRequest request) {
        Long instituteId = getInstituteId(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("Certificate request submitted",
                        certService.requestCertificate(instituteId, req)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('INSTITUTE_ADMIN','INSTITUTE_STAFF')")
    @Operation(summary = "Get certificate by ID (institute-scoped)")
    public ResponseEntity<ApiResponse<CertificateResponse>> getById(
            @PathVariable Long id, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Certificate retrieved",
                certService.getByInstitute(getInstituteId(request), id)));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('INSTITUTE_ADMIN','INSTITUTE_STAFF')")
    @Operation(summary = "Download certificate PDF")
    public ResponseEntity<byte[]> download(
            @PathVariable Long id, HttpServletRequest request) {
        Long instituteId = getInstituteId(request);
        byte[] pdf = certService.downloadPdf(instituteId, id);
        if (pdf == null) {
            return ResponseEntity.noContent().build();
        }
        CertificateResponse cert = certService.getByInstitute(instituteId, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + cert.getCertificateNumber() + ".pdf\"")
                .body(pdf);
    }

    @GetMapping("/{id}/preview")
    @PreAuthorize("hasAnyRole('INSTITUTE_ADMIN','INSTITUTE_STAFF')")
    @Operation(summary = "Preview certificate as image",
               description = "Renders the same PDF used for download as a PNG image for in-browser preview. " +
                             "Only available for ISSUED certificates. Returns 204 if not yet issued.")
    public ResponseEntity<byte[]> preview(
            @PathVariable Long id, HttpServletRequest request) {
        Long instituteId = getInstituteId(request);
        byte[] png = certService.previewImage(instituteId, id);
        if (png == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(png);
    }

    private Long getInstituteId(HttpServletRequest request) {
        Object attr = request.getAttribute("authenticatedInstituteId");
        if (attr == null) throw new IllegalStateException("Institute context not found in token.");
        return (Long) attr;
    }
}
