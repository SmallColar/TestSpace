package com.testpilot.domain;

import com.testpilot.enums.RuleSource;
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
 * 编排规则实体，同时充当「编排规则库」的存储
 * （spec/SPEC-2026-10-15.md §4）。
 * <p>
 * 可见性：所有已认证用户都能查看全部规则；只有自己的能改，管理员能改全部。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "arrange_rule", indexes = {
        @Index(name = "idx_rule_task", columnList = "task_id"),
        @Index(name = "idx_rule_source", columnList = "source"),
        @Index(name = "idx_rule_owner", columnList = "user_id")
})
public class ArrangeRule extends BaseEntity {

    /** 归属用户（决定谁能编辑） */
    @Column(name = "user_id")
    private Long userId;

    /** 来源任务；从录制结果生成时回填，手工新建的可以为空 */
    @Column(name = "task_id")
    private Long taskId;

    /** 基于哪条历史规则派生出来的（"基于历史新建/更新"） */
    @Column(name = "derived_from")
    private Long derivedFrom;

    /** 版本号：每次基于历史更新 +1 */
    @Column(name = "version")
    private Integer version = 1;

    @Column(name = "name", length = 256)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 32)
    private RuleSource source;

    /** JSON 编排链路（仅有序接口） */
    @Column(name = "steps", columnDefinition = "TEXT")
    private String steps;

    /** 是否已入向量库 */
    @Column(name = "vectorized")
    private Boolean vectorized;

    @Column(name = "enabled")
    private Boolean enabled = true;
}
