package com.aicit.dto.response;

import com.aicit.entity.CertificateBatch;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * View of a certificate batch, optionally including its certificates.
 */
@Getter @Builder
public class BatchResponse {
    private Long          id;
    private String        batchCode;
    private Long          instituteId;
    private String        instituteName;
    private String        type;             // SINGLE | BULK
    private Integer       certificateCount;
    private BigDecimal    unitAmount;
    private BigDecimal    totalAmount;
    private String        paymentStatus;
    private String        batchStatus;
    private String        utrNumber;
    private String        paymentRejectionReason;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Populated only on detail views */
    private List<CertificateResponse> certificates;

    public static BatchResponse from(CertificateBatch b) {
        return base(b).build();
    }

    public static BatchResponse withCertificates(CertificateBatch b,
                                                 List<CertificateResponse> certs) {
        return base(b).certificates(certs).build();
    }

    private static BatchResponseBuilder base(CertificateBatch b) {
        return BatchResponse.builder()
                .id(b.getId())
                .batchCode(b.getBatchCode())
                .instituteId(b.getInstitute().getId())
                .instituteName(b.getInstitute().getName())
                .type(b.getType().name())
                .certificateCount(b.getCertificateCount())
                .unitAmount(b.getUnitAmount())
                .totalAmount(b.getTotalAmount())
                .paymentStatus(b.getPaymentStatus().name())
                .batchStatus(b.getBatchStatus().name())
                .utrNumber(b.getUtrNumber())
                .paymentRejectionReason(b.getPaymentRejectionReason())
                .paidAt(b.getPaidAt())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt());
    }
}
