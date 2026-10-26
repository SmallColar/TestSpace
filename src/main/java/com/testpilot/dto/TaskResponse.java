package com.testpilot.dto;

import com.testpilot.domain.AgentTask;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务响应（对外视图）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {

    private Long id;
    /** 归属用户 ID，管理端靠它区分任务属于谁 */
    private Long userId;
    private String stage;
    private String status;
    private String name;
    private String targetUrl;
    private String recordingGoal;
    private String caseGenerationPrompt;
    private String browserProvider;
    private String resultSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TaskResponse from(AgentTask task) {
        return new TaskResponse(
                task.getId(),
                task.getUserId(),
                task.getStage() == null ? null : task.getStage().name(),
                task.getStatus() == null ? null : task.getStatus().name(),
                task.getName(),
                task.getTargetUrl(),
                task.getRecordingGoal(),
                task.getCaseGenerationPrompt(),
                task.getBrowserProvider(),
                task.getResultSummary(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
