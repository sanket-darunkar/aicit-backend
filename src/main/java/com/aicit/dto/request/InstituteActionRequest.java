package com.aicit.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor
public class InstituteActionRequest {
    private String reason;  // optional reason for rejection/suspension
}
