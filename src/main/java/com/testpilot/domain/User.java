package com.testpilot.domain;

import com.testpilot.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户实体。
 * <p>密码以 BCrypt 哈希存储，不存明文。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sys_user", indexes = {
        @Index(name = "idx_sys_user_username", columnList = "username", unique = true)
})
public class User extends BaseEntity {

    @Column(name = "username", nullable = false, length = 64, unique = true)
    private String username;

    /** BCrypt 哈希后的密码 */
    @Column(name = "password", nullable = false, length = 128)
    private String password;

    /** 昵称 */
    @Column(name = "nickname", length = 64)
    private String nickname;

    /** 头像（本期存 URL / 文本，后续可扩展为上传） */
    @Column(name = "avatar", length = 512)
    private String avatar;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private UserRole role = UserRole.USER;

    public boolean isAdmin() {
        return this.role == UserRole.ADMIN;
    }
}
