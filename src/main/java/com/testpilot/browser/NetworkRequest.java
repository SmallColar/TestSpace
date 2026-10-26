package com.testpilot.browser;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 网络请求结构（浏览器自动化 Provider 的统一产出）。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NetworkRequest {

    private String url;
    private String method;
    private Integer status;
    private String requestHeaders;
    private String requestBody;
    private String responseHeaders;
    private String responseBody;
}
