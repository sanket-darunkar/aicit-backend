package com.aicit.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "certificates",
    indexes = {
        @Index(name = "idx_cert_number",    columnList = "certificate_number", unique = true),
        @Index(name = "idx_cert_institute", columnList = "institute_id"),
        @Index(name = "idx_cert_student",   columnList = "student_id"),
        @Index(name = "idx_cert_status",    columnList = "status")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Certificate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Generated at approval time — null until approved */
    @Column(name = "certificate_number", unique = true, length = 30)
    private String certificateNumber;

    /** DATA FENCE — every query must filter by this */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "institute_id", nullable = false)
    private Institute institute;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id")
    private Enrollment enrollment;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(length = 20)
    private String marks;

    @Column(length = 10)
    private String grade;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Status status = Status.REQUESTED;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private AdminUser reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "pdf_data", columnDefinition = "bytea")
    private byte[] pdfData;

    @Column(name = "pdf_generated_at")
    private LocalDateTime pdfGeneratedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoke_reason", length = 500)
    private String revokeReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum Status {
        REQUESTED, UNDER_REVIEW, APPROVED, ISSUED, REJECTED, REVOKED
    }
}
