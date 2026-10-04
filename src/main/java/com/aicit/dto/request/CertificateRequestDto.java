package com.aicit.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor
public class CertificateRequestDto {
    @NotNull
    private Long studentId;
    @NotNull
    private Long courseId;
    private String marks;
    private String grade;
}
