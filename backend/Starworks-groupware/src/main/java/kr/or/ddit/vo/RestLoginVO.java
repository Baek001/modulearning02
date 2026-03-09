package kr.or.ddit.vo;

import lombok.Data;

@Data
public class RestLoginVO {
    private String identifier;
    private String username;
    private String email;
    private String password;
    private String tenantId;
}
