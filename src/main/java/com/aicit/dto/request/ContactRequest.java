package com.aicit.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor
public class ContactRequest {
    @NotBlank @Size(max = 255)
    private String name;
    @NotBlank @Email
    private String email;
    private String mobile;
    @NotBlank @Size(max = 255)
    private String subject;
    @NotBlank @Size(max = 2000)
    private String message;
}
