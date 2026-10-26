package com.testpilot.domain;

import com.testpilot.enums.CaseStatus;
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
 * 自动化用例实体，同时充当「自动化用例库」的存储
 * （spec/SPEC-2026-10-15.md §6）。
 * <p>
 * 可见性：所有已认证用户都能查看全部用例；只有自己的能改，管理员能改全部。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "test_case", indexes = {
        @Index(name = "idx_case_task", columnList = "task_id"),
        @Index(name = "idx_case_rule", columnList = "rule_id"),
        @Index(name = "idx_case_owner", columnList = "user_id")
})
public class TestCase extends BaseEntity {

    /** 归属用户（决定谁能编辑） */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "source_type", length = 32)
    private String sourceType;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * JSON 步骤。原始字面值已被替换成 {@code {{占位符}}}，
     * 运行前用公共变量 + 环境变量回填。
     */
    @Column(name = "steps", columnDefinition = "TEXT")
    private String steps;

    /** JSON 校验点（断言） */
    @Column(name = "assertions", columnDefinition = "TEXT")
    private String assertions;

    /** JSON 入参组合 */
    @Column(name = "params", columnDefinition = "TEXT")
    private String params;

    /** JSON 数组：本用例确认过的公共变量 [{name,type,value,sourceExpr,description}] */
    @Column(name = "public_vars", columnDefinition = "TEXT")
    private String publicVars;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32)
    private CaseStatus status;

    @Column(name = "enabled")
    private Boolean enabled = true;

    /** 幂等唯一键 */
    @Column(name = "unique_key", length = 512, unique = true)
    private String uniqueKey;
}
