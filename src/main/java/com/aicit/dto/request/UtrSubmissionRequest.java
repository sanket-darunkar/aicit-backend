package com.aicit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Institute submits a UTR / transaction id for a batch's manual UPI payment.
 */
@Getter @Setter @NoArgsConstructor
public class UtrSubmissionRequest {
    @NotBlank(message = "UTR / transaction id is required")
    @Size(min = 6, max = 50, message = "UTR must be between 6 and 50 characters")
    private String utrNumber;
}
