package kr.or.ddit.tenant.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    @GetMapping({"/signup/config", "/signup/runtime-config"})
    public PublicSignupConfigResponse signupConfig() {
        return publicSignupGuardService.readSignupConfig();
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody TenantSignupRequest request, HttpServletRequest httpServletRequest) {
        try {
            publicSignupGuardService.validateOwnerSignup(request, httpServletRequest);
            TenantMembershipVO membership = tenantPlatformService.signUpOwner(request);
            log.info(
                "Public owner signup created tenantId={} ownerEmail={} clientIp={}",
                membership.getTenantId(),
                request.getOwnerEmail(),
                httpServletRequest == null ? "unknown" : httpServletRequest.getRemoteAddr()
            );
            return ResponseEntity.ok(Map.of("tenant", membership));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Public owner signup failed for ownerEmail={}", request.getOwnerEmail(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                Map.of(
                    "error", "SIGNUP_FAILED",
                    "message", "Signup failed. Please try again later."
                )
            );
        }
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
