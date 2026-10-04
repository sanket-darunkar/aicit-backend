package com.aicit.dto.response;

import com.aicit.entity.Certificate;
import com.aicit.service.PdfGeneratorService;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Builder
public class CertificateResponse {
    private Long          id;
    private String        certificateNumber;
    private Long          instituteId;
    private String        instituteName;
    private Long          studentId;
    private String        studentName;
    private String        courseName;
    private String        certificateType;  // "AICIT" | "TYPING" — derived from course name
    private String        marks;
    private String        grade;
    private LocalDate     issueDate;
    private String        status;
    private String        rejectionReason;
    private LocalDateTime reviewedAt;
    private boolean       hasPdf;
    private LocalDateTime pdfGeneratedAt;
    private LocalDateTime revokedAt;
    private String        revokeReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CertificateResponse from(Certificate c) {
        return CertificateResponse.builder()
                .id(c.getId())
                .certificateNumber(c.getCertificateNumber())
                .instituteId(c.getInstitute().getId())
                .instituteName(c.getInstitute().getName())
                .studentId(c.getStudent().getId())
                .studentName(c.getStudent().getFirstName() + " " + c.getStudent().getSurname())
                .courseName(c.getCourse().getName())
                .certificateType(PdfGeneratorService.isTypingCourse(c.getCourse().getName()) ? "TYPING" : "AICIT")
                .marks(c.getMarks())
                .grade(c.getGrade())
                .issueDate(c.getIssueDate())
                .status(c.getStatus().name())
                .rejectionReason(c.getRejectionReason())
                .reviewedAt(c.getReviewedAt())
                .hasPdf(c.getPdfData() != null)
                .pdfGeneratedAt(c.getPdfGeneratedAt())
                .revokedAt(c.getRevokedAt())
                .revokeReason(c.getRevokeReason())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    /** Public verification — only safe fields */
    public static CertificateResponse publicView(Certificate c) {
        return CertificateResponse.builder()
                .certificateNumber(c.getCertificateNumber())
                .studentName(c.getStudent().getFirstName() + " " + c.getStudent().getSurname())
                .courseName(c.getCourse().getName())
                .instituteName(c.getInstitute().getName())
                .grade(c.getGrade())
                .issueDate(c.getIssueDate())
                .status(c.getStatus().name())
                .build();
    }
}
