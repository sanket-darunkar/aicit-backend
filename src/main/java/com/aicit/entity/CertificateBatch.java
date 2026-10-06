package com.aicit.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A payment batch grouping one or more {@link Certificate} requests.
 *
 * <p>Both single and bulk flows create a batch:
 * <ul>
 *   <li>SINGLE → one batch with {@code certificateCount == 1}</li>
 *   <li>BULK   → one batch with {@code certificateCount == valid rows}</li>
 * </ul>
 * The batch owns the manual-UPI payment lifecycle. Certificates can only be
 * approved/processed once the batch's {@code paymentStatus == PAID}.
 */
@Entity
@Table(name = "certificate_batches",
    indexes = {
        @Index(name = "idx_batch_code",       columnList = "batch_code", unique = true),
        @Index(name = "idx_batch_institute",  columnList = "institute_id"),
        @Index(name = "idx_batch_pay_status", columnList = "payment_status"),
        @Index(name = "idx_batch_status",     columnList = "batch_status")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CertificateBatch {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Public identifier, e.g. BATCH-2026-000001 */
    @Column(name = "batch_code", nullable = false, unique = true, length = 40)
    private String batchCode;

    /** DATA FENCE — every query must filter by this */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "institute_id", nullable = false)
    private Institute institute;

    @Column(name = "certificate_count", nullable = false)
    @Builder.Default
    private Integer certificateCount = 0;

    /** Server-computed = certificateCount * unitAmount. Never trusted from client. */
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "unit_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal unitAmount = new BigDecimal("250");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private Type type = Type.BULK;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "batch_status", nullable = false, length = 30)
    @Builder.Default
    private BatchStatus batchStatus = BatchStatus.DRAFT;

    @Column(name = "utr_number", length = 50)
    private String utrNumber;

    @Column(name = "payment_rejection_reason", length = 500)
    private String paymentRejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by")
    private InstituteUser submittedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private AdminUser verifiedBy;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum Type { SINGLE, BULK }

    /** Request lifecycle of the batch. */
    public enum BatchStatus {
        DRAFT, VALIDATED, SUBMITTED, PROCESSING, GENERATED, COMPLETED, REJECTED
    }
}
