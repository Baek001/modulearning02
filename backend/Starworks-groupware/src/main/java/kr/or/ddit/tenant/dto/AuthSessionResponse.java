package kr.or.ddit.tenant.dto;

import java.util.List;

import kr.or.ddit.tenant.vo.TenantMembershipVO;
import kr.or.ddit.vo.UsersVO;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthSessionResponse {
    private UsersVO user;
    private TenantMembershipVO currentTenant;
    private List<TenantMembershipVO> memberships;
}
