package com.testpilot.dto;

import lombok.Data;

import java.util.List;

/**
 * 从录制结果拖拽生成编排规则（spec/SPEC-2026-10-15.md §4 来源 a）。
 */
@Data
public class RuleFromRecordRequest {

    private Long taskId;

    private String name;

    private String description;

    /** 用户在编排序列里排好序的接口 id 列表 */
    private List<Long> apiIds;
}
