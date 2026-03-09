package kr.or.ddit.users.service;

import java.util.List;

import kr.or.ddit.vo.UsersVO;

public interface UsersService {

    boolean createUser(UsersVO user);

    List<UsersVO> readUserList();

    List<UsersVO> readUserListByTenant(String tenantId);

    UsersVO readUser(String userId);

    UsersVO readUserByTenant(String tenantId, String userId);

    boolean modifyUser(UsersVO user);

    boolean modifyUserByTenant(UsersVO user);

    boolean retireUser(String userId);

    boolean retireUserByTenant(String tenantId, String userId);

    List<UsersVO> searchUsers(String term);

    List<UsersVO> searchUsersInTenant(String tenantId, String term);

    List<UsersVO> readResignedUserList();

    List<UsersVO> readResignedUserListByTenant(String tenantId);

    UsersVO readWorkStts(String userId);

    boolean modifyWorkStts(String userId, String workSttsCd);

    boolean createUserList(List<UsersVO> userList);
}
