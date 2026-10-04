package com.aicit.service;

import com.aicit.dto.request.CreateStudentRequest;
import com.aicit.dto.request.UpdateStudentRequest;
import com.aicit.dto.response.PagedResponse;
import com.aicit.dto.response.StudentResponse;
import org.springframework.web.multipart.MultipartFile;

public interface StudentService {
    // Institute-scoped
    StudentResponse       create(Long instituteId, Long createdByUserId,
                                 CreateStudentRequest request, MultipartFile photo);
    PagedResponse<StudentResponse> list(Long instituteId, String search,
                                        String status, int page, int size);
    StudentResponse       getById(Long instituteId, Long studentId);
    StudentResponse       update(Long instituteId, Long studentId,
                                 UpdateStudentRequest request, MultipartFile photo);
    void                  delete(Long instituteId, Long studentId);
    byte[]                getPhoto(Long instituteId, Long studentId);

    // Admin platform-wide (no institute fence)
    PagedResponse<StudentResponse> listAll(String search, Long instituteId, int page, int size);
    StudentResponse       adminGetById(Long studentId);
}
