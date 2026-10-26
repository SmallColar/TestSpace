package com.testpilot.service;

import com.testpilot.common.BusinessException;
import com.testpilot.domain.User;
import com.testpilot.dto.ChangePasswordRequest;
import com.testpilot.dto.LoginRequest;
import com.testpilot.dto.LoginResponse;
import com.testpilot.dto.RegisterRequest;
import com.testpilot.dto.UpdateProfileRequest;
import com.testpilot.dto.UserResponse;
import com.testpilot.enums.UserRole;
import com.testpilot.repository.UserRepository;
import com.testpilot.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户注册 / 登录 / 个人信息。
 * <p>
 * 开放注册，注册出来的都是 {@link UserRole#USER}；管理员由
 * {@link com.testpilot.config.DataInitializer} 硬编码种子（spec/SPEC-2026-10-15.md §2）。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    /** 开放注册 */
    @Transactional
    public LoginResponse register(RegisterRequest req) {
        String username = req.getUsername().trim();
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(40900, "用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(StringUtils.hasText(req.getNickname()) ? req.getNickname().trim() : username);
        user.setRole(UserRole.USER);
        user = userRepository.save(user);
        return new LoginResponse(jwtService.issue(user), UserResponse.from(user));
    }

    /** 登录，成功返回 token + 用户信息 */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.getUsername().trim())
                .orElseThrow(() -> new BusinessException(40101, "用户名或密码错误"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException(40101, "用户名或密码错误");
        }
        return new LoginResponse(jwtService.issue(user), UserResponse.from(user));
    }

    /** 按 ID 取用户，取不到抛 404 */
    @Transactional(readOnly = true)
    public User requireById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "用户不存在"));
    }

    @Transactional(readOnly = true)
    public UserResponse profile(Long userId) {
        return UserResponse.from(requireById(userId));
    }

    /** 更新昵称/头像，null 字段视为不改 */
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = requireById(userId);
        if (req.getNickname() != null) {
            user.setNickname(req.getNickname().trim());
        }
        if (req.getAvatar() != null) {
            user.setAvatar(req.getAvatar().trim());
        }
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = requireById(userId);
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BusinessException(40102, "原密码不正确");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
    }
}
