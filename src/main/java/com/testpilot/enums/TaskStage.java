package com.testpilot.enums;

/**
 * 任务执行阶段。
 */
public enum TaskStage {
    /** 录制阶段（产出：业务接口列表） */
    RECORD,
    /** 编排解析阶段（产出：编排规则 JSON） */
    PARSE,
    /** 用例生成阶段（产出：用例列表） */
    GENERATE,
    /** 完成 */
    DONE
}
