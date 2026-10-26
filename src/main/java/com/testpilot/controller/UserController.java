package com.testpilot.controller;

import com.testpilot.common.R;
import com.testpilot.dto.ChangePasswordRequest;
import com.testpilot.dto.UpdateProfileRequest;
import com.testpilot.dto.UserResponse;
import com.testpilot.security.UserContext;
import com.testpilot.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人中心：查看/修改个人信息、修改密码（spec/SPEC-2026-10-15.md §7）。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public R<UserResponse> profile() {
        return R.ok(userService.profile(UserContext.userId()));
    }

    @PutMapping("/profile")
    public R<UserResponse> updateProfile(@RequestBody UpdateProfileRequest req) {
        return R.ok(userService.updateProfile(UserContext.userId(), req));
    }

    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(UserContext.userId(), req);
        return R.ok(null);
    }
}
