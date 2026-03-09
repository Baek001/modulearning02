package kr.or.ddit.department.service;

import java.util.List;

import kr.or.ddit.vo.DepartmentVO;

public interface DepartmentService {

    boolean createDepartment(DepartmentVO dept);

    List<DepartmentVO> readDepartmentList();

    List<DepartmentVO> readDepartmentListByTenant(String tenantId);

    DepartmentVO readDepartment(String deptId);

    DepartmentVO readDepartmentByTenant(String tenantId, String deptId);

    boolean removeDepartment(String deptId);

    boolean removeDepartmentByTenant(String tenantId, String deptId);

    boolean modifyDepartment(DepartmentVO dept);

    boolean modifyDepartmentByTenant(DepartmentVO dept);
}
