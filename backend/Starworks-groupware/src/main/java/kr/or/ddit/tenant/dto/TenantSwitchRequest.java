package kr.or.ddit.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TenantSwitchRequest {
    @NotBlank
    private String tenantId;
}
