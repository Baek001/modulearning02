package kr.or.ddit.security;

import java.util.Locale;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.util.StringUtils;

import kr.or.ddit.vo.UsersVO;
import lombok.ToString;

@ToString
public class CustomUserDetails extends User implements RealUserWrapper {
    private final UsersVO realUser;

    public CustomUserDetails(UsersVO realUser) {
        super(
            realUser.getUserId(),
            realUser.getUserPswd(),
            !"Y".equalsIgnoreCase(java.util.Objects.toString(realUser.getRsgntnYn(), "N")),
            true,
            true,
            true,
            AuthorityUtils.createAuthorityList(java.util.Objects.toString(realUser.getUserRole(), "ROLE_USER"))
        );
        this.realUser = realUser;
    }

    @Override
    public UsersVO getRealUser() {
        return realUser;
    }

    public String getTenantId() {
        return realUser.getTenantId();
    }

    public String getTenantRoleCd() {
        return realUser.getTenantRoleCd();
    }

    public boolean isTenantAdmin() {
        String role = StringUtils.hasText(realUser.getTenantRoleCd())
            ? realUser.getTenantRoleCd().trim().toUpperCase(Locale.ROOT)
            : "";
        return "OWNER".equals(role)
            || "ADMIN".equals(role)
            || getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equalsIgnoreCase(authority.getAuthority()) || "ADMIN".equalsIgnoreCase(authority.getAuthority()));
    }
}
