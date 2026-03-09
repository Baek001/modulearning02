package kr.or.ddit.tenant.vo;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class TenantInvitationVO {
    private String invitationId;
    private String tenantId;
    private String tenantNm;
    private String tenantSlug;
    private String inviteEmail;
    private String inviteName;
    private String tenantRoleCd;
    private String statusCd;
    private String invitationToken;
    private String createdByUserId;
    private String acceptedUserId;
    private LocalDateTime expiresAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime crtDt;
    private LocalDateTime lastChgDt;
}
