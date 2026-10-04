package com.aicit.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "institutes",
    indexes = {
        @Index(name = "idx_institute_code",   columnList = "institute_code", unique = true),
        @Index(name = "idx_institute_status", columnList = "status"),
        @Index(name = "idx_institute_email",  columnList = "contact_email")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Institute {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "institute_code", nullable = false, unique = true, length = 50)
    private String instituteCode;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 100)
    private String type;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String district;

    @Column(length = 100)
    private String state;

    @Column(name = "pin_code", length = 10)
    private String pinCode;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @NotBlank
    @Column(name = "contact_person_name", nullable = false, length = 255)
    private String contactPersonName;

    @NotBlank @Email
    @Column(name = "contact_email", nullable = false, unique = true, length = 255)
    private String contactEmail;

    @NotBlank
    @Column(name = "contact_mobile", nullable = false, length = 15)
    private String contactMobile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Status status = Status.PENDING_REVIEW;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private AdminUser approvedBy;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING_REVIEW, APPROVED, SUSPENDED, DEACTIVATED, REJECTED
    }
}
