package kr.or.ddit.mypage.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.or.ddit.mypage.dto.MyOnboardingResponse;
import kr.or.ddit.mypage.dto.MyPasswordChangeRequest;
import kr.or.ddit.mypage.dto.MyProfileUpdateRequest;
import kr.or.ddit.mypage.service.MyPageService;
import kr.or.ddit.security.CustomUserDetails;
import kr.or.ddit.tenant.dto.AuthSessionResponse;
import kr.or.ddit.tenant.service.TenantPlatformService;
import kr.or.ddit.vo.UsersVO;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/rest/mypage")
@RequiredArgsConstructor
public class MyPageRestController {

    private final MyPageService service;
    private final TenantPlatformService tenantPlatformService;

    @GetMapping
    public AuthSessionResponse getMyInfo(@AuthenticationPrincipal CustomUserDetails user) {
        return tenantPlatformService.buildSession(user.getUsername());
    }

    @GetMapping("/onboarding")
    public MyOnboardingResponse getOnboardingInfo(@AuthenticationPrincipal CustomUserDetails user) {
        return service.readOnboarding(user.getUsername());
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateMyInfo(
        @AuthenticationPrincipal CustomUserDetails user,
        @ModelAttribute MyProfileUpdateRequest request
    ) {
        return ResponseEntity.ok(service.updateUserInfo(user.getUsername(), request));
    }

    @PutMapping("/onboarding")
    public AuthSessionResponse completeOnboarding(
        @AuthenticationPrincipal CustomUserDetails user,
        @ModelAttribute MyProfileUpdateRequest request
    ) {
        UsersVO updatedUser = service.completeOnboarding(user.getUsername(), request);
        return tenantPlatformService.buildSession(updatedUser);
    }

    @PutMapping("/password")
    public ResponseEntity<?> updatePassword(
        @AuthenticationPrincipal CustomUserDetails user,
        @RequestBody MyPasswordChangeRequest request
    ) {
        service.changePassword(user.getUsername(), request);
        return ResponseEntity.ok().build();
    }
}
