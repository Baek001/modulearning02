package kr.or.ddit.mybatis.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import kr.or.ddit.vo.UsersVO;

@Mapper
public interface UsersMapper {

    int insertUser(UsersVO user);

    List<UsersVO> selectUserList();

    List<UsersVO> selectUserListByTenant(String tenantId);

    UsersVO selectUser(String userId);

    UsersVO selectUserByTenant(@Param("tenantId") String tenantId, @Param("userId") String userId);

    int updateUser(UsersVO user);

    int updateUserByTenant(UsersVO user);

    int retireUser(String userId);

    int retireUserByTenant(@Param("tenantId") String tenantId, @Param("userId") String userId);

    List<UsersVO> selectUsersByTerm(String term);

    List<UsersVO> selectUsersByTermInTenant(@Param("tenantId") String tenantId, @Param("term") String term);

    List<UsersVO> selectResignedUserList();

    List<UsersVO> selectResignedUserListByTenant(String tenantId);

    UsersVO selectWorkStts(String userId);

    int updateWorkStts(@Param("userId") String userId, @Param("workSttsCd") String workSttsCd);

    UsersVO selectUserById(String userId);

    List<UsersVO> selectUsersByDept(String deptId);

    Map<String, Object> selectUserForCertificate(@Param("userId") String userId);
}
