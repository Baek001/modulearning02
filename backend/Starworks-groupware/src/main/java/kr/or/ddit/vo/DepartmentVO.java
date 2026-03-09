package kr.or.ddit.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Include;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DepartmentVO {

    private String tenantId;

    @Include
    @NotBlank
    private String deptId;
    private String upDeptId;

    @NotBlank
    private String deptNm;
    private String useYn;
}
