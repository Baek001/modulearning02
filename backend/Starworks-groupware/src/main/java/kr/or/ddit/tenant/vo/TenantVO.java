package kr.or.ddit.tenant.vo;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class TenantVO {
    private String tenantId;
    private String tenantNm;
    private String tenantSlug;
    private String statusCd;
    private String ownerUserId;
    private LocalDateTime crtDt;
    private LocalDateTime lastChgDt;
}
