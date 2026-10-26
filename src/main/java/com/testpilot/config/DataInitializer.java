package com.testpilot.config;

import com.testpilot.domain.User;
import com.testpilot.enums.UserRole;
import com.testpilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动时确保硬编码管理员存在（spec/SPEC-2026-10-15.md §2）。
 * <p>
 * 本期管理员账号密码写死在配置里（admin / 666666），后续再换成可运营的方式。
 * 已存在则不改密码，避免重启把管理员改过的密码覆盖回去。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${testpilot.admin.username}")
    private String adminUsername;

    @Value("${testpilot.admin.password}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (userRepository.existsByUsername(adminUsername)) {
            return;
        }
        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setNickname("管理员");
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
        log.info("已初始化管理员账号: {}", adminUsername);
    }
}
