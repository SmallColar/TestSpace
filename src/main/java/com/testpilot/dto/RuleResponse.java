package com.testpilot.dto;

import com.testpilot.domain.ArrangeRule;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 编排规则响应。
 */
@Data
public class RuleResponse {

    private Long id;
    private Long userId;
    private String creatorName;
    private Long taskId;
    private Long derivedFrom;
    private Integer version;
    private String name;
    private String description;
    private String source;
    private String steps;
    private Boolean enabled;
    /** Cosine similarity to the current task; set only for vector recommendations. */
    private Double similarityScore;
    /** 当前登录用户能否编辑它 */
    private Boolean editable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RuleResponse from(ArrangeRule rule, boolean editable) {
        RuleResponse r = new RuleResponse();
        r.setId(rule.getId());
        r.setUserId(rule.getUserId());
        r.setTaskId(rule.getTaskId());
        r.setDerivedFrom(rule.getDerivedFrom());
        r.setVersion(rule.getVersion());
        r.setName(rule.getName());
        r.setDescription(rule.getDescription());
        r.setSource(rule.getSource() == null ? null : rule.getSource().name());
        r.setSteps(rule.getSteps());
        r.setEnabled(rule.getEnabled());
        r.setEditable(editable);
        r.setCreatedAt(rule.getCreatedAt());
        r.setUpdatedAt(rule.getUpdatedAt());
        return r;
    }
}
