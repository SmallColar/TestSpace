package com.testpilot.controller;

import com.testpilot.common.R;
import com.testpilot.dto.LoginRequest;
import com.testpilot.dto.LoginResponse;
import com.testpilot.dto.RegisterRequest;
import com.testpilot.dto.UserResponse;
import com.testpilot.security.UserContext;
import com.testpilot.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录 / 注册 / 当前用户。
 * <p>
 * {@code /api/auth/login} 与 {@code /api/auth/register} 在
 * {@link com.testpilot.config.WebConfig} 中已排除拦截；{@code /api/auth/me} 需要登录态。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /** 开放注册 */
    @PostMapping("/register")
    public R<LoginResponse> register(@Valid @RequestBody RegisterRequest req) {
        return R.ok(userService.register(req));
    }

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return R.ok(userService.login(req));
    }

    /** 当前登录用户（前端刷新页面时用它恢复登录态） */
    @GetMapping("/me")
    public R<UserResponse> me() {
        return R.ok(userService.profile(UserContext.userId()));
    }
}
