package com.aicit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter @AllArgsConstructor @Builder
public class LoginResponse {
    private String  token;
    private String  tokenType;
    private String  role;
    private String  email;
    private String  fullName;
    private Long    instituteId;
    private String  instituteName;
    private long    expiresIn;
}
