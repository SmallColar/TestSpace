package com.testpilot.dto;

import com.testpilot.domain.TestCase;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自动化用例响应。
 */
@Data
public class CaseResponse {

    private Long id;
    private Long userId;
    private String creatorName;
    private Long taskId;
    private Long ruleId;
    private String sourceType;
    private String name;
    private String description;
    private String steps;
    private String assertions;
    private String publicVars;
    private String status;
    private Boolean enabled;
    private Boolean editable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CaseResponse from(TestCase tc, boolean editable) {
        CaseResponse r = new CaseResponse();
        r.setId(tc.getId());
        r.setUserId(tc.getUserId());
        r.setTaskId(tc.getTaskId());
        r.setRuleId(tc.getRuleId());
        r.setSourceType(tc.getSourceType());
        r.setName(tc.getName());
        r.setDescription(tc.getDescription());
        r.setSteps(tc.getSteps());
        r.setAssertions(tc.getAssertions());
        r.setPublicVars(tc.getPublicVars());
        r.setStatus(tc.getStatus() == null ? null : tc.getStatus().name());
        r.setEnabled(tc.getEnabled());
        r.setEditable(editable);
        r.setCreatedAt(tc.getCreatedAt());
        r.setUpdatedAt(tc.getUpdatedAt());
        return r;
    }
}
