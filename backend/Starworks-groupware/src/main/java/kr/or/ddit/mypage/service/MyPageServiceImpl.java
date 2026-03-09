package kr.or.ddit.mypage.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import kr.or.ddit.comm.file.FileFolderType;
import kr.or.ddit.comm.file.service.impl.FileUploadServiceImpl;
import kr.or.ddit.mypage.dto.MyOnboardingResponse;
import kr.or.ddit.mypage.dto.MyPasswordChangeRequest;
import kr.or.ddit.mypage.dto.MyProfileUpdateRequest;
import kr.or.ddit.mybatis.mapper.DepartmentMapper;
import kr.or.ddit.mybatis.mapper.JobGradeMapper;
import kr.or.ddit.mybatis.mapper.MyPageMapper;
import kr.or.ddit.mybatis.mapper.UsersMapper;
import kr.or.ddit.vo.DepartmentVO;
import kr.or.ddit.vo.JobGradeVO;
import kr.or.ddit.vo.UsersVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MyPageServiceImpl implements MyPageService {

    private final MyPageMapper mapper;
    private final UsersMapper usersMapper;
    private final DepartmentMapper departmentMapper;
    private final JobGradeMapper jobGradeMapper;
    private final FileUploadServiceImpl fileService;
    private final PasswordEncoder passwordEncoder;
    private final UserOnboardingStatusService userOnboardingStatusService;

    @Override
    public MyOnboardingResponse readOnboarding(String userId) {
        UsersVO user = requireExistingUser(userId);
        List<DepartmentVO> departments = departmentMapper.selectDepartmentListByTenant(user.getTenantId());
        List<JobGradeVO> jobGrades = jobGradeMapper.selectJobGeadeList();

        return new MyOnboardingResponse(
            user,
            departments,
            jobGrades,
            jobGrades.isEmpty(),
            userOnboardingStatusService.isComplete(user)
        );
    }

    @Override
    public UsersVO updateUserInfo(String userId, MyProfileUpdateRequest request) {
        return saveUserInfo(userId, request, false);
    }

    @Override
    public UsersVO completeOnboarding(String userId, MyProfileUpdateRequest request) {
        return saveUserInfo(userId, request, true);
    }

    @Override
    public void changePassword(String userId, MyPasswordChangeRequest request) {
        UsersVO existingUser = requireExistingUser(userId);

        if (request == null
            || request.getCurrentPassword() == null
            || request.getNewPassword() == null
            || request.getNewPassword().trim().length() < 4) {
            throw new IllegalArgumentException("비밀번호 입력값이 올바르지 않습니다.");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), existingUser.getUserPswd())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        mapper.updateUserPassword(userId, passwordEncoder.encode(request.getNewPassword().trim()));
    }

    private UsersVO saveUserInfo(String userId, MyProfileUpdateRequest request, boolean onboardingMode) {
        UsersVO mergedUser = mergeUser(requireExistingUser(userId), request, onboardingMode);

        if (request != null && request.getProfileImage() != null && !request.getProfileImage().isEmpty()) {
            mergedUser.setFileList(List.of(request.getProfileImage()));
            fileService.saveFileS3(mergedUser, FileFolderType.PROFILE.toString());
        }

        int updateRows = mapper.updateUserInfo(mergedUser);
        log.info("Updated mypage profile for userId={} rows={}", userId, updateRows);
        return requireExistingUser(userId);
    }

    private UsersVO mergeUser(UsersVO existingUser, MyProfileUpdateRequest request, boolean onboardingMode) {
        if (request == null) {
            request = new MyProfileUpdateRequest();
        }

        if (request.getUserNm() != null) {
            existingUser.setUserNm(normalizeNullableText(request.getUserNm()));
        }
        if (request.getUserEmail() != null) {
            existingUser.setUserEmail(normalizeEmail(request.getUserEmail()));
        }
        if (request.getUserTelno() != null) {
            existingUser.setUserTelno(normalizeNullableText(request.getUserTelno()));
        }
        if (request.getExtTel() != null) {
            existingUser.setExtTel(normalizeNullableText(request.getExtTel()));
        }
        if (request.getDeptId() != null) {
            existingUser.setDeptId(resolveDepartmentId(existingUser.getTenantId(), request.getDeptId()));
        }

        String resolvedJobGradeCd = resolveJobGradeCode(request);
        if (resolvedJobGradeCd != null) {
            existingUser.setJbgdCd(resolvedJobGradeCd);
        }

        validateRequiredProfileFields(existingUser, onboardingMode);
        return existingUser;
    }

    private void validateRequiredProfileFields(UsersVO user, boolean onboardingMode) {
        if (!StringUtils.hasText(user.getUserNm())) {
            throw new ResponseStatusException(BAD_REQUEST, "이름을 입력해 주세요.");
        }
        if (!StringUtils.hasText(user.getUserEmail())) {
            throw new ResponseStatusException(BAD_REQUEST, "이메일 정보를 확인할 수 없습니다.");
        }
        if (onboardingMode && !StringUtils.hasText(user.getDeptId())) {
            throw new ResponseStatusException(BAD_REQUEST, "부서를 선택해 주세요.");
        }
        if (onboardingMode && !StringUtils.hasText(user.getUserTelno())) {
            throw new ResponseStatusException(BAD_REQUEST, "휴대폰 번호를 입력해 주세요.");
        }
        if (onboardingMode && !StringUtils.hasText(user.getJbgdCd())) {
            throw new ResponseStatusException(BAD_REQUEST, "직급을 선택하거나 입력해 주세요.");
        }
    }

    private String resolveDepartmentId(String tenantId, String deptId) {
        String normalizedDeptId = normalizeNullableText(deptId);
        if (!StringUtils.hasText(normalizedDeptId)) {
            return null;
        }

        DepartmentVO department = departmentMapper.selectDepartmentByTenant(tenantId, normalizedDeptId);
        if (department == null) {
            throw new ResponseStatusException(BAD_REQUEST, "선택한 부서를 찾을 수 없습니다.");
        }
        return department.getDeptId();
    }

    private String resolveJobGradeCode(MyProfileUpdateRequest request) {
        String jbgdCd = normalizeNullableText(request.getJbgdCd());
        if (StringUtils.hasText(jbgdCd)) {
            JobGradeVO jobGrade = jobGradeMapper.selectJobGrade(jbgdCd);
            if (jobGrade == null) {
                throw new ResponseStatusException(BAD_REQUEST, "선택한 직급을 찾을 수 없습니다.");
            }
            return jobGrade.getJbgdCd();
        }

        String jobGradeName = normalizeNullableText(request.getJobGradeName());
        if (StringUtils.hasText(jobGradeName)) {
            return ensureJobGrade(jobGradeName).getJbgdCd();
        }

        return null;
    }

    private JobGradeVO ensureJobGrade(String jobGradeName) {
        JobGradeVO existing = jobGradeMapper.selectJobGradeByName(jobGradeName);
        if (existing != null) {
            return existing;
        }

        JobGradeVO created = new JobGradeVO();
        created.setJbgdCd(nextJobGradeId());
        created.setJbgdNm(jobGradeName);
        created.setApprAtrzYn("N");
        created.setUseYn("Y");
        jobGradeMapper.insertJobGrade(created);
        return jobGradeMapper.selectJobGrade(created.getJbgdCd());
    }

    private UsersVO requireExistingUser(String userId) {
        UsersVO existingUser = usersMapper.selectUser(userId);
        if (existingUser == null) {
            throw new ResponseStatusException(NOT_FOUND, "사용자 정보를 찾을 수 없습니다.");
        }
        return existingUser;
    }

    private String normalizeEmail(String value) {
        String normalized = normalizeNullableText(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String nextJobGradeId() {
        return "JBGD_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }
}
