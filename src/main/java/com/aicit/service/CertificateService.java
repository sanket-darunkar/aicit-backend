package com.aicit.service;

import com.aicit.dto.request.CertificateActionRequest;
import com.aicit.dto.request.CertificateRequestDto;
import com.aicit.dto.response.CertificateResponse;
import com.aicit.dto.response.PagedResponse;

public interface CertificateService {
    // Institute
    CertificateResponse requestCertificate(Long instituteId, CertificateRequestDto req);
    PagedResponse<CertificateResponse> listByInstitute(Long instituteId, String status, int page, int size);
    CertificateResponse getByInstitute(Long instituteId, Long certId);
    byte[] downloadPdf(Long instituteId, Long certId);

    // Admin
    PagedResponse<CertificateResponse> listAll(String status, String search, int page, int size);
    CertificateResponse adminGet(Long certId);
    CertificateResponse approve(Long certId, Long adminId, CertificateActionRequest req);
    CertificateResponse reject(Long certId, Long adminId, CertificateActionRequest req);
    CertificateResponse revoke(Long certId, Long adminId, CertificateActionRequest req);
    byte[] adminDownloadPdf(Long certId);

    /**
     * Force-regenerates the PDF for an ISSUED certificate using the current
     * template/layout code, overwriting any previously stored PDF bytes.
     * Use this to refresh certificates after a template or positioning change.
     */
    CertificateResponse regeneratePdf(Long certId, Long adminId);
}
