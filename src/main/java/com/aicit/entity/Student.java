package com.aicit.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "students",
    indexes = {
        @Index(name = "idx_student_institute", columnList = "institute_id"),
        @Index(name = "idx_student_status",    columnList = "institute_id,status"),
        @Index(name = "idx_student_name",      columnList = "first_name,surname"),
        @Index(name = "idx_student_mobile",    columnList = "own_mobile")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_student_id_inst",
                          columnNames = {"institute_id", "student_id"})
    })
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Student {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** DATA FENCE — every query must filter by this */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "institute_id", nullable = false)
    private Institute institute;

    @NotBlank
    @Column(name = "student_id", nullable = false, length = 50)
    private String studentId;

    @NotBlank
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String surname;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 20)
    private String gender;

    /** Sensitive – masked in public responses */
    @Column(name = "aadhaar_number", length = 20)
    private String aadhaarNumber;

    @NotBlank
    @Column(name = "own_mobile", nullable = false, length = 15)
    private String ownMobile;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String district;

    @Column(length = 100)
    private String state;

    @Column(name = "pin_code", length = 10)
    private String pinCode;

    @Column(length = 100)
    private String qualification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(name = "photo_data", columnDefinition = "bytea")
    private byte[] photoData;

    @Column(name = "photo_mime_type", length = 20)
    private String photoMimeType;

    @Column(length = 2000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private InstituteUser createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum Status { ACTIVE, INACTIVE, COMPLETED, DROPPED }
}
