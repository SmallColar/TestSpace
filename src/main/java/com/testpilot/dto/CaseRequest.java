package com.testpilot.dto;

import lombok.Data;

/**
 * 新建/更新自动化用例请求。带 id 表示更新。
 */
@Data
public class CaseRequest {

    private Long id;

    private String name;

    private String description;

    /** JSON 步骤数组 */
    private String steps;

    /** JSON 校验点数组 */
    private String assertions;

    /** JSON 公共变量数组 */
    private String publicVars;

    private Boolean enabled;
}
