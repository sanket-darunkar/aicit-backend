package com.aicit.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates unique AICIT certificate numbers in the format:
 *   AICIT-{YEAR}-{6-DIGIT-SEQUENCE}
 *   e.g. AICIT-2026-000001
 *
 * Uses a DB sequence via a counter that is incremented atomically.
 * The sequence resets logic is handled at the service layer by checking
 * the max existing certificate number for the current year.
 */
@Component
public class CertificateNumberGenerator {

    public String generate(long sequenceNumber) {
        int year = LocalDate.now().getYear();
        return String.format("AICIT-%d-%06d", year, sequenceNumber);
    }
}
