package com.aicit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of validating a bulk certificate CSV, returned for confirmation
 * BEFORE any batch/certificate records are created.
 */
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class CsvValidationResponse {

    private int totalRows;
    private int validCount;
    private int invalidCount;

    /** Server-computed = validCount * unitAmount. Never trusted from client. */
    private BigDecimal unitAmount;
    private BigDecimal totalAmount;

    private List<ValidRow>   validRows;
    private List<InvalidRow> invalidRows;

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ValidRow {
        private int    rowNumber;
        private String studentCode;   // institute's student_id
        private String studentName;
        private String courseCode;
        private String courseName;
        private String marks;
        private String grade;
    }

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InvalidRow {
        private int          rowNumber;
        private String       studentCode;
        private String       courseCode;
        private List<String> errors;
    }
}
