package com.aicit.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor
public class ResetPasswordRequest {
    private String newPassword;   // if null, a random password is generated
}
