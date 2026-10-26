package com.testpilot.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAPI 持久化：将已确认的用例写入 OpenAPI 模拟平台（幂等，唯一键去重）。
 * <p>该工具只在用户确认后由服务层显式调用，确保「先确认、后落库」。
 */
@Component
public class OpenApiPersistTool {

    /** OpenAPI 模拟平台固定写入地址（与 MockTestPlatformController 的 /openapi/mock/cases 对应） */
    @Value("${testpilot.platform.sync-url:http://localhost:8080/openapi/mock/cases}")
    private String syncUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Tool(description = "将已确认的测试用例写入 OpenAPI 模拟平台（幂等，按唯一键去重）")
    public String persistCase(
            @ToolParam(description = "用例名称") String name,
            @ToolParam(description = "步骤 JSON") String steps,
            @ToolParam(description = "断言 JSON") String assertions,
            @ToolParam(description = "参数 JSON") String params,
            @ToolParam(description = "唯一键") String uniqueKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("steps", steps);
        payload.put("assertions", assertions);
        payload.put("params", params);
        payload.put("uniqueKey", uniqueKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(syncUrl, request, String.class);
            return "写入成功: " + resp.getBody();
        } catch (Exception e) {
            return "写入失败: " + e.getMessage();
        }
    }
}
