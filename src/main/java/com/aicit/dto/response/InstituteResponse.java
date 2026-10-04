package com.aicit.dto.response;

import com.aicit.entity.Institute;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter @Builder
public class InstituteResponse {
    private Long   id;
    private String instituteCode;
    private String name;
    private String type;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String district;
    private String state;
    private String pinCode;
    private String websiteUrl;
    private String contactPersonName;
    private String contactEmail;
    private String contactMobile;
    private String status;
    private String rejectionReason;
    private String suspensionReason;
    private LocalDateTime approvedAt;
    private LocalDateTime suspendedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static InstituteResponse from(Institute i) {
        return InstituteResponse.builder()
                .id(i.getId())
                .instituteCode(i.getInstituteCode())
                .name(i.getName())
                .type(i.getType())
                .addressLine1(i.getAddressLine1())
                .addressLine2(i.getAddressLine2())
                .city(i.getCity())
                .district(i.getDistrict())
                .state(i.getState())
                .pinCode(i.getPinCode())
                .websiteUrl(i.getWebsiteUrl())
                .contactPersonName(i.getContactPersonName())
                .contactEmail(i.getContactEmail())
                .contactMobile(i.getContactMobile())
                .status(i.getStatus().name())
                .rejectionReason(i.getRejectionReason())
                .suspensionReason(i.getSuspensionReason())
                .approvedAt(i.getApprovedAt())
                .suspendedAt(i.getSuspendedAt())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .build();
    }
}
