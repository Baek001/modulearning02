package kr.or.ddit.messenger.community.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.or.ddit.messenger.community.service.CommunityService;
import kr.or.ddit.security.CustomUserDetails;
import kr.or.ddit.tenant.service.TenantSecurityService;
import kr.or.ddit.vo.CommunityMemberVO;
import kr.or.ddit.vo.CommunityVO;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/rest/communities")
@RequiredArgsConstructor
public class CommunityRestController {

    private final CommunityService communityService;
    private final TenantSecurityService tenantSecurityService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public List<CommunityVO> list(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Authentication authentication,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String view,
        @RequestParam(required = false, defaultValue = "false") boolean manageable
    ) {
        return communityService.getCommunities(userDetails.getTenantId(), userDetails.getUsername(), q, view, manageable, tenantSecurityService.isTenantAdmin(authentication));
    }

    @GetMapping("/search")
    public List<CommunityVO> search(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Authentication authentication,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String view,
        @RequestParam(required = false, defaultValue = "false") boolean manageable
    ) {
        return communityService.getCommunities(userDetails.getTenantId(), userDetails.getUsername(), q, view, manageable, tenantSecurityService.isTenantAdmin(authentication));
    }

    @GetMapping("/{communityId}")
    public CommunityVO detail(
        @PathVariable Long communityId,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Authentication authentication
    ) {
        return communityService.getCommunity(userDetails.getTenantId(), communityId, userDetails.getUsername(), tenantSecurityService.isTenantAdmin(authentication));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public CommunityVO createJson(
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Authentication authentication
    ) {
        CommunityVO community = toCommunity(body);
        return communityService.createCommunity(
            userDetails.getTenantId(),
            community,
            userDetails.getUsername(),
            asStringList(body.get("memberUserIds")),
            asStringList(body.get("operatorUserIds")),
            null,
            null,
            tenantSecurityService.isTenantAdmin(authentication)
        );
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommunityVO createMultipart(
        @RequestPart("payload") String payload,
        @RequestPart(name = "iconFile", required = false) MultipartFile iconFile,
        @RequestPart(name = "coverFile", required = false) MultipartFile coverFile,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Authentication authentication
    ) throws IOException {
        Map<String, Object> body = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
        CommunityVO community = toCommunity(body);
        return communityService.createCommunity(
            userDetails.getTenantId(),
            community,
            userDetails.getUsername(),
            asStringList(body.get("memberUserIds")),
            asStringList(body.get("operatorUserIds")),
            iconFile,
            coverFile,
            tenantSecurityService.isTenantAdmin(authentication)
        );
    }

    @PatchMapping(value = "/{communityId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CommunityVO updateJson(
        @PathVariable Long communityId,
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Authentication authentication
    ) {
        CommunityVO community = toCommunity(body);
        return communityService.updateCommunity(
            userDetails.getTenantId(),
            communityId,
            community,
            userDetails.getUsername(),
            asStringList(body.get("operatorUserIds")),
            null,
            null,
            tenantSecurityService.isTenantAdmin(authentication)
        );
    }

    @PatchMapping(value = "/{communityId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommunityVO updateMultipart(
        @PathVariable Long communityId,
        @RequestPart("payload") String payload,
        @RequestPart(name = "iconFile", required = false) MultipartFile iconFile,
        @RequestPart(name = "coverFile", required = false) MultipartFile coverFile,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Authentication authentication
    ) throws IOException {
        Map<String, Object> body = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
        CommunityVO community = toCommunity(body);
        return communityService.updateCommunity(
            userDetails.getTenantId(),
            communityId,
            community,
            userDetails.getUsername(),
            asStringList(body.get("operatorUserIds")),
            iconFile,
            coverFile,
            tenantSecurityService.isTenantAdmin(authentication)
        );
    }

    @DeleteMapping("/{communityId}")
    public Map<String, Object> delete(
        @PathVariable Long communityId,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Authentication authentication
    ) {
        communityService.closeCommunity(userDetails.getTenantId(), communityId, userDetails.getUsername(), tenantSecurityService.isTenantAdmin(authentication));
        return Map.of("success", true);
    }

    @PostMapping("/{communityId}/close")
    public Map<String, Object> close(
        @PathVariable Long communityId,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Authentication authentication
    ) {
        communityService.closeCommunity(userDetails.getTenantId(), communityId, userDetails.getUsername(), tenantSecurityService.isTenantAdmin(authentication));
        return Map.of("success", true);
    }

    @PostMapping("/{communityId}/join")
    public CommunityVO join(
        @PathVariable Long communityId,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Authentication authentication
    ) {
        return communityService.joinCommunity(userDetails.getTenantId(), communityId, userDetails.getUsername(), tenantSecurityService.isTenantAdmin(authentication));
    }

    @PostMapping("/{communityId}/leave")
    public Map<String, Object> leave(
        @PathVariable Long communityId,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Authentication authentication
    ) {
        communityService.leaveCommunity(userDetails.getTenantId(), communityId, userDetails.getUsername(), tenantSecurityService.isTenantAdmin(authentication));
        return Map.of("success", true);
    }

    @GetMapping("/{communityId}/members")
    public List<CommunityMemberVO> members(
        @PathVariable Long communityId,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Authentication authentication,
        @RequestParam(required = false) String status
    ) {
        return communityService.getMembers(userDetails.getTenantId(), communityId, userDetails.getUsername(), tenantSecurityService.isTenantAdmin(authentication), status);
    }

    @GetMapping("/{communityId}/requests")
    public List<CommunityMemberVO> requests(
        @PathVariable Long communityId,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Authentication authentication
    ) {
        return communityService.getPendingMembers(userDetails.getTenantId(), communityId, userDetails.getUsername(), tenantSecurityService.isTenantAdmin(authentication));
    }

    @PostMapping("/{communityId}/members")
    public Map<String, Object> addMembers(
        @PathVariable Long communityId,
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Authentication authentication
    ) {
        communityService.addMembers(userDetails.getTenantId(), communityId, asStringList(body.get("userIds")), userDetails.getUsername(), tenantSecurityService.isTenantAdmin(authentication));
        return Map.of("success", true);
    }

    @DeleteMapping("/{communityId}/members/{userId}")
    public Map<String, Object> removeMember(
        @PathVariable Long communityId,
        @PathVariable String userId,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Authentication authentication
    ) {
        communityService.removeMember(userDetails.getTenantId(), communityId, userId, userDetails.getUsername(), tenantSecurityService.isTenantAdmin(authentication));
        return Map.of("success", true);
    }

    @PatchMapping("/{communityId}/members/{userId}/role")
    public Map<String, Object> updateMemberRole(
        @PathVariable Long communityId,
        @PathVariable String userId,
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Authentication authentication
    ) {
        communityService.updateMemberRole(userDetails.getTenantId(), communityId, userId, body.get("roleCd") == null ? null : String.valueOf(body.get("roleCd")), userDetails.getUsername(), tenantSecurityService.isTenantAdmin(authentication));
        return Map.of("success", true);
    }

    @PostMapping("/{communityId}/requests/{userId}/approve")
    public Map<String, Object> approveRequest(
        @PathVariable Long communityId,
        @PathVariable String userId,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Authentication authentication
    ) {
        communityService.approveMember(userDetails.getTenantId(), communityId, userId, userDetails.getUsername(), tenantSecurityService.isTenantAdmin(authentication));
        return Map.of("success", true);
    }

    @PostMapping("/{communityId}/requests/{userId}/reject")
    public Map<String, Object> rejectRequest(
        @PathVariable Long communityId,
        @PathVariable String userId,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Authentication authentication
    ) {
        communityService.rejectMember(userDetails.getTenantId(), communityId, userId, userDetails.getUsername(), tenantSecurityService.isTenantAdmin(authentication));
        return Map.of("success", true);
    }

    @PutMapping("/{communityId}/favorite")
    public Map<String, Object> favorite(
        @PathVariable Long communityId,
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        boolean favorite = body.get("favoriteYn") == null || "Y".equalsIgnoreCase(String.valueOf(body.get("favoriteYn"))) || Boolean.TRUE.equals(body.get("favoriteYn"));
        communityService.toggleFavorite(userDetails.getTenantId(), communityId, userDetails.getUsername(), favorite);
        return Map.of("success", true);
    }

    @PutMapping("/order")
    public Map<String, Object> saveOrder(
        @RequestBody Map<String, Object> body,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        communityService.saveOrder(userDetails.getTenantId(), userDetails.getUsername(), asLongList(body.get("communityIds")));
        return Map.of("success", true);
    }

    @PostMapping("/sync-org")
    public Map<String, Object> syncOrg(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        Authentication authentication
    ) {
        return communityService.syncOrgCommunities(userDetails.getTenantId(), userDetails.getUsername(), tenantSecurityService.isTenantAdmin(authentication));
    }

    private CommunityVO toCommunity(Map<String, Object> body) {
        CommunityVO community = new CommunityVO();
        community.setCommunityNm((String) body.get("communityNm"));
        community.setCommunityDesc((String) body.get("communityDesc"));
        community.setCommunityTypeCd(asString(body.get("communityTypeCd")));
        community.setVisibilityCd(asString(body.get("visibilityCd")));
        community.setJoinPolicyCd(asString(body.get("joinPolicyCd")));
        community.setIntroText((String) body.get("introText"));
        community.setPostTemplateHtml((String) body.get("postTemplateHtml"));
        return community;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        return items.stream().filter(item -> item != null).map(String::valueOf).toList();
    }

    private List<Long> asLongList(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        return items.stream()
            .filter(item -> item != null)
            .map(String::valueOf)
            .map(Long::valueOf)
            .toList();
    }
}
