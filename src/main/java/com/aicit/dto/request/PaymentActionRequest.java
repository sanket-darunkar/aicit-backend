package com.aicit.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Admin verifies or rejects a batch's payment.
 * {@code reason} is required when rejecting.
 */
@Getter @Setter @NoArgsConstructor
public class PaymentActionRequest {
    private String reason;
}
