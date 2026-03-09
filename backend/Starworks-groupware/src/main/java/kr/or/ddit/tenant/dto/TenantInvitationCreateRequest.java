package kr.or.ddit.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TenantInvitationCreateRequest {
    @Email
    @NotBlank
    private String inviteEmail;

    @NotBlank
    private String inviteName;

    private String tenantRoleCd;
}
