package kr.or.ddit.users.service.impl;

import java.util.List;
import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import kr.or.ddit.comm.exception.EntityNotFoundException;
import kr.or.ddit.messenger.community.service.CommunityService;
import kr.or.ddit.mybatis.mapper.TenantPlatformMapper;
import kr.or.ddit.mybatis.mapper.UserHistoryMapper;
import kr.or.ddit.mybatis.mapper.UsersMapper;
import kr.or.ddit.tenant.vo.TenantMembershipVO;
import kr.or.ddit.users.service.UsersService;
import kr.or.ddit.vo.UserHistoryVO;
import kr.or.ddit.vo.UsersVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsersServiceImpl implements UsersService {

    private final UsersMapper mapper;
    private final UserHistoryMapper historyMapper;
    private final TenantPlatformMapper tenantPlatformMapper;
    private final PasswordEncoder passwordEncoder;
    private final CommunityService communityService;

    @Override
    public boolean createUser(UsersVO user) {
        encodePassword(user);
        ensureLoginId(user);
        boolean created = mapper.insertUser(user) > 0;
        if (created) {
            upsertTenantMembership(user);
            syncTenantCommunities(user.getTenantId());
        }
        return created;
    }

    @Override
    public List<UsersVO> readUserList() {
        return mapper.selectUserList();
    }

    @Override
    public List<UsersVO> readUserListByTenant(String tenantId) {
        return mapper.selectUserListByTenant(tenantId);
    }

    @Override
    public UsersVO readUser(String userId) {
        UsersVO user = mapper.selectUser(userId);
        if (user == null) {
            throw new EntityNotFoundException(user);
        }
        return user;
    }

    @Override
    public UsersVO readUserByTenant(String tenantId, String userId) {
        UsersVO user = mapper.selectUserByTenant(tenantId, userId);
        if (user == null) {
            throw new EntityNotFoundException(userId);
        }
        return user;
    }

    @Override
    public boolean modifyUser(UsersVO user) {
        UsersVO before = mapper.selectUserById(user.getUserId());
        if (before == null) {
            throw new EntityNotFoundException(user);
        }
        if (!StringUtils.hasText(user.getUserPswd())) {
            throw new IllegalArgumentException("Password is required.");
        }

        user.setUserPswd(passwordEncoder.encode(user.getUserPswd()));
        ensureLoginId(user);
        boolean result = mapper.updateUser(user) > 0;
        writeUserHistory(before, user, result);
        if (result) {
            syncTenantCommunities(user.getTenantId());
        }
        return result;
    }

    @Override
    public boolean modifyUserByTenant(UsersVO user) {
        UsersVO before = mapper.selectUserByTenant(user.getTenantId(), user.getUserId());
        if (before == null) {
            throw new EntityNotFoundException(user);
        }
        if (!StringUtils.hasText(user.getUserPswd())) {
            throw new IllegalArgumentException("Password is required.");
        }

        user.setUserPswd(passwordEncoder.encode(user.getUserPswd()));
        ensureLoginId(user);
        boolean result = mapper.updateUserByTenant(user) > 0;
        writeUserHistory(before, user, result);
        if (result) {
            syncTenantCommunities(user.getTenantId());
        }
        return result;
    }

    @Override
    public boolean retireUser(String userId) {
        UsersVO current = readUser(userId);
        boolean retired = mapper.retireUser(userId) > 0;
        if (retired && StringUtils.hasText(current.getTenantId())) {
            tenantPlatformMapper.updateTenantMemberStatus(current.getTenantId(), userId, "RETIRED");
            syncTenantCommunities(current.getTenantId());
        }
        return retired;
    }

    @Override
    public boolean retireUserByTenant(String tenantId, String userId) {
        boolean retired = mapper.retireUserByTenant(tenantId, userId) > 0;
        if (retired) {
            tenantPlatformMapper.updateTenantMemberStatus(tenantId, userId, "RETIRED");
            syncTenantCommunities(tenantId);
        }
        return retired;
    }

    @Override
    public List<UsersVO> searchUsers(String term) {
        return mapper.selectUsersByTerm(term);
    }

    @Override
    public List<UsersVO> searchUsersInTenant(String tenantId, String term) {
        return mapper.selectUsersByTermInTenant(tenantId, term);
    }

    @Override
    public List<UsersVO> readResignedUserList() {
        return mapper.selectResignedUserList();
    }

    @Override
    public List<UsersVO> readResignedUserListByTenant(String tenantId) {
        return mapper.selectResignedUserListByTenant(tenantId);
    }

    @Override
    public UsersVO readWorkStts(String userId) {
        return mapper.selectWorkStts(userId);
    }

    @Override
    public boolean modifyWorkStts(String userId, String workSttsCd) {
        return mapper.updateWorkStts(userId, workSttsCd) > 0;
    }

    @Override
    public boolean createUserList(List<UsersVO> userList) {
        boolean success = true;
        for (UsersVO user : userList) {
            encodePassword(user);
            ensureLoginId(user);
            int result = mapper.insertUser(user);
            if (result <= 0) {
                success = false;
                continue;
            }
            upsertTenantMembership(user);
        }
        if (success && !userList.isEmpty()) {
            syncTenantCommunities(userList.get(0).getTenantId());
        }
        return success;
    }

    private void writeUserHistory(UsersVO before, UsersVO user, boolean result) {
        boolean deptChanged = !Objects.equals(before.getDeptId(), user.getDeptId());
        boolean jbgdChanged = !Objects.equals(before.getJbgdCd(), user.getJbgdCd());
        if (!result || (!deptChanged && !jbgdChanged)) {
            return;
        }

        UserHistoryVO hist = new UserHistoryVO();
        hist.setUserId(user.getUserId());
        hist.setBeforeDeptId(before.getDeptId());
        hist.setAfterDeptId(user.getDeptId());
        hist.setBeforeJbgdCd(before.getJbgdCd());
        hist.setAfterJbgdCd(user.getJbgdCd());
        hist.setReason("Administrator updated profile.");

        if (deptChanged && jbgdChanged) {
            hist.setChangeType("03");
        } else if (deptChanged) {
            hist.setChangeType("01");
        } else if (jbgdChanged) {
            hist.setChangeType("02");
        }

        historyMapper.insertUserHistory(hist);
    }

    private void encodePassword(UsersVO user) {
        if (user != null && StringUtils.hasText(user.getUserPswd())) {
            user.setUserPswd(passwordEncoder.encode(user.getUserPswd()));
        }
    }

    private void ensureLoginId(UsersVO user) {
        if (user == null) {
            return;
        }
        if (!StringUtils.hasText(user.getLoginId())) {
            if (StringUtils.hasText(user.getUserEmail())) {
                user.setLoginId(user.getUserEmail().trim().toLowerCase());
            } else {
                user.setLoginId(user.getUserId());
            }
        }
    }

    private void upsertTenantMembership(UsersVO user) {
        if (user == null || !StringUtils.hasText(user.getTenantId())) {
            return;
        }
        TenantMembershipVO membership = new TenantMembershipVO();
        membership.setTenantId(user.getTenantId());
        membership.setUserId(user.getUserId());
        membership.setTenantRoleCd(StringUtils.hasText(user.getTenantRoleCd()) ? user.getTenantRoleCd() : "MEMBER");
        membership.setMembershipStatusCd("ACTIVE");
        tenantPlatformMapper.insertTenantMember(membership);
    }

    private void syncTenantCommunities(String tenantId) {
        if (StringUtils.hasText(tenantId)) {
            communityService.syncOrgCommunities(tenantId, "system", true);
        }
    }
}
