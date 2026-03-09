package kr.or.ddit.tenant.dto;

public record PublicSignupConfigResponse(
    boolean signupEnabled,
    boolean turnstileEnabled,
    String turnstileSiteKey
) {
}
