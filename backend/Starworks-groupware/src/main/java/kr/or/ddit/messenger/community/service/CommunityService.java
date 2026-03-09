package kr.or.ddit.messenger.community.service;

import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

import kr.or.ddit.vo.CommunityMemberVO;
import kr.or.ddit.vo.CommunityVO;

public interface CommunityService {

    List<CommunityVO> getCommunities(String tenantId, String currentUserId, String keyword, String view, boolean manageableOnly, boolean admin);

    CommunityVO getCommunity(String tenantId, Long communityId, String currentUserId, boolean admin);

    CommunityVO createCommunity(String tenantId, CommunityVO community, String currentUserId, List<String> memberUserIds, List<String> operatorUserIds, MultipartFile iconFile, MultipartFile coverFile, boolean admin);

    CommunityVO updateCommunity(String tenantId, Long communityId, CommunityVO community, String currentUserId, List<String> operatorUserIds, MultipartFile iconFile, MultipartFile coverFile, boolean admin);

    void closeCommunity(String tenantId, Long communityId, String currentUserId, boolean admin);

    List<CommunityMemberVO> getMembers(String tenantId, Long communityId, String currentUserId, boolean admin, String statusCd);

    void addMembers(String tenantId, Long communityId, List<String> userIds, String currentUserId, boolean admin);

    void removeMember(String tenantId, Long communityId, String userId, String currentUserId, boolean admin);

    CommunityVO joinCommunity(String tenantId, Long communityId, String currentUserId, boolean admin);

    void leaveCommunity(String tenantId, Long communityId, String currentUserId, boolean admin);

    void updateMemberRole(String tenantId, Long communityId, String targetUserId, String roleCd, String currentUserId, boolean admin);

    List<CommunityMemberVO> getPendingMembers(String tenantId, Long communityId, String currentUserId, boolean admin);

    void approveMember(String tenantId, Long communityId, String targetUserId, String currentUserId, boolean admin);

    void rejectMember(String tenantId, Long communityId, String targetUserId, String currentUserId, boolean admin);

    void toggleFavorite(String tenantId, Long communityId, String currentUserId, boolean favorite);

    void saveOrder(String tenantId, String currentUserId, List<Long> communityIds);

    Map<String, Object> syncOrgCommunities(String tenantId, String currentUserId, boolean admin);
}
