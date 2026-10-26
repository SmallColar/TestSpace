package com.testpilot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建任务请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskRequest {

    private Long id;

    private String name;

    /** AI exploration goals, boundaries, and interface selection requirements. */
    private String recordingGoal;

    /** 目标页面地址（录制 / 编排任务的目标页面） */
    private String targetUrl;

    /** 浏览器 Provider；新任务默认使用 chrome-devtools-mcp */
    private String browserProvider;

}
