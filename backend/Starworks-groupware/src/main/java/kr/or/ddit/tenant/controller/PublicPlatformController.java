package kr.or.ddit.tenant.controller;

import java.util.Map;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kr.or.ddit.tenant.dto.PublicSignupConfigResponse;
import kr.or.ddit.tenant.dto.TenantInvitationAcceptRequest;
import kr.or.ddit.tenant.dto.TenantSignupRequest;
import kr.or.ddit.tenant.vo.TenantMembershipVO;
import kr.or.ddit.tenant.service.PublicSignupGuardService;
import kr.or.ddit.tenant.service.TenantPlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Validated
@RequestMapping("/public")
@RequiredArgsConstructor
@Slf4j
public class PublicPlatformController {

    private final TenantPlatformService tenantPlatformService;
    private final PublicSignupGuardService publicSignupGuardService;

    @GetMapping("/signup/config")
    public PublicSignupConfigResponse signupConfig() {
        return publicSignupGuardService.readSignupConfig();
    }

    @PostMapping("/signup")
    public Map<String, Object> signup(@Valid @RequestBody TenantSignupRequest request, HttpServletRequest httpServletRequest) {
        publicSignupGuardService.validateOwnerSignup(request, httpServletRequest);
        TenantMembershipVO membership = tenantPlatformService.signUpOwner(request);
        log.info(
            "Public owner signup created tenantId={} ownerEmail={} clientIp={}",
            membership.getTenantId(),
            request.getOwnerEmail(),
            httpServletRequest == null ? "unknown" : httpServletRequest.getRemoteAddr()
        );
        return Map.of("tenant", membership);
    }

    @GetMapping("/invitations/{token}")
    public Map<String, Object> invitation(@PathVariable String token) {
        return Map.of("invitation", tenantPlatformService.readInvitation(token));
    }

    @PostMapping("/invitations/accept")
    public Map<String, Object> acceptInvitation(@Valid @RequestBody TenantInvitationAcceptRequest request) {
        return Map.of("tenant", tenantPlatformService.acceptInvitation(request));
    }
}
