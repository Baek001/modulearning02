package kr.or.ddit.security.jwt;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import jakarta.annotation.PostConstruct;
import kr.or.ddit.security.CustomUserDetails;

@Component
public class JwtProvider {
    @Value("${jwt.sign-key}")
    private String secretKey;
    private byte[] keyByte;
    private JWSSigner signer;

    @PostConstruct
    public void init() throws KeyLengthException {
        keyByte = secretKey.getBytes(StandardCharsets.UTF_8);
        signer = new MACSigner(keyByte);
    }

    public String generateJwt(Authentication authentication) {
        try {
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .subject(authentication.getName())
                .claim("scope", authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 86400000L));

            if (authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
                builder.claim("tenantId", userDetails.getTenantId());
                builder.claim("tenantRoleCd", userDetails.getTenantRoleCd());
                builder.claim("userEmail", userDetails.getRealUser().getUserEmail());
            }

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), builder.build());
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("JWT generation failed", e);
        }
    }

    public boolean verifyToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            return jwt.verify(new MACVerifier(keyByte));
        } catch (ParseException | JOSEException e) {
            return false;
        }
    }

    public Authentication parseJwt(String token) {
        Jwt jwt = Jwt.withTokenValue(token).build();
        return new JwtAuthenticationToken(
            jwt,
            Optional.ofNullable(jwt.getClaimAsStringList("scope"))
                .map(AuthorityUtils::createAuthorityList)
                .orElse(Collections.emptyList())
        );
    }
}
