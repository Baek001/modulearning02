package kr.or.ddit.tenant.service;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import kr.or.ddit.tenant.dto.PublicSignupConfigResponse;
import kr.or.ddit.tenant.dto.TenantSignupRequest;
import lombok.extern.slf4j.Slf4j;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

@Service
@Slf4j
public class PublicSignupGuardService {

    private static final String TURNSTILE_VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final RestClient restClient = RestClient.create();
    private final Map<String, FixedWindowCounter> ipCounters = new ConcurrentHashMap<>();
    private final Map<String, FixedWindowCounter> emailCounters = new ConcurrentHashMap<>();
    private final Map<String, FixedWindowCounter> slugCounters = new ConcurrentHashMap<>();

    @Value("${app.signup.turnstile.enabled:false}")
    private boolean turnstileEnabled;

    @Value("${app.signup.turnstile.site-key:}")
    private String turnstileSiteKey;

    @Value("${app.signup.turnstile.secret-key:}")
    private String turnstileSecretKey;

    @Value("${app.signup.rate-limit.ip.max-attempts:10}")
    private int ipMaxAttempts;

    @Value("${app.signup.rate-limit.ip.window-seconds:600}")
    private long ipWindowSeconds;

    @Value("${app.signup.rate-limit.email.max-attempts:5}")
    private int emailMaxAttempts;

    @Value("${app.signup.rate-limit.email.window-seconds:3600}")
    private long emailWindowSeconds;

    @Value("${app.signup.rate-limit.slug.max-attempts:5}")
    private int slugMaxAttempts;

    @Value("${app.signup.rate-limit.slug.window-seconds:3600}")
    private long slugWindowSeconds;

    public PublicSignupConfigResponse readSignupConfig() {
        boolean configured = isTurnstileConfigured();
        if (turnstileEnabled && !configured) {
            log.error("Public signup Turnstile is enabled but not fully configured.");
        }
        return new PublicSignupConfigResponse(
            !turnstileEnabled || configured,
            turnstileEnabled,
            configured ? turnstileSiteKey.trim() : ""
        );
    }

    public void validateOwnerSignup(TenantSignupRequest request, HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        String emailKey = normalizeEmail(request == null ? null : request.getOwnerEmail());
        String slugKey = resolveRateLimitSlugKey(request);

        enforceWindow(ipCounters, "ip:" + clientIp, ipMaxAttempts, ipWindowSeconds, "가입 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        if (StringUtils.hasText(emailKey)) {
            enforceWindow(emailCounters, "email:" + emailKey, emailMaxAttempts, emailWindowSeconds, "이 이메일로 가입 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        }
        if (StringUtils.hasText(slugKey)) {
            enforceWindow(slugCounters, "slug:" + slugKey, slugMaxAttempts, slugWindowSeconds, "이 워크스페이스 이름으로 가입 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        }

        if (!turnstileEnabled) {
            return;
        }

        if (!isTurnstileConfigured()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "가입 보안 설정이 준비되지 않았습니다. 관리자에게 문의해 주세요.");
        }
        if (request == null || !StringUtils.hasText(request.getTurnstileToken())) {
            throw new ResponseStatusException(BAD_REQUEST, "보안 확인을 완료해 주세요.");
        }
        if (!verifyTurnstile(request.getTurnstileToken().trim(), clientIp)) {
            throw new ResponseStatusException(BAD_REQUEST, "보안 확인에 실패했습니다. 다시 시도해 주세요.");
        }
    }

    private boolean verifyTurnstile(String token, String clientIp) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("secret", turnstileSecretKey.trim());
        body.add("response", token);
        if (StringUtils.hasText(clientIp)) {
            body.add("remoteip", clientIp);
        }

        try {
            Map<?, ?> response = restClient.post()
                .uri(TURNSTILE_VERIFY_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(Map.class);
            boolean success = response != null && Boolean.TRUE.equals(response.get("success"));
            if (!success) {
                log.warn("Turnstile verification failed for ip={} errors={}", clientIp, response == null ? null : response.get("error-codes"));
            }
            return success;
        } catch (RuntimeException exception) {
            log.error("Turnstile verification request failed for ip={}", clientIp, exception);
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "가입 보안 확인 서버와 통신하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private void enforceWindow(
        Map<String, FixedWindowCounter> counters,
        String key,
        int maxAttempts,
        long windowSeconds,
        String message
    ) {
        if (maxAttempts <= 0 || windowSeconds <= 0 || !StringUtils.hasText(key)) {
            return;
        }

        long windowMillis = windowSeconds * 1000L;
        long now = System.currentTimeMillis();
        FixedWindowCounter counter = counters.computeIfAbsent(key, ignored -> new FixedWindowCounter());
        int count = counter.incrementAndGet(now, windowMillis);
        if (count > maxAttempts) {
            log.warn("Public owner signup rate limited for key={} count={} maxAttempts={}", key, count, maxAttempts);
            throw new ResponseStatusException(TOO_MANY_REQUESTS, message);
        }
    }

    private String extractClientIp(HttpServletRequest httpRequest) {
        if (httpRequest == null) {
            return "unknown";
        }

        String[] headerNames = { "CF-Connecting-IP", "X-Forwarded-For", "X-Real-IP" };
        for (String headerName : headerNames) {
            String headerValue = httpRequest.getHeader(headerName);
            if (StringUtils.hasText(headerValue)) {
                return headerValue.split(",")[0].trim();
            }
        }
        return StringUtils.hasText(httpRequest.getRemoteAddr()) ? httpRequest.getRemoteAddr().trim() : "unknown";
    }

    private String normalizeEmail(String ownerEmail) {
        return StringUtils.hasText(ownerEmail) ? ownerEmail.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String resolveRateLimitSlugKey(TenantSignupRequest request) {
        if (request == null) {
            return "";
        }
        String candidate = StringUtils.hasText(request.getWorkspaceSlug())
            ? request.getWorkspaceSlug()
            : request.getCompanyName();
        if (!StringUtils.hasText(candidate)) {
            return "";
        }
        String normalized = candidate.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        return candidate.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isTurnstileConfigured() {
        return turnstileEnabled
            && StringUtils.hasText(turnstileSiteKey)
            && StringUtils.hasText(turnstileSecretKey);
    }

    private static final class FixedWindowCounter {
        private long windowStart;
        private int count;

        private synchronized int incrementAndGet(long now, long windowMillis) {
            if (windowStart == 0L || now - windowStart >= windowMillis) {
                windowStart = now;
                count = 0;
            }
            count += 1;
            return count;
        }
    }
}
