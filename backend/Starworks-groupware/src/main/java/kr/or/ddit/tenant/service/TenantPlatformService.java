package kr.or.ddit.tenant.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import kr.or.ddit.messenger.community.service.CommunityService;
import kr.or.ddit.mybatis.mapper.DepartmentMapper;
import kr.or.ddit.mybatis.mapper.TenantPlatformMapper;
import kr.or.ddit.mybatis.mapper.UsersMapper;
import kr.or.ddit.tenant.dto.AuthSessionResponse;
import kr.or.ddit.tenant.dto.TenantInvitationAcceptRequest;
import kr.or.ddit.tenant.dto.TenantInvitationCreateRequest;
import kr.or.ddit.tenant.dto.TenantSignupRequest;
import kr.or.ddit.tenant.vo.TenantInvitationVO;
import kr.or.ddit.tenant.vo.TenantMembershipVO;
import kr.or.ddit.tenant.vo.TenantVO;
import kr.or.ddit.vo.DepartmentVO;
import kr.or.ddit.vo.UsersVO;
import lombok.RequiredArgsConstructor;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class TenantPlatformService {

    private final TenantPlatformMapper tenantPlatformMapper;
    private final UsersMapper usersMapper;
    private final DepartmentMapper departmentMapper;
    private final PasswordEncoder passwordEncoder;
    private final CommunityService communityService;

    @Transactional
    public TenantMembershipVO signUpOwner(TenantSignupRequest request) {
        validateSignupRequest(request);

        String tenantId = nextId("TNT");
        String companyName = request.getCompanyName().trim();
        String ownerName = request.getOwnerName().trim();
        String ownerEmail = request.getOwnerEmail().trim().toLowerCase(Locale.ROOT);
        String tenantSlug = resolveTenantSlug(request.getWorkspaceSlug(), companyName);
        if (tenantPlatformMapper.selectTenantBySlug(tenantSlug) != null) {
            throw new ResponseStatusException(CONFLICT, "Workspace slug is already in use.");
        }

        TenantVO tenant = new TenantVO();
        tenant.setTenantId(tenantId);
        tenant.setTenantNm(companyName);
        tenant.setTenantSlug(tenantSlug);
        tenant.setStatusCd("ACTIVE");
        tenantPlatformMapper.insertTenant(tenant);

        String deptId = departmentMapper.getNextTopDeptId();
        DepartmentVO rootDepartment = new DepartmentVO();
        rootDepartment.setTenantId(tenantId);
        rootDepartment.setDeptId(deptId);
        rootDepartment.setDeptNm(companyName);
        rootDepartment.setUseYn("Y");
        departmentMapper.insertDepartment(rootDepartment);

        UsersVO owner = new UsersVO();
        owner.setUserId(nextId("USR"));
        owner.setTenantId(tenantId);
        owner.setLoginId(ownerEmail);
        owner.setUserPswd(passwordEncoder.encode(request.getPassword()));
        owner.setUserNm(ownerName);
        owner.setUserEmail(ownerEmail);
        owner.setDeptId(deptId);
        owner.setJbgdCd("J004");
        owner.setUserRole("ROLE_USER");
        owner.setHireYmd(LocalDate.now());
        usersMapper.insertUser(owner);
        tenantPlatformMapper.updateTenantOwnerUserId(tenantId, owner.getUserId());

        TenantMembershipVO membership = new TenantMembershipVO();
        membership.setTenantId(tenantId);
        membership.setUserId(owner.getUserId());
        membership.setTenantRoleCd("OWNER");
        membership.setMembershipStatusCd("ACTIVE");
        tenantPlatformMapper.insertTenantMember(membership);
        tenantPlatformMapper.insertTenantCompanySetting(tenantId, companyName, owner.getUserId(), owner.getUserEmail());

        communityService.syncOrgCommunities(tenantId, owner.getUserId(), true);
        return tenantPlatformMapper.selectMembershipByUserIdAndTenant(owner.getUserId(), tenantId);
    }

    @Transactional
    public TenantInvitationVO createInvitation(String tenantId, String inviterUserId, TenantInvitationCreateRequest request) {
        if (!StringUtils.hasText(tenantId)) {
            throw new ResponseStatusException(BAD_REQUEST, "tenantId is required.");
        }
        if (request == null || !StringUtils.hasText(request.getInviteEmail()) || !StringUtils.hasText(request.getInviteName())) {
            throw new ResponseStatusException(BAD_REQUEST, "Invite email and invite name are required.");
        }
        TenantVO tenant = tenantPlatformMapper.selectTenantById(tenantId);
        if (tenant == null) {
            throw new ResponseStatusException(NOT_FOUND, "Tenant was not found.");
        }

        TenantInvitationVO invitation = new TenantInvitationVO();
        invitation.setInvitationId(nextId("INV"));
        invitation.setTenantId(tenantId);
        invitation.setInviteEmail(request.getInviteEmail().trim().toLowerCase(Locale.ROOT));
        invitation.setInviteName(request.getInviteName().trim());
        invitation.setTenantRoleCd(normalizeTenantRole(request.getTenantRoleCd()));
        invitation.setStatusCd("PENDING");
        invitation.setInvitationToken(UUID.randomUUID().toString().replace("-", ""));
        invitation.setCreatedByUserId(inviterUserId);
        invitation.setExpiresAt(LocalDateTime.now().plusDays(7));
        tenantPlatformMapper.insertInvitation(invitation);
        return tenantPlatformMapper.selectInvitationByToken(invitation.getInvitationToken());
    }

    @Transactional
    public TenantMembershipVO acceptInvitation(TenantInvitationAcceptRequest request) {
        if (request == null || !StringUtils.hasText(request.getToken())) {
            throw new ResponseStatusException(BAD_REQUEST, "Invitation token is required.");
        }
        TenantInvitationVO invitation = tenantPlatformMapper.selectInvitationByToken(request.getToken().trim());
        if (invitation == null || !"PENDING".equalsIgnoreCase(invitation.getStatusCd())) {
            throw new ResponseStatusException(NOT_FOUND, "Invitation was not found.");
        }
        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(BAD_REQUEST, "Invitation has expired.");
        }
        if (!invitation.getInviteEmail().equalsIgnoreCase(request.getUserEmail().trim())) {
            throw new ResponseStatusException(BAD_REQUEST, "Invitation email does not match.");
        }

        String deptId = resolveTenantRootDepartmentId(invitation.getTenantId());
        UsersVO user = new UsersVO();
        user.setUserId(nextId("USR"));
        user.setTenantId(invitation.getTenantId());
        user.setLoginId(request.getUserEmail().trim().toLowerCase(Locale.ROOT));
        user.setUserPswd(passwordEncoder.encode(request.getPassword()));
        user.setUserNm(request.getUserNm().trim());
        user.setUserEmail(request.getUserEmail().trim().toLowerCase(Locale.ROOT));
        user.setDeptId(deptId);
        user.setJbgdCd("J001");
        user.setUserRole("ROLE_USER");
        user.setHireYmd(LocalDate.now());
        usersMapper.insertUser(user);

        TenantMembershipVO membership = new TenantMembershipVO();
        membership.setTenantId(invitation.getTenantId());
        membership.setUserId(user.getUserId());
        membership.setTenantRoleCd(normalizeTenantRole(invitation.getTenantRoleCd()));
        membership.setMembershipStatusCd("ACTIVE");
        tenantPlatformMapper.insertTenantMember(membership);
        tenantPlatformMapper.acceptInvitation(invitation.getInvitationId(), user.getUserId());
        communityService.syncOrgCommunities(invitation.getTenantId(), user.getUserId(), true);

        return tenantPlatformMapper.selectMembershipByUserIdAndTenant(user.getUserId(), invitation.getTenantId());
    }

    @Transactional(readOnly = true)
    public TenantInvitationVO readInvitation(String token) {
        TenantInvitationVO invitation = tenantPlatformMapper.selectInvitationByToken(token);
        if (invitation == null) {
            throw new ResponseStatusException(NOT_FOUND, "Invitation was not found.");
        }
        return invitation;
    }

    @Transactional(readOnly = true)
    public AuthSessionResponse buildSession(UsersVO user) {
        List<TenantMembershipVO> memberships = resolveMemberships(user);
        TenantMembershipVO currentTenant = memberships.stream()
            .filter(membership -> membership.getTenantId().equals(user.getTenantId()))
            .findFirst()
            .orElseGet(() -> tenantPlatformMapper.selectMembershipByUserIdAndTenant(user.getUserId(), user.getTenantId()));
        return new AuthSessionResponse(user, currentTenant, memberships);
    }

    @Transactional(readOnly = true)
    public AuthSessionResponse buildSession(String userId) {
        UsersVO user = usersMapper.selectUser(userId);
        if (user == null) {
            throw new ResponseStatusException(NOT_FOUND, "User was not found.");
        }
        return buildSession(user);
    }

    private List<TenantMembershipVO> resolveMemberships(UsersVO user) {
        if (user == null) {
            return List.of();
        }
        if (StringUtils.hasText(user.getUserEmail())) {
            return tenantPlatformMapper.selectMembershipsByUserEmail(user.getUserEmail());
        }
        return tenantPlatformMapper.selectMembershipsByUserId(user.getUserId());
    }

    private String resolveTenantRootDepartmentId(String tenantId) {
        List<DepartmentVO> departments = departmentMapper.selectDepartmentListByTenant(tenantId);
        return departments.stream()
            .filter(department -> !StringUtils.hasText(department.getUpDeptId()))
            .map(DepartmentVO::getDeptId)
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tenant root department was not found."));
    }

    private void validateSignupRequest(TenantSignupRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Signup payload is required.");
        }
        if (!StringUtils.hasText(request.getCompanyName())
            || !StringUtils.hasText(request.getOwnerName())
            || !StringUtils.hasText(request.getOwnerEmail())
            || !StringUtils.hasText(request.getPassword())) {
            throw new ResponseStatusException(BAD_REQUEST, "Company, owner, email, and password are required.");
        }
        if (request.getCompanyName().trim().length() < 2 || request.getCompanyName().trim().length() > 200) {
            throw new ResponseStatusException(BAD_REQUEST, "Company name must be between 2 and 200 characters.");
        }
        if (request.getOwnerName().trim().length() < 2 || request.getOwnerName().trim().length() > 120) {
            throw new ResponseStatusException(BAD_REQUEST, "Owner name must be between 2 and 120 characters.");
        }
        if (request.getOwnerEmail().trim().length() > 255) {
            throw new ResponseStatusException(BAD_REQUEST, "Owner email must be 255 characters or fewer.");
        }
        if (request.getPassword().length() < 8 || request.getPassword().length() > 72) {
            throw new ResponseStatusException(BAD_REQUEST, "Password must be between 8 and 72 characters.");
        }
        if (StringUtils.hasText(request.getWorkspaceSlug())) {
            String normalizedSlug = normalizeSlugValue(request.getWorkspaceSlug());
            if (!StringUtils.hasText(normalizedSlug)) {
                throw new ResponseStatusException(BAD_REQUEST, "Workspace slug can contain only letters, numbers, and hyphens.");
            }
            if (normalizedSlug.length() < 3 || normalizedSlug.length() > 63) {
                throw new ResponseStatusException(BAD_REQUEST, "Workspace slug must be between 3 and 63 characters.");
            }
        }
    }

    private String resolveTenantSlug(String requestedSlug, String companyName) {
        String slug = normalizeSlugValue(StringUtils.hasText(requestedSlug) ? requestedSlug : companyName);
        if (!StringUtils.hasText(slug)) {
            slug = "workspace-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toLowerCase(Locale.ROOT);
        }
        return slug;
    }

    private String normalizeSlugValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
    }

    private String normalizeTenantRole(String tenantRoleCd) {
        String role = StringUtils.hasText(tenantRoleCd) ? tenantRoleCd.trim().toUpperCase(Locale.ROOT) : "MEMBER";
        return switch (role) {
            case "OWNER", "ADMIN" -> role;
            default -> "MEMBER";
        };
    }

    private String nextId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }
}
