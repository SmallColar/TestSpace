package com.testpilot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录/注册响应：令牌 + 用户信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** JWT 令牌，前端存 localStorage，后续请求放 Authorization: Bearer <token> */
    private String token;

    private UserResponse user;
}
