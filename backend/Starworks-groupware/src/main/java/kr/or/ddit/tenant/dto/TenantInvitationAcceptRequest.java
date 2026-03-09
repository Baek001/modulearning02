package kr.or.ddit.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TenantInvitationAcceptRequest {
    @NotBlank
    private String token;

    @NotBlank
    private String userNm;

    @Email
    @NotBlank
    private String userEmail;

    @NotBlank
    private String password;
}
