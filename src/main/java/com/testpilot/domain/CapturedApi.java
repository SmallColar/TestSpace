package com.testpilot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 捕获的原始/业务接口实体。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "captured_api", indexes = {
        @Index(name = "idx_captured_api_task", columnList = "task_id"),
        @Index(name = "idx_captured_api_task_seq", columnList = "task_id,seq")
})
public class CapturedApi extends BaseEntity {

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(name = "method", length = 16)
    private String method;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "request_headers", columnDefinition = "TEXT")
    private String requestHeaders;

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "response_headers", columnDefinition = "TEXT")
    private String responseHeaders;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    /** 捕获顺序 */
    @Column(name = "seq")
    private Integer seq;

    /** 是否业务接口（过滤后标记） */
    @Column(name = "business")
    private Boolean business;

    /**
     * 用户在录制节点逐项勾选的结果：勾中=这条接口需要
     * （spec/SPEC-2026-10-15.md §5）。
     */
    @Column(name = "checked")
    private Boolean checked = true;
}
