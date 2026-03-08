package kr.or.ddit.comm.controller;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.or.ddit.security.jwt.JwtProvider;
import kr.or.ddit.vo.RestLoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class RestLoginController {
    private final JwtProvider provider;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;

    @Value("${app.cookie.domain:}")
    private String cookieDomain;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site:Strict}")
    private String cookieSameSite;

    @Value("${app.cookie.max-age:86400}")
    private long cookieMaxAge;

    @PostMapping("/common/auth")
    public ResponseEntity<?> restLogin(
        @RequestBody RestLoginVO restLoginVO,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String username = sanitizeUsername(restLoginVO.getUsername());
        Authentication inputToken = UsernamePasswordAuthenticationToken
            .unauthenticated(restLoginVO.getUsername(), restLoginVO.getPassword());

        try {
            Authentication authentication = authenticationManager.authenticate(inputToken);

            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);
            contextRepository.saveContext(securityContext, request, response);

            String jwt = provider.generateJwt(authentication);
            ResponseCookie jwtCookie = buildAccessTokenCookie(jwt, cookieMaxAge);

            return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .body(authentication);
        } catch (BadCredentialsException e) {
            log.warn("Invalid login credentials for username={}", username);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody(
                "INVALID_CREDENTIALS",
                "아이디 또는 비밀번호가 올바르지 않습니다."
            ));
        } catch (AuthenticationException e) {
            if (isAuthenticationServiceFailure(e)) {
                log.error("Login service failure for username={}", username, e);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorBody(
                    "AUTH_SERVICE_UNAVAILABLE",
                    "로그인 서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."
                ));
            }

            log.warn("Login failed for username={} with type={}", username, e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody(
                "LOGIN_FAILED",
                "인증에 실패했습니다. 관리자에게 문의하세요."
            ));
        }
    }

    @RequestMapping("/common/auth/revoke")
    public ResponseEntity<?> revoke(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) {
        SecurityContextHolder.clearContext();
        contextRepository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);

        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, buildAccessTokenCookie("", 0).toString())
            .build();
    }

    private Map<String, String> errorBody(String errorCode, String message) {
        Map<String, String> errorBody = new HashMap<>();
        errorBody.put("errorCode", errorCode);
        errorBody.put("message", message);
        return errorBody;
    }

    private ResponseCookie buildAccessTokenCookie(String value, long maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from("access_token", value)
            .httpOnly(true)
            .path("/")
            .secure(cookieSecure)
            .maxAge(maxAge)
            .sameSite(cookieSameSite);

        if (StringUtils.hasText(cookieDomain)) {
            builder.domain(cookieDomain.trim());
        }

        return builder.build();
    }

    private boolean isAuthenticationServiceFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof DataAccessException || current instanceof SQLException) {
                return true;
            }
            if (current instanceof AuthenticationServiceException && !(current instanceof BadCredentialsException)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String sanitizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return "<blank>";
        }
        return username.trim();
    }
}
