package com.aicit.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter @NoArgsConstructor
public class UpdateStudentRequest {
    @Size(max = 100)
    private String firstName;
    @Size(max = 100)
    private String middleName;
    @Size(max = 100)
    private String surname;
    private LocalDate dateOfBirth;
    private String gender;
    @Size(max = 20)
    private String aadhaarNumber;
    @Size(min = 10, max = 15)
    private String ownMobile;
    private String addressLine1;
    private String city;
    private String district;
    private String state;
    private String pinCode;
    private String qualification;
    private String status;
    private String notes;
    private boolean removePhoto;
}
