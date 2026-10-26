package com.testpilot.common;

import com.testpilot.security.UserContext;

/**
 * 数据可见性与编辑权限（spec/SPEC-2026-10-15.md §2）。
 * <p>
 * 规则库与用例库的规则是：**看全部，改自己的**；管理员两者皆可。
 * 这类判断 URL 匹配器表达不了，所以放在服务层统一处理。
 */
public final class Perms {

    private Perms() {
    }

    /** 当前用户能否编辑归属于 {@code ownerId} 的资源 */
    public static boolean canEdit(Long ownerId) {
        if (UserContext.isAdmin()) {
            return true;
        }
        Long me = UserContext.get() == null ? null : UserContext.get().id();
        return ownerId != null && ownerId.equals(me);
    }

    /** 无编辑权限直接抛 40300 */
    public static void requireEdit(Long ownerId, String what) {
        if (!canEdit(ownerId)) {
            throw new BusinessException(40300, "无权修改他人的" + what);
        }
    }
}
