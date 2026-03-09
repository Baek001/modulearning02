package kr.or.ddit.tenant.service;

import java.util.Locale;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import kr.or.ddit.security.CustomUserDetails;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class TenantSecurityService {

    public CustomUserDetails requireCurrentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Authentication is required.");
        }
        return userDetails;
    }

    public CustomUserDetails requireCurrentUser() {
        return requireCurrentUser(SecurityContextHolder.getContext().getAuthentication());
    }

    public CustomUserDetails requireTenantUser(Authentication authentication) {
        CustomUserDetails userDetails = requireCurrentUser(authentication);
        if (!StringUtils.hasText(userDetails.getTenantId())) {
            throw new ResponseStatusException(FORBIDDEN, "A tenant context is required.");
        }
        return userDetails;
    }

    public CustomUserDetails requireTenantAdmin(Authentication authentication) {
        CustomUserDetails userDetails = requireTenantUser(authentication);
        if (!isTenantAdmin(userDetails)) {
            throw new ResponseStatusException(FORBIDDEN, "Tenant administrator access is required.");
        }
        return userDetails;
    }

    public boolean isTenantAdmin(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return false;
        }
        return isTenantAdmin(userDetails);
    }

    public boolean isTenantAdmin(CustomUserDetails userDetails) {
        if (userDetails == null) {
            return false;
        }
        String role = StringUtils.hasText(userDetails.getTenantRoleCd())
            ? userDetails.getTenantRoleCd().trim().toUpperCase(Locale.ROOT)
            : "";
        return "OWNER".equals(role)
            || "ADMIN".equals(role)
            || userDetails.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equalsIgnoreCase(authority.getAuthority()) || "ADMIN".equalsIgnoreCase(authority.getAuthority()));
    }
}
