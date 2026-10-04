package com.aicit.dto.response;

import com.aicit.entity.Student;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Builder
public class StudentResponse {
    private Long   id;
    private Long   instituteId;
    private String instituteName;
    private String studentId;
    private String firstName;
    private String middleName;
    private String surname;
    private String fullName;
    private LocalDate dateOfBirth;
    private String gender;
    private String aadhaarNumberMasked;  // never expose raw Aadhaar
    private String ownMobile;
    private String addressLine1;
    private String city;
    private String district;
    private String state;
    private String pinCode;
    private String qualification;
    private String status;
    private boolean hasPhoto;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StudentResponse from(Student s) {
        String maskedAadhaar = null;
        if (s.getAadhaarNumber() != null && s.getAadhaarNumber().length() >= 4) {
            maskedAadhaar = "XXXX-XXXX-" + s.getAadhaarNumber()
                    .substring(s.getAadhaarNumber().length() - 4);
        }
        return StudentResponse.builder()
                .id(s.getId())
                .instituteId(s.getInstitute().getId())
                .instituteName(s.getInstitute().getName())
                .studentId(s.getStudentId())
                .firstName(s.getFirstName())
                .middleName(s.getMiddleName())
                .surname(s.getSurname())
                .fullName(s.getFirstName() + " " +
                          (s.getMiddleName() != null ? s.getMiddleName() + " " : "") +
                          s.getSurname())
                .dateOfBirth(s.getDateOfBirth())
                .gender(s.getGender())
                .aadhaarNumberMasked(maskedAadhaar)
                .ownMobile(s.getOwnMobile())
                .addressLine1(s.getAddressLine1())
                .city(s.getCity())
                .district(s.getDistrict())
                .state(s.getState())
                .pinCode(s.getPinCode())
                .qualification(s.getQualification())
                .status(s.getStatus().name())
                .hasPhoto(s.getPhotoData() != null)
                .notes(s.getNotes())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
