package com.aicit.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs",
    indexes = {
        @Index(name = "idx_audit_actor",  columnList = "actor_type,actor_id"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_entity", columnList = "entity_type,entity_id"),
        @Index(name = "idx_audit_date",   columnList = "created_at")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_type", nullable = false, length = 30)
    private String actorType;   // ADMIN, INSTITUTE_USER, SYSTEM

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Column(nullable = false, length = 100)
    private String action;      // INSTITUTE_APPROVED, CERT_ISSUED, etc.

    @Column(name = "entity_type", length = 50)
    private String entityType;  // Institute, Student, Certificate

    @Column(name = "entity_id")
    private Long entityId;

    @Column(length = 1000)
    private String description;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
