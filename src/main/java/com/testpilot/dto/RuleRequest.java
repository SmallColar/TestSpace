package com.testpilot.dto;

import lombok.Data;

/**
 * 新建/更新编排规则请求。带 id 表示更新。
 */
@Data
public class RuleRequest {

    private Long id;

    private String name;

    private String description;

    /** JSON 步骤数组，元素形如 {seq, api, method, url, variables} */
    private String steps;

    /** 规则来源，缺省 RECORD */
    private String source;

    /** 来源任务，可空 */
    private Long taskId;

    /** 是否启用；null 表示不改动 */
    private Boolean enabled;
}
