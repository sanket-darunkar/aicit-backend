package com.aicit.service;

import com.aicit.dto.request.InstituteActionRequest;
import com.aicit.dto.request.InstituteRegistrationRequest;
import com.aicit.dto.response.InstituteResponse;
import com.aicit.dto.response.PagedResponse;
import com.aicit.entity.Institute;

public interface InstituteService {
    InstituteResponse register(InstituteRegistrationRequest request);
    PagedResponse<InstituteResponse> listAll(Institute.Status status, String search, int page, int size);
    InstituteResponse getById(Long id);
    InstituteResponse approve(Long id, Long adminId);
    InstituteResponse reject(Long id, Long adminId, InstituteActionRequest request);
    InstituteResponse suspend(Long id, Long adminId, InstituteActionRequest request);
    InstituteResponse activate(Long id, Long adminId);
    InstituteResponse deactivate(Long id, Long adminId);
    void delete(Long id, Long adminId);
}
