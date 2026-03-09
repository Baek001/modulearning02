package kr.or.ddit.tenant.controller;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import kr.or.ddit.security.CustomUserDetails;
import kr.or.ddit.tenant.dto.TenantInvitationCreateRequest;
import kr.or.ddit.tenant.service.TenantPlatformService;
import kr.or.ddit.tenant.service.TenantSecurityService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/rest/platform")
@RequiredArgsConstructor
public class TenantPlatformRestController {

    private final TenantPlatformService tenantPlatformService;
    private final TenantSecurityService tenantSecurityService;

    @PostMapping("/invitations")
    public Map<String, Object> createInvitation(
        @Valid @RequestBody TenantInvitationCreateRequest request,
        Authentication authentication
    ) {
        CustomUserDetails userDetails = tenantSecurityService.requireTenantAdmin(authentication);
        return Map.of(
            "invitation",
            tenantPlatformService.createInvitation(userDetails.getTenantId(), userDetails.getUsername(), request)
        );
    }
}
