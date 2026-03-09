package kr.or.ddit.mybatis.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import kr.or.ddit.vo.DepartmentVO;

@Mapper
public interface DepartmentMapper {

    int insertDepartment(DepartmentVO department);

    List<DepartmentVO> selectDepartmentList();

    List<DepartmentVO> selectDepartmentListByTenant(String tenantId);

    DepartmentVO selectDepartment(String deptId);

    DepartmentVO selectDepartmentByTenant(@Param("tenantId") String tenantId, @Param("deptId") String deptId);

    int countUsersInDepartment(String deptId);

    int countUsersInDepartmentByTenant(@Param("tenantId") String tenantId, @Param("deptId") String deptId);

    int deleteDepartment(String deptId);

    int deleteDepartmentByTenant(@Param("tenantId") String tenantId, @Param("deptId") String deptId);

    List<Map<String, Object>> selectDepartmentUserCounts();

    String getNextTopDeptId();

    String getNextChildDeptId(String upDeptId);

    int updateDepartment(DepartmentVO dept);

    int updateDepartmentByTenant(DepartmentVO dept);
}
