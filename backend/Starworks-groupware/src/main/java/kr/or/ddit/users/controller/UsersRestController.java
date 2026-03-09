package kr.or.ddit.users.controller;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import kr.or.ddit.security.CustomUserDetails;
import kr.or.ddit.tenant.service.TenantSecurityService;
import kr.or.ddit.users.service.UsersService;
import kr.or.ddit.vo.UsersVO;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/rest/comm-user")
@RequiredArgsConstructor
public class UsersRestController {

    private final UsersService service;
    private final TenantSecurityService tenantSecurityService;

    @GetMapping
    public List<UsersVO> readUserList(Authentication authentication) {
        CustomUserDetails userDetails = tenantSecurityService.requireTenantUser(authentication);
        return service.readUserListByTenant(userDetails.getTenantId());
    }

    @GetMapping("/{userId}")
    public UsersVO readUser(@PathVariable String userId, Authentication authentication) {
        CustomUserDetails userDetails = tenantSecurityService.requireTenantUser(authentication);
        return service.readUserByTenant(userDetails.getTenantId(), userId);
    }

    @GetMapping("/autocomplete")
    public List<UsersVO> autocompleteUsers(@RequestParam String term, Authentication authentication) {
        CustomUserDetails userDetails = tenantSecurityService.requireTenantUser(authentication);
        return service.searchUsersInTenant(userDetails.getTenantId(), term);
    }

    @PostMapping
    public Map<String, Object> createUser(@RequestBody UsersVO newUser, Authentication authentication) {
        CustomUserDetails adminUser = tenantSecurityService.requireTenantAdmin(authentication);
        newUser.setTenantId(adminUser.getTenantId());
        if (!org.springframework.util.StringUtils.hasText(newUser.getTenantRoleCd())) {
            newUser.setTenantRoleCd("MEMBER");
        }
        boolean success = service.createUser(newUser);
        return Map.of("success", success);
    }

    @PutMapping("/{userId}")
    public boolean modifyUser(@PathVariable String userId, @RequestBody UsersVO vo, Authentication authentication) {
        CustomUserDetails adminUser = tenantSecurityService.requireTenantAdmin(authentication);
        vo.setTenantId(adminUser.getTenantId());
        vo.setUserId(userId);
        return service.modifyUserByTenant(vo);
    }

    @PatchMapping("/{userId}/retire")
    public boolean retireUser(@PathVariable String userId, Authentication authentication) {
        CustomUserDetails adminUser = tenantSecurityService.requireTenantAdmin(authentication);
        return service.retireUserByTenant(adminUser.getTenantId(), userId);
    }

    @GetMapping("/resigned")
    public List<UsersVO> getResignedUsers(Authentication authentication) {
        CustomUserDetails adminUser = tenantSecurityService.requireTenantAdmin(authentication);
        return service.readResignedUserListByTenant(adminUser.getTenantId());
    }

    @GetMapping("/search")
    public List<UsersVO> searchUsers(@RequestParam String term, Authentication authentication) {
        CustomUserDetails userDetails = tenantSecurityService.requireTenantUser(authentication);
        return service.searchUsersInTenant(userDetails.getTenantId(), term);
    }

    @GetMapping("/work-status/{userId}")
    public Map<String, Object> getWorkStts(@PathVariable("userId") String userId) {
        UsersVO workStts = service.readWorkStts(userId);
        return Map.of("workStts", workStts);
    }

    @PutMapping("/work-status")
    public Map<String, Object> modifyWorkStts(@RequestBody UsersVO vo, Authentication authentication) {
        CustomUserDetails userDetails = tenantSecurityService.requireCurrentUser(authentication);
        boolean success = service.modifyWorkStts(userDetails.getRealUser().getUserId(), vo.getWorkSttsCd());
        return Map.of("success", success);
    }

    @GetMapping("/me")
    public Authentication getLoginUser(Authentication authentication) {
        return authentication;
    }

    @PostMapping("/uploadExcel")
    public ResponseEntity<?> uploadExcel(@RequestParam("file") MultipartFile file, Authentication authentication) {
        CustomUserDetails adminUser = tenantSecurityService.requireTenantAdmin(authentication);
        try (InputStream inputStream = file.getInputStream()) {
            Workbook workbook = WorkbookFactory.create(inputStream);
            Sheet sheet = workbook.getSheetAt(0);
            List<UsersVO> userList = new ArrayList<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                if (row.getCell(0) == null || getCellValue(row.getCell(0)).trim().isEmpty()) {
                    continue;
                }

                UsersVO user = new UsersVO();
                user.setUserId(getCellValue(row.getCell(0)));
                user.setUserPswd(getCellValue(row.getCell(1)));
                user.setUserNm(getCellValue(row.getCell(2)));
                user.setUserEmail(getCellValue(row.getCell(3)));
                user.setUserTelno(getCellValue(row.getCell(4)));
                user.setExtTel(getCellValue(row.getCell(5)));
                user.setDeptId(getCellValue(row.getCell(6)));
                user.setJbgdCd(getCellValue(row.getCell(7)));
                user.setHireYmd(getLocalDateValue(row.getCell(8)));
                user.setTenantId(adminUser.getTenantId());
                user.setTenantRoleCd("MEMBER");
                userList.add(user);
            }

            workbook.close();
            service.createUserList(userList);
            return ResponseEntity.ok(userList.size() + " users were imported.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Excel import failed: " + e.getMessage());
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

    private LocalDate getLocalDateValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        if (cell.getCellType() == CellType.STRING) {
            String value = cell.getStringCellValue().trim();
            if (value.isEmpty()) {
                return null;
            }
            try {
                return LocalDate.parse(value);
            } catch (Exception first) {
                try {
                    return LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
                } catch (Exception second) {
                    return null;
                }
            }
        }
        return null;
    }
}
