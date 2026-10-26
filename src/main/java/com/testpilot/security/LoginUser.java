package com.testpilot.security;

import com.testpilot.enums.UserRole;

/**
 * 当前登录用户的轻量视图，从 JWT 解析得到，存入 {@link UserContext}。
 *
 * @param id       用户 ID
 * @param username 用户名
 * @param role     角色
 */
public record LoginUser(Long id, String username, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
