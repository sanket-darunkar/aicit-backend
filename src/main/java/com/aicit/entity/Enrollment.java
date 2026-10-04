package com.aicit.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "enrollments",
    indexes = {
        @Index(name = "idx_enroll_student",   columnList = "student_id"),
        @Index(name = "idx_enroll_institute", columnList = "institute_id"),
        @Index(name = "idx_enroll_course",    columnList = "course_id"),
        @Index(name = "idx_enroll_status",    columnList = "status")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_enrollment", columnNames = {"student_id","course_id"})
    })
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Enrollment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /** DATA FENCE */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institute_id", nullable = false)
    private Institute institute;

    @Column(name = "admission_date", nullable = false)
    private LocalDate admissionDate;

    @Column(name = "batch_time", length = 50)
    private String batchTime;

    @Column(name = "total_fees", precision = 10, scale = 2)
    private BigDecimal totalFees;

    @Column(name = "fees_paid", precision = 10, scale = 2)
    private BigDecimal feesPaid;

    @Column(name = "receipt_number", length = 50)
    private String receiptNumber;

    @Column(name = "receipt_date")
    private LocalDate receiptDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Status status = Status.ENROLLED;

    @Column(length = 2000)
    private String notes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum Status { ENROLLED, COMPLETED, DROPPED, EXAM_FORM_SUBMITTED }
}
