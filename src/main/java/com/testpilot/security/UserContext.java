package com.testpilot.security;

import com.testpilot.common.BusinessException;

/**
 * 当前请求的登录用户上下文（ThreadLocal）。
 * <p>
 * 由 {@link AuthInterceptor} 在 preHandle 写入、afterCompletion 清理。
 * Service 层用它做数据可见性判断（spec/SPEC-2026-10-15.md §2）：
 * 普通用户只能看/改自己的数据，管理员可以看/改全部。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    /** 未登录时返回 null，用于放行接口内的可选判断 */
    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 取当前用户，未登录直接抛 401 */
    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException(40100, "未登录或登录已过期");
        }
        return user;
    }

    public static Long userId() {
        return require().id();
    }

    /** 管理员视为已登录，否则要求当前用户 */
    public static boolean isAdmin() {
        LoginUser user = HOLDER.get();
        return user != null && user.isAdmin();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
