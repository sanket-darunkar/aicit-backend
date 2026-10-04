package com.aicit.service.impl;

import com.aicit.dto.request.CreateStudentRequest;
import com.aicit.dto.request.UpdateStudentRequest;
import com.aicit.dto.response.PagedResponse;
import com.aicit.dto.response.StudentResponse;
import com.aicit.entity.Institute;
import com.aicit.entity.InstituteUser;
import com.aicit.entity.Student;
import com.aicit.exception.DataConflictException;
import com.aicit.exception.ResourceNotFoundException;
import com.aicit.repository.InstituteRepository;
import com.aicit.repository.InstituteUserRepository;
import com.aicit.repository.StudentRepository;
import com.aicit.service.StudentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentServiceImpl implements StudentService {

    private static final long    MAX_PHOTO_SIZE = 2 * 1024 * 1024L; // 2 MB
    private static final String  ALLOWED_TYPES  = "image/jpeg,image/png";

    private final StudentRepository     studentRepository;
    private final InstituteRepository   instituteRepository;
    private final InstituteUserRepository instituteUserRepository;

    // ── Student ID generation ────────────────────────────────
    private String generateStudentId(Long instituteId, String instituteCode) {
        long count = studentRepository.countByInstituteId(instituteId) + 1;
        String year = String.valueOf(LocalDateTime.now().getYear());
        return instituteCode + "-" + year + "-" + String.format("%03d", count);
    }

    // ── Institute-scoped operations ──────────────────────────

    @Override
    @Transactional
    public StudentResponse create(Long instituteId, Long createdByUserId,
                                  CreateStudentRequest req, MultipartFile photo) {
        Institute institute = findInstituteOrThrow(instituteId);
        InstituteUser createdBy = null;
        if (createdByUserId != null) {
            createdBy = instituteUserRepository.findById(createdByUserId).orElse(null);
        }

        String studentId = generateStudentId(instituteId, institute.getInstituteCode());

        Student student = Student.builder()
                .institute(institute)
                .studentId(studentId)
                .firstName(req.getFirstName())
                .middleName(req.getMiddleName())
                .surname(req.getSurname())
                .dateOfBirth(req.getDateOfBirth())
                .gender(req.getGender())
                .aadhaarNumber(req.getAadhaarNumber())
                .ownMobile(req.getOwnMobile())
                .addressLine1(req.getAddressLine1())
                .city(req.getCity())
                .district(req.getDistrict())
                .state(req.getState())
                .pinCode(req.getPinCode())
                .qualification(req.getQualification())
                .notes(req.getNotes())
                .status(Student.Status.ACTIVE)
                .createdBy(createdBy)
                .build();

        applyPhoto(student, photo);
        return StudentResponse.from(studentRepository.save(student));
    }

    @Override
    public PagedResponse<StudentResponse> list(Long instituteId, String search,
                                                String statusStr, int page, int size) {
        String statusParam = (statusStr != null && !statusStr.isBlank()) ? statusStr.toUpperCase() : null;
        String searchParam = StringUtils.hasText(search) ? search : null;
        Page<Student> result = studentRepository.findByInstituteIdFiltered(
                instituteId, searchParam, statusParam, PageRequest.of(page, size));
        return PagedResponse.from(result.map(StudentResponse::from));
    }

    @Override
    public StudentResponse getById(Long instituteId, Long studentId) {
        return StudentResponse.from(findOrThrow(instituteId, studentId));
    }

    @Override
    @Transactional
    public StudentResponse update(Long instituteId, Long studentId,
                                  UpdateStudentRequest req, MultipartFile photo) {
        Student student = findOrThrow(instituteId, studentId);

        if (StringUtils.hasText(req.getFirstName())) student.setFirstName(req.getFirstName());
        if (req.getMiddleName()  != null)  student.setMiddleName(req.getMiddleName());
        if (StringUtils.hasText(req.getSurname())) student.setSurname(req.getSurname());
        if (req.getDateOfBirth() != null)  student.setDateOfBirth(req.getDateOfBirth());
        if (req.getGender()      != null)  student.setGender(req.getGender());
        if (req.getAadhaarNumber() != null) student.setAadhaarNumber(req.getAadhaarNumber());
        if (StringUtils.hasText(req.getOwnMobile())) student.setOwnMobile(req.getOwnMobile());
        if (req.getAddressLine1() != null) student.setAddressLine1(req.getAddressLine1());
        if (req.getCity()        != null)  student.setCity(req.getCity());
        if (req.getDistrict()    != null)  student.setDistrict(req.getDistrict());
        if (req.getState()       != null)  student.setState(req.getState());
        if (req.getPinCode()     != null)  student.setPinCode(req.getPinCode());
        if (req.getQualification() != null) student.setQualification(req.getQualification());
        if (req.getNotes()       != null)  student.setNotes(req.getNotes());
        if (StringUtils.hasText(req.getStatus())) {
            student.setStatus(Student.Status.valueOf(req.getStatus()));
        }
        if (req.isRemovePhoto()) {
            student.setPhotoData(null);
            student.setPhotoMimeType(null);
        }
        applyPhoto(student, photo);

        return StudentResponse.from(studentRepository.save(student));
    }

    @Override
    @Transactional
    public void delete(Long instituteId, Long studentId) {
        Student student = findOrThrow(instituteId, studentId);
        studentRepository.delete(student);
    }

    @Override
    public byte[] getPhoto(Long instituteId, Long studentId) {
        return findOrThrow(instituteId, studentId).getPhotoData();
    }

    // ── Admin platform-wide ──────────────────────────────────

    @Override
    public PagedResponse<StudentResponse> listAll(String search, Long filterInstituteId,
                                                   int page, int size) {
        String searchParam = StringUtils.hasText(search) ? search : null;
        Page<Student> result = studentRepository.findAllFiltered(
                searchParam, filterInstituteId, PageRequest.of(page, size));
        return PagedResponse.from(result.map(StudentResponse::from));
    }

    @Override
    public StudentResponse adminGetById(Long studentId) {
        return StudentResponse.from(studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId)));
    }

    // ── Helpers ──────────────────────────────────────────────

    private Student findOrThrow(Long instituteId, Long studentId) {
        return studentRepository.findByIdAndInstituteId(studentId, instituteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found or does not belong to your institute."));
    }

    private Institute findInstituteOrThrow(Long id) {
        return instituteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Institute", id));
    }

    private void applyPhoto(Student student, MultipartFile photo) {
        if (photo == null || photo.isEmpty()) return;
        String mime = photo.getContentType();
        if (mime == null || !ALLOWED_TYPES.contains(mime)) {
            throw new IllegalStateException("Photo must be JPEG or PNG.");
        }
        if (photo.getSize() > MAX_PHOTO_SIZE) {
            throw new IllegalStateException("Photo must be smaller than 2 MB.");
        }
        try {
            student.setPhotoData(photo.getBytes());
            student.setPhotoMimeType(mime);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read photo file.");
        }
    }

    private Student.Status parseStatus(String s) {
        if (!StringUtils.hasText(s)) return null;
        try { return Student.Status.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
