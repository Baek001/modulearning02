package kr.or.ddit.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TenantSignupRequest {
    @NotBlank
    private String companyName;

    private String workspaceSlug;

    @NotBlank
    private String ownerName;

    @Email
    @NotBlank
    private String ownerEmail;

    @NotBlank
    private String password;

    private String turnstileToken;
}
