package kr.or.ddit.mybatis.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import kr.or.ddit.tenant.vo.TenantInvitationVO;
import kr.or.ddit.tenant.vo.TenantMembershipVO;
import kr.or.ddit.tenant.vo.TenantVO;

@Mapper
public interface TenantPlatformMapper {

    int insertTenant(TenantVO tenant);

    TenantVO selectTenantById(String tenantId);

    TenantVO selectTenantBySlug(String tenantSlug);

    int updateTenantOwnerUserId(@Param("tenantId") String tenantId, @Param("ownerUserId") String ownerUserId);

    int insertTenantMember(TenantMembershipVO membership);

    int updateTenantMemberStatus(@Param("tenantId") String tenantId, @Param("userId") String userId, @Param("membershipStatusCd") String membershipStatusCd);

    List<TenantMembershipVO> selectLoginCandidates(@Param("identifier") String identifier, @Param("tenantId") String tenantId);

    List<TenantMembershipVO> selectMembershipsByUserId(String userId);

    List<TenantMembershipVO> selectMembershipsByUserEmail(String userEmail);

    TenantMembershipVO selectMembershipByUserEmailAndTenant(@Param("userEmail") String userEmail, @Param("tenantId") String tenantId);

    TenantMembershipVO selectMembershipByUserIdAndTenant(@Param("userId") String userId, @Param("tenantId") String tenantId);

    int insertInvitation(TenantInvitationVO invitation);

    TenantInvitationVO selectInvitationByToken(String invitationToken);

    int acceptInvitation(@Param("invitationId") String invitationId, @Param("acceptedUserId") String acceptedUserId);

    int insertTenantCompanySetting(@Param("tenantId") String tenantId, @Param("companyNm") String companyNm, @Param("userId") String userId, @Param("senderEmail") String senderEmail);
}
