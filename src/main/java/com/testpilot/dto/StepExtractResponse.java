package com.testpilot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 从上传的测试用例里提取出的步骤预览。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StepExtractResponse {

    private String ruleName;

    /** JSON 步骤数组字符串，可直接作为规则的 steps 保存 */
    private String steps;

    /** 便于前端展示的步骤摘要 */
    private List<String> lines;
}
