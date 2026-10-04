package com.aicit.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor
public class InstituteRegistrationRequest {
    @NotBlank @Size(max = 255)
    private String instituteName;
    private String instituteType;
    @NotBlank @Size(max = 255)
    private String addressLine1;
    private String addressLine2;
    @NotBlank
    private String city;
    @NotBlank
    private String district;
    @NotBlank
    private String state;
    @NotBlank
    private String pinCode;
    private String websiteUrl;
    @NotBlank @Size(max = 255)
    private String contactPersonName;
    @NotBlank @Email
    private String contactEmail;
    @NotBlank @Size(min = 10, max = 15)
    private String contactMobile;
}
