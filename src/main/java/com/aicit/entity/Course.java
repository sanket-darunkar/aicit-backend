package com.aicit.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "courses",
    indexes = {
        @Index(name = "idx_course_code",     columnList = "code",      unique = true),
        @Index(name = "idx_course_active",   columnList = "is_active"),
        @Index(name = "idx_course_category", columnList = "category")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Course {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String name;

    @NotBlank
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "duration_months")
    private Integer durationMonths;

    /** Raw human duration exactly as in the source catalog (e.g. "45 DAYS", "1.5 MONTHS"). */
    @Column(name = "duration_label", length = 40)
    private String durationLabel;

    /** Normalized duration in days, for sorting/filtering across mixed units. */
    @Column(name = "duration_days")
    private Integer durationDays;

    /** Source serial number from the catalog (reference only, not the PK). */
    @Column(name = "source_sr_no")
    private Integer sourceSrNo;

    @Column(length = 100)
    private String category;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
