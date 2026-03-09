package kr.or.ddit.department.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import kr.or.ddit.comm.exception.EntityNotFoundException;
import kr.or.ddit.department.service.DepartmentService;
import kr.or.ddit.messenger.community.service.CommunityService;
import kr.or.ddit.mybatis.mapper.DepartmentMapper;
import kr.or.ddit.vo.DepartmentVO;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper mapper;
    private final CommunityService communityService;

    @Override
    public boolean createDepartment(DepartmentVO dept) {
        if (dept.getUpDeptId() != null && !dept.getUpDeptId().isEmpty()) {
            DepartmentVO upperDept = mapper.selectDepartmentByTenant(dept.getTenantId(), dept.getUpDeptId());
            if (upperDept == null) {
                throw new EntityNotFoundException(dept.getUpDeptId());
            }
            if (!upperDept.getDeptId().endsWith("000")) {
                throw new IllegalArgumentException("Child departments can only be created below a top-level department.");
            }
        }

        String newDeptId = (dept.getUpDeptId() == null || dept.getUpDeptId().isEmpty())
            ? mapper.getNextTopDeptId()
            : mapper.getNextChildDeptId(dept.getUpDeptId());
        dept.setDeptId(newDeptId);

        boolean created = mapper.insertDepartment(dept) > 0;
        if (created) {
            communityService.syncOrgCommunities(dept.getTenantId(), "system", true);
        }
        return created;
    }

    @Override
    public List<DepartmentVO> readDepartmentList() {
        return mapper.selectDepartmentList();
    }

    @Override
    public List<DepartmentVO> readDepartmentListByTenant(String tenantId) {
        return mapper.selectDepartmentListByTenant(tenantId);
    }

    @Override
    public DepartmentVO readDepartment(String deptId) {
        DepartmentVO dept = mapper.selectDepartment(deptId);
        if (dept == null) {
            throw new EntityNotFoundException(deptId);
        }
        return dept;
    }

    @Override
    public DepartmentVO readDepartmentByTenant(String tenantId, String deptId) {
        DepartmentVO dept = mapper.selectDepartmentByTenant(tenantId, deptId);
        if (dept == null) {
            throw new EntityNotFoundException(deptId);
        }
        return dept;
    }

    @Override
    public boolean removeDepartment(String deptId) {
        int userCount = mapper.countUsersInDepartment(deptId);
        if (userCount > 0) {
            return false;
        }
        return mapper.deleteDepartment(deptId) > 0;
    }

    @Override
    public boolean removeDepartmentByTenant(String tenantId, String deptId) {
        int userCount = mapper.countUsersInDepartmentByTenant(tenantId, deptId);
        if (userCount > 0) {
            return false;
        }
        boolean removed = mapper.deleteDepartmentByTenant(tenantId, deptId) > 0;
        if (removed) {
            communityService.syncOrgCommunities(tenantId, "system", true);
        }
        return removed;
    }

    @Override
    public boolean modifyDepartment(DepartmentVO dept) {
        return mapper.updateDepartment(dept) > 0;
    }

    @Override
    public boolean modifyDepartmentByTenant(DepartmentVO dept) {
        boolean modified = mapper.updateDepartmentByTenant(dept) > 0;
        if (modified) {
            communityService.syncOrgCommunities(dept.getTenantId(), "system", true);
        }
        return modified;
    }
}
