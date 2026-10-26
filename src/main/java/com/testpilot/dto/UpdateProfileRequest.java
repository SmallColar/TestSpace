package com.testpilot.dto;

import lombok.Data;

/**
 * 更新个人信息请求（昵称 / 头像）。
 */
@Data
public class UpdateProfileRequest {

    private String nickname;

    private String avatar;
}
