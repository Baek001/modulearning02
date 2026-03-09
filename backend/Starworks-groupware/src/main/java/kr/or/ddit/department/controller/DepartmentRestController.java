package kr.or.ddit.department.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.or.ddit.security.CustomUserDetails;
import kr.or.ddit.department.service.DepartmentService;
import kr.or.ddit.tenant.service.TenantSecurityService;
import kr.or.ddit.vo.DepartmentVO;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/rest/comm-depart")
@RequiredArgsConstructor
public class DepartmentRestController {

    private final DepartmentService service;
    private final TenantSecurityService tenantSecurityService;

    @GetMapping
    public List<DepartmentVO> readDepartmentList(Authentication authentication) {
        CustomUserDetails userDetails = tenantSecurityService.requireTenantUser(authentication);
        return service.readDepartmentListByTenant(userDetails.getTenantId());
    }

    @GetMapping("/{deptId}")
    public DepartmentVO readDepartment(@PathVariable String deptId, Authentication authentication) {
        CustomUserDetails userDetails = tenantSecurityService.requireTenantUser(authentication);
        return service.readDepartmentByTenant(userDetails.getTenantId(), deptId);
    }

    @PostMapping
    public boolean createDepartment(@RequestBody DepartmentVO deptVo, Authentication authentication) {
        CustomUserDetails adminUser = tenantSecurityService.requireTenantAdmin(authentication);
        deptVo.setTenantId(adminUser.getTenantId());
        return service.createDepartment(deptVo);
    }

    @DeleteMapping("/{deptId}")
    public Map<String, Object> deleteDepartment(@PathVariable String deptId, Authentication authentication) {
        CustomUserDetails adminUser = tenantSecurityService.requireTenantAdmin(authentication);
        boolean success = service.removeDepartmentByTenant(adminUser.getTenantId(), deptId);
        return success
            ? Map.of("success", true, "message", "Department was archived.")
            : Map.of("success", false, "message", "Department still has active users.");
    }

    @PutMapping("/{deptId}")
    public boolean modifyDepartment(
        @PathVariable String deptId,
        @RequestBody DepartmentVO vo,
        Authentication authentication
    ) {
        CustomUserDetails adminUser = tenantSecurityService.requireTenantAdmin(authentication);
        vo.setTenantId(adminUser.getTenantId());
        vo.setDeptId(deptId);
        return service.modifyDepartmentByTenant(vo);
    }
}
