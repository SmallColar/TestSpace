package com.testpilot.controller;

import com.testpilot.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenAPI 模拟平台：接收已确认用例，幂等（按 uniqueKey 去重）。
 * <p>生产环境可替换为真实 OpenAPI 平台网关。
 */
@RestController
@RequestMapping("/openapi/mock")
public class MockTestPlatformController {

    private final ConcurrentHashMap<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    @PostMapping("/cases")
    public R<Map<String, Object>> receiveCase(@RequestBody Map<String, Object> payload) {
        String uniqueKey = payload.get("uniqueKey") == null ? null : String.valueOf(payload.get("uniqueKey"));
        boolean duplicated = uniqueKey != null && store.containsKey(uniqueKey);
        if (uniqueKey != null) {
            store.put(uniqueKey, payload);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("uniqueKey", uniqueKey);
        resp.put("duplicated", duplicated);
        resp.put("message", duplicated ? "已存在，幂等跳过" : "写入成功");
        return R.ok(resp);
    }

    @GetMapping("/cases")
    public R<Collection<Map<String, Object>>> listCases() {
        return R.ok(store.values());
    }
}
