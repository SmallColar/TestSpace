package com.testpilot.enums;

/**
 * 任务状态。
 */
public enum TaskStatus {
    /** 已创建，待执行 */
    PENDING,
    /** 执行中 */
    RUNNING,
    /** 已完成 */
    COMPLETED,
    /** 执行失败 */
    FAILED,
    /** 用户主动终止 */
    CANCELLED
}
