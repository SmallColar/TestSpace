package com.testpilot.domain;

import com.testpilot.enums.TaskStage;
import com.testpilot.enums.TaskStatus;
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
 * 任务实体。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "agent_task", indexes = {
        @Index(name = "idx_agent_task_status", columnList = "status"),
        @Index(name = "idx_agent_task_created", columnList = "created_at"),
        @Index(name = "idx_agent_task_user", columnList = "user_id")
})
public class AgentTask extends BaseEntity {

    /**
     * 归属用户：普通用户只能看到自己的任务，管理员可见全部
     * （spec/SPEC-2026-10-15.md §2）。
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", length = 32)
    private TaskStage stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TaskStatus status;

    /** 目标页面 URL */
    @Column(name = "target_url", length = 2048)
    private String targetUrl;

    @Column(name = "name", length = 256)
    private String name;

    /** Natural-language exploration goals and interface selection requirements. */
    @Column(name = "recording_goal", columnDefinition = "TEXT")
    private String recordingGoal;

    /** Additional user instructions for AI-generated cases. */
    @Column(name = "case_generation_prompt", columnDefinition = "TEXT")
    private String caseGenerationPrompt;

    /** Structured AI generation draft; copied to the case library when saved by the user. */
    @Column(name = "generated_cases", columnDefinition = "TEXT")
    private String generatedCases;

    /** 浏览器自动化 Provider（可插拔），默认 playwright-mcp */
    @Column(name = "browser_provider", length = 64)
    private String browserProvider;

    /** 执行结果摘要 */
    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;
}
