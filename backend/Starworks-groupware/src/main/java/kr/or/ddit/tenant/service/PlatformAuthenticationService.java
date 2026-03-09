package kr.or.ddit.tenant.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import kr.or.ddit.mybatis.mapper.TenantPlatformMapper;
import kr.or.ddit.mybatis.mapper.UsersMapper;
import kr.or.ddit.security.CustomUserDetails;
import kr.or.ddit.tenant.vo.TenantMembershipVO;
import kr.or.ddit.vo.RestLoginVO;
import kr.or.ddit.vo.UsersVO;
import lombok.RequiredArgsConstructor;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
@RequiredArgsConstructor
public class PlatformAuthenticationService {

    private final TenantPlatformMapper tenantPlatformMapper;
    private final UsersMapper usersMapper;
    private final PasswordEncoder passwordEncoder;

    public Authentication authenticate(RestLoginVO request) {
        String identifier = normalizeIdentifier(request);
        if (!StringUtils.hasText(identifier) || !StringUtils.hasText(request.getPassword())) {
            throw new BadCredentialsException("Missing credentials.");
        }

        List<TenantMembershipVO> candidates = tenantPlatformMapper.selectLoginCandidates(identifier, blankToNull(request.getTenantId()));
        if (candidates.isEmpty()) {
            throw new BadCredentialsException("Invalid credentials.");
        }

        TenantMembershipVO selected = candidates.stream()
            .filter(candidate -> passwordMatches(candidate, request.getPassword()))
            .min(Comparator.comparingInt(this::membershipPriority).thenComparing(TenantMembershipVO::getTenantId))
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials."));

        return buildAuthentication(selected.getUserId());
    }

    public Authentication switchTenant(CustomUserDetails currentUser, String tenantId) {
        if (currentUser == null || !StringUtils.hasText(currentUser.getRealUser().getUserEmail())) {
            throw new ResponseStatusException(FORBIDDEN, "A switchable tenant identity was not found.");
        }
        if (!StringUtils.hasText(tenantId)) {
            throw new ResponseStatusException(BAD_REQUEST, "tenantId is required.");
        }

        TenantMembershipVO targetMembership = tenantPlatformMapper.selectMembershipByUserEmailAndTenant(
            currentUser.getRealUser().getUserEmail(),
            tenantId.trim()
        );
        if (targetMembership == null) {
            throw new ResponseStatusException(FORBIDDEN, "You do not belong to the requested tenant.");
        }
        return buildAuthentication(targetMembership.getUserId());
    }

    public Authentication authenticateUserId(String userId) {
        return buildAuthentication(userId);
    }

    private Authentication buildAuthentication(String userId) {
        UsersVO realUser = usersMapper.selectUser(userId);
        if (realUser == null) {
            throw new BadCredentialsException("User was not found.");
        }
        CustomUserDetails principal = new CustomUserDetails(realUser);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private boolean passwordMatches(TenantMembershipVO membership, String rawPassword) {
        UsersVO user = usersMapper.selectUser(membership.getUserId());
        if (user == null || !StringUtils.hasText(user.getUserPswd())) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, user.getUserPswd());
    }

    private int membershipPriority(TenantMembershipVO membership) {
        String role = Objects.toString(membership.getTenantRoleCd(), "").trim().toUpperCase(Locale.ROOT);
        return switch (role) {
            case "OWNER" -> 0;
            case "ADMIN" -> 1;
            default -> 2;
        };
    }

    private String normalizeIdentifier(RestLoginVO request) {
        if (request == null) {
            return null;
        }
        if (StringUtils.hasText(request.getIdentifier())) {
            return request.getIdentifier().trim();
        }
        if (StringUtils.hasText(request.getEmail())) {
            return request.getEmail().trim();
        }
        if (StringUtils.hasText(request.getUsername())) {
            return request.getUsername().trim();
        }
        return null;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
