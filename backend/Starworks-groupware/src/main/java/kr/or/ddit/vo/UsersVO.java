package kr.or.ddit.vo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.NotBlank;
import kr.or.ddit.comm.file.FileAttachable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.Include;
import lombok.ToString;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UsersVO implements FileAttachable {

    @Include
    @NotBlank
    private String userId;
    private String tenantId;
    private String loginId;
    private String tenantRoleCd;
    private String membershipStatusCd;
    private String tenantNm;
    private String tenantSlug;

    @NotBlank
    private String userPswd;

    @NotBlank
    private String userNm;

    @NotBlank
    private String userEmail;

    private String userTelno;
    private String extTel;
    private String rsgntnYn;
    private LocalDate rsgntnYmd;

    @NotBlank
    private String deptId;

    @NotBlank
    private String jbgdCd;

    private String userImgFileId;
    private String userRole;
    private LocalDate hireYmd;

    private String deptNm;
    private String jbgdNm;

    private String workSttsCd;
    private String codeNm;

    private String upDeptId;
    private String filePath;
    private String userOtpSecret;

    @JsonIgnore
    @ToString.Exclude
    private List<MultipartFile> fileList;

    @Override
    public List<MultipartFile> getFileList() {
        return this.fileList;
    }

    @Override
    public void setFileId(String fileId) {
        this.userImgFileId = fileId;
    }
}
