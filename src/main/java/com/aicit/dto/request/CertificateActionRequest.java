package com.aicit.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor
public class CertificateActionRequest {
    private String reason;           // for reject / revoke
    private String marks;            // optional override on approve
    private String grade;            // optional override on approve
    private String customCertNumber; // optional: admin-supplied cert number; auto-generated if blank
}
