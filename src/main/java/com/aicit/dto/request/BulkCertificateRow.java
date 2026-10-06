package com.aicit.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One parsed row from a bulk certificate CSV upload.
 * References an EXISTING student by the institute's own student code.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BulkCertificateRow {
    /** Institute's own student code (students.student_id), NOT the DB PK */
    private String studentId;
    /** Course code (courses.code) */
    private String courseCode;
    private String marks;
    private String grade;
}
