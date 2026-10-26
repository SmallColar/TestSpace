package com.testpilot.browser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testpilot.common.BusinessException;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 默认浏览器 Provider：经 Spring AI MCP Client（stdio）对接独立进程的 Playwright MCP Server。
 * <p>工具名映射：browser_navigate / browser_network_requests / browser_network_request / browser_wait_for。
 * <p>注意两点易踩的坑：
 * <ul>
 *   <li>{@code browser_wait_for} 的 {@code time} 单位是「秒」，不是毫秒；</li>
 *   <li>{@code browser_network_requests} 返回的是 Markdown 文本而不是 JSON，
 *       请求/响应体需要用 {@code browser_network_request} 按 index 单独取。</li>
 * </ul>
 */
public class PlaywrightMcpProvider implements BrowserAutomationProvider {

    public static final String PROVIDER_NAME = "playwright-mcp";

    /** browser_network_requests 输出的行格式：{@code 11. [GET] https://x/y => [200]} */
    private static final Pattern LIST_LINE =
            Pattern.compile("^\\s*(\\d+)\\.\\s*\\[(\\w+)]\\s*(\\S+)\\s*=>\\s*\\[(\\d+)]");

    /** 单次录制最多回捞多少条请求的明细，避免一个接口爆炸的页面把录制拖到几分钟 */
    private static final int MAX_DETAIL_FETCH = 25;

    private static final String RESULT_PREFIX = "### Result";

    private final McpSyncClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlaywrightMcpProvider(ServerParameters serverParameters) {
        this.client = McpClient.sync(new StdioClientTransport(serverParameters))
                // 默认 20s 对真实页面导航偏紧（首次导航还要拉起浏览器），放宽到 60s
                .requestTimeout(Duration.ofSeconds(60))
                .build();
        this.client.initialize();
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public void navigate(String url) {
        callTool("browser_navigate", Map.of("url", url));
    }

    @Override
    public List<NetworkRequest> getNetworkRequests() {
        List<RawEntry> entries = parseEntries(callTool("browser_network_requests", Map.of()));
        List<NetworkRequest> list = new ArrayList<>(entries.size());
        int fetched = 0;
        for (RawEntry entry : entries) {
            NetworkRequest req = entry.request();
            if (fetched < MAX_DETAIL_FETCH) {
                enrich(req, entry.index());
                fetched++;
            }
            list.add(req);
        }
        return list;
    }

    @Override
    public void waitFor(String selectorOrMillis) {
        // 纯数字按毫秒理解，但 Playwright MCP 的 browser_wait_for.time 单位是「秒」，
        // 传毫秒会被当成几千秒直接卡死，所以这里做单位换算。
        if (selectorOrMillis != null && selectorOrMillis.matches("\\d+")) {
            double seconds = Math.max(0.1, Long.parseLong(selectorOrMillis) / 1000.0);
            callTool("browser_wait_for", Map.of("time", seconds));
        } else {
            callTool("browser_wait_for", Map.of("text", selectorOrMillis));
        }
    }

    @Override
    public void perform(String toolName, Map<String, Object> arguments) {
        if (!List.of("browser_click", "browser_type", "browser_fill_form", "browser_press_key",
                "browser_select_option", "browser_hover", "browser_drag", "browser_navigate", "browser_wait_for")
                .contains(toolName)) {
            throw new BusinessException(40002, "录制 Agent 不允许调用该浏览器操作：" + toolName);
        }
        callTool(toolName, arguments == null ? Map.of() : arguments);
    }

    @Override
    public String snapshot() {
        return callTool("browser_snapshot", Map.of());
    }

    @Override
    public void close() {
        try {
            client.closeGracefully();
        } catch (Exception ignored) {
            // 资源释放失败不影响主流程
        }
    }

    private String callTool(String name, Map<String, Object> args) {
        try {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(name, args));
            return result.content().stream()
                    .filter(c -> c instanceof McpSchema.TextContent)
                    .map(c -> ((McpSchema.TextContent) c).text())
                    .collect(Collectors.joining("\n"));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(50002, "Playwright MCP 工具调用失败 " + name + "：" + e.getMessage());
        }
    }

    /** 明细回捞失败不应让整次录制失败，最多丢一点请求体/响应体 */
    private String callToolQuietly(String name, Map<String, Object> args) {
        try {
            return callTool(name, args);
        } catch (Exception e) {
            return null;
        }
    }

    private record RawEntry(int index, NetworkRequest request) {
    }

