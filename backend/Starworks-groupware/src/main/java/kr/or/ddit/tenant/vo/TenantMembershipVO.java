package kr.or.ddit.tenant.vo;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class TenantMembershipVO {
    private String tenantId;
    private String tenantNm;
    private String tenantSlug;
    private String userId;
    private String loginId;
    private String userNm;
    private String userEmail;
    private String deptId;
    private String deptNm;
    private String tenantRoleCd;
    private String membershipStatusCd;
    private LocalDateTime crtDt;
    private LocalDateTime lastChgDt;
}
