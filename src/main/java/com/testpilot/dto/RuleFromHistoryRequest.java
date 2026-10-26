package com.testpilot.dto;

import lombok.Data;

/**
 * 基于历史规则新建或更新（spec/SPEC-2026-10-15.md §4 来源 b）。
 */
@Data
public class RuleFromHistoryRequest {

    private Long taskId;

    /** 被参考的历史规则 id */
    private Long historyRuleId;

    /** SAVE_AS_NEW creates a derived scene; UPDATE_HISTORY updates the owned history rule and also creates a task scene. */
    private String mode;

    /** MANUAL_HISTORY, VECTOR_RECOMMENDED_HISTORY, or legacy AI_RECOMMENDED_HISTORY. */
    private String source;

    private String name;

    private String description;

    /** 用户调整后的 JSON 步骤；为空则原样沿用历史规则的步骤 */
    private String steps;
}