    /**
     * 解析 {@code browser_network_requests} 的输出。
     * <p>优先按 JSON 解析（兼容未来版本或自建 Server），失败再按 Markdown 行解析。
     */
    private List<RawEntry> parseEntries(String raw) {
        List<RawEntry> list = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return list;
        }
        if (parseJsonEntries(raw, list)) {
            return list;
        }
        int fallbackIndex = 0;
        for (String line : raw.split("\n")) {
            Matcher m = LIST_LINE.matcher(line);
            if (!m.find()) {
                continue;
            }
            fallbackIndex++;
            NetworkRequest req = new NetworkRequest();
            req.setMethod(m.group(2));
            req.setUrl(m.group(3));
            try {
                req.setStatus(Integer.parseInt(m.group(4)));
            } catch (NumberFormatException ignored) {
                // 状态码缺失时留空
            }
            list.add(new RawEntry(Integer.parseInt(m.group(1)), req));
        }
        // Markdown 行里的序号就是 MCP 的 index；万一序号缺失则退回行序
        if (fallbackIndex == 0) {
            return list;
        }
        return list;
    }

    private boolean parseJsonEntries(String raw, List<RawEntry> out) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode arr = root.isArray() ? root : root.path("requests");
            if (!arr.isArray() || arr.isEmpty()) {
                return false;
            }
            int i = 0;
            for (JsonNode n : arr) {
                i++;
                NetworkRequest req = new NetworkRequest();
                req.setUrl(n.path("url").asText(null));
                req.setMethod(n.path("method").asText(null));
                req.setStatus(n.path("status").isNull() ? null : n.path("status").asInt());
                req.setRequestHeaders(n.path("requestHeaders").isNull() ? null : n.path("requestHeaders").toString());
                req.setRequestBody(n.path("postData").isNull() ? null : n.path("postData").toString());
                req.setResponseBody(n.path("response").isNull() ? null : n.path("response").toString());
                out.add(new RawEntry(n.path("index").asInt(i), req));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 按 index 补齐请求头/响应头/请求体/响应体 */
    private void enrich(NetworkRequest req, int index) {
        String detail = callToolQuietly("browser_network_request", Map.of("index", index));
        applyDetail(req, detail);

        if (hasBody(req.getMethod())) {
            String body = stripResult(callToolQuietly("browser_network_request",
                    Map.of("index", index, "part", "request-body")));
            if (body != null && !body.isBlank()) {
                req.setRequestBody(body);
            }
        }
        String respBody = stripResult(callToolQuietly("browser_network_request",
                Map.of("index", index, "part", "response-body")));
        if (respBody != null && !respBody.isBlank()) {
            req.setResponseBody(respBody);
        }
    }

    /** 解析明细文本：General 段取状态码，Request headers / Response headers 段转成 JSON */
    private void applyDetail(NetworkRequest req, String detail) {
        if (detail == null || detail.isBlank()) {
            return;
        }
        Map<String, String> requestHeaders = new LinkedHashMap<>();
        Map<String, String> responseHeaders = new LinkedHashMap<>();
        Map<String, String> section = null;

        for (String line : detail.split("\n")) {
            if (line.startsWith(RESULT_PREFIX)) {
                continue;
            }
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (line.startsWith("  ") && !line.startsWith("    ") && !trimmed.contains(":")) {
                section = switch (trimmed) {
                    case "Request headers" -> requestHeaders;
                    case "Response headers" -> responseHeaders;
                    default -> null;
                };
                continue;
            }
            if (line.startsWith("    ") && section != null && trimmed.contains(":")) {
                int idx = trimmed.indexOf(':');
                section.put(trimmed.substring(0, idx).trim(), trimmed.substring(idx + 1).trim());
            }
        }

        if (req.getRequestHeaders() == null && !requestHeaders.isEmpty()) {
            req.setRequestHeaders(toJson(requestHeaders));
        }
        if (!responseHeaders.isEmpty()) {
            // 响应头没有独立字段，拼进响应体里一起留给下游分析
            req.setResponseHeaders(toJson(responseHeaders));
        }
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasBody(String method) {
        if (method == null) {
            return false;
        }
        String m = method.toUpperCase();
        return "POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m) || "DELETE".equals(m);
    }

    private String stripResult(String text) {
        if (text == null) {
            return null;
        }
        String t = text.strip();
        if (t.startsWith(RESULT_PREFIX)) {
            t = t.substring(RESULT_PREFIX.length()).strip();
        }
        return t;
    }
}
