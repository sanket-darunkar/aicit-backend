package com.aicit.entity;

/**
 * Payment lifecycle for a certificate request (single or bulk).
 * Shared by both {@link CertificateBatch} and {@link Certificate}.
 *
 * Manual UPI flow:
 *   PENDING              → batch created, no UTR yet
 *   UTR_SUBMITTED        → institute submitted a UTR / transaction id
 *   VERIFICATION_PENDING → awaiting admin review (alias state for queued verification)
 *   PAID                 → admin verified the payment; certificates may now be processed
 *   REJECTED             → admin rejected the payment
 */
public enum PaymentStatus {
    PENDING,
    UTR_SUBMITTED,
    VERIFICATION_PENDING,
    PAID,
    REJECTED
}
