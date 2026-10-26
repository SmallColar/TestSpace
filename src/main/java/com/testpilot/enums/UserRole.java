package com.testpilot.enums;

/**
 * 用户角色。
 * <p>权限模型见 spec/SPEC-2026-10-15.md §2：
 * 普通用户仅能查看自己的录制任务，规则库/用例库可查看全部但只能编辑自己的；
 * 管理员可查看与编辑全部。
 */
public enum UserRole {
    /** 普通用户（注册默认角色） */
    USER,
    /** 管理员 */
    ADMIN
}
