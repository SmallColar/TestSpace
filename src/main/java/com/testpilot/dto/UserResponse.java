package com.testpilot.dto;

import com.testpilot.domain.User;
import com.testpilot.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息响应（不含密码）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private UserRole role;

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(),
                user.getNickname(), user.getAvatar(), user.getRole());
    }
}
