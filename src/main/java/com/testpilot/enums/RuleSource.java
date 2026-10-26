package com.testpilot.enums;

/**
 * 编排规则来源（spec/SPEC-2026-10-15.md §4）。
 * <p>
 * 规则库的"新建"有三个来源：从录制结果拖拽（RECORD）、
 * 基于历史规则新建/更新（HISTORY）、上传测试用例提取步骤（UPLOAD）。
 * MANUAL/AUTO/PARSED 保留给任务流水线自动解析出来的规则。
 */
public enum RuleSource {

    /** 从录制结果拖拽 API 生成 */
    RECORD,

    /** 基于历史规则新建或更新 */
    HISTORY,

    /** Historical rule explicitly selected by a user. */
    MANUAL_HISTORY,

    /** Historical rule added from an AI recommendation. */
    AI_RECOMMENDED_HISTORY,

    /** Historical rule selected from vector similarity recommendations. */
    VECTOR_RECOMMENDED_HISTORY,

    /** 上传测试用例，提取其中的步骤 */
    UPLOAD,

    /** 手工填写编排文本 */
    MANUAL,

    /** 流水线自动生成 */
    AUTO,

    /** 从文档解析 */
    PARSED
}
