package com.aicit.service;

import com.aicit.dto.request.CertificateRequestDto;
import com.aicit.dto.request.PaymentActionRequest;
import com.aicit.dto.request.UtrSubmissionRequest;
import com.aicit.dto.response.BatchResponse;
import com.aicit.dto.response.CsvValidationResponse;
import com.aicit.dto.response.PagedResponse;
import org.springframework.web.multipart.MultipartFile;

public interface BatchCertificateService {

    // ── Institute: create requests ────────────────────────────

    /** SINGLE: validate one student+course, create a 1-item batch (payment PENDING). */
    BatchResponse createSingle(Long instituteId, Long instituteUserId, CertificateRequestDto req);

    /** BULK step 1: validate a CSV and return a preview. Creates NOTHING. */
    CsvValidationResponse validateCsv(Long instituteId, MultipartFile file);

    /** BULK step 2: create a batch from a CSV (re-validates server-side, skips invalid rows). */
    BatchResponse createBulk(Long instituteId, Long instituteUserId, MultipartFile file);

    // ── Institute: payment ────────────────────────────────────

    BatchResponse submitUtr(Long instituteId, Long batchId, UtrSubmissionRequest req);

    // ── Institute: read ───────────────────────────────────────

    PagedResponse<BatchResponse> listByInstitute(Long instituteId, String paymentStatus, int page, int size);
    BatchResponse getByInstitute(Long instituteId, Long batchId);

    // ── Admin: payment verification + processing ──────────────

    PagedResponse<BatchResponse> listAll(String paymentStatus, int page, int size);
    BatchResponse adminGet(Long batchId);
    BatchResponse verifyPayment(Long batchId, Long adminId, PaymentActionRequest req);
    BatchResponse rejectPayment(Long batchId, Long adminId, PaymentActionRequest req);

    /** Generate certificates for all certs in a PAID batch. */
    BatchResponse processBatch(Long batchId, Long adminId);

    // ── Downloads ─────────────────────────────────────────────

    /** ZIP of all issued certificate PDFs in a batch (admin). */
    byte[] downloadBatchZip(Long batchId);

    /** ZIP of all issued certificate PDFs in a batch (institute-scoped). */
    byte[] downloadBatchZipForInstitute(Long instituteId, Long batchId);

    /** CSV template bytes for bulk upload. */
    byte[] csvTemplate();
}
