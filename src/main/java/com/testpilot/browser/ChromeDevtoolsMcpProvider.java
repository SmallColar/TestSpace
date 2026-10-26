package com.testpilot.browser;

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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Browser automation and incremental network capture through Chrome DevTools MCP. */
public class ChromeDevtoolsMcpProvider implements BrowserAutomationProvider {

    public static final String PROVIDER_NAME = "chrome-devtools-mcp";

    private static final Pattern PAGE_LINE = Pattern.compile("(?m)^\\s*(\\d+):\\s*(.+)$");
    private static final Pattern REQUEST_LINE = Pattern.compile(
            "(?m)^reqid=(\\d+)\\s+(\\S+)\\s+(.+?)\\s+\\[([^]]+)](?:\\s+.*)?$");
    private static final Pattern SECTION = Pattern.compile("(?m)^###\\s+(.+?)\\s*$");
    private static final int REQUEST_PAGE_SIZE = 5000;

    private final McpSyncClient client;
    /** Ordered by first observation so the API list follows browser activity order. */
    private final Map<Long, NetworkRequest> observedRequests = new LinkedHashMap<>();
    private final Map<Long, Boolean> detailComplete = new LinkedHashMap<>();
    private Integer pageId;

    public ChromeDevtoolsMcpProvider(ServerParameters serverParameters) {
        this.client = McpClient.sync(new StdioClientTransport(serverParameters))
                .requestTimeout(Duration.ofSeconds(90))
                .build();
        this.client.initialize();
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public synchronized void navigate(String url) {
        String pageResponse = callTool("new_page", Map.of("url", url));
        pageId = parsePageId(pageResponse, url);
        if (pageId == null) {
            pageId = parsePageId(callTool("list_pages", Map.of()), url);
        }
        if (pageId == null) {
            throw new BusinessException(50002, "Chrome DevTools MCP 已打开网页，但未能读取页面 ID");
        }
        collectNetworkRequestsQuietly();
    }

    @Override
    public synchronized List<NetworkRequest> getNetworkRequests() {
        collectNetworkRequests();
        return new ArrayList<>(observedRequests.values());
    }

    @Override
    public synchronized void waitFor(String textOrMillis) {
        if (textOrMillis != null && textOrMillis.matches("\\d+")) {
            try {
                Thread.sleep(Math.min(Long.parseLong(textOrMillis), 120_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(50002, "等待浏览器操作时被中断");
            }
        } else {
            callPageTool("wait_for", Map.of("text", List.of(textOrMillis), "timeout", 15_000));
        }
        collectNetworkRequestsQuietly();
    }

    @Override
    public synchronized void perform(String toolName, Map<String, Object> arguments) {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        String uid = firstValue(args, "uid", "ref");
        switch (toolName) {
            case "browser_click" -> callPageTool("click", Map.of("uid", required(uid, "元素引用")));
            case "browser_type" -> callPageTool("fill", Map.of(
                    "uid", required(uid, "元素引用"),
                    "value", firstValue(args, "text", "value") == null ? "" : firstValue(args, "text", "value")));
            case "browser_press_key" -> callPageTool("press_key", Map.of(
                    "key", required(firstValue(args, "key"), "按键")));
            case "browser_navigate" -> {
                String url = required(firstValue(args, "url"), "网址");
                callPageTool("navigate_page", Map.of("type", "url", "url", url));
            }
            case "browser_wait_for" -> {
                String value = firstValue(args, "text", "time");
                waitFor(value);
                return;
            }
            default -> throw new BusinessException(40002, "Chrome DevTools MCP 不支持该浏览器操作：" + toolName);
        }
        collectNetworkRequestsQuietly();
    }

    @Override
    public synchronized String snapshot() {
        return callPageTool("take_snapshot", Map.of());
    }

    @Override
    public void close() {
        try {
            client.closeGracefully();
        } catch (Exception ignored) {
            // Best effort resource cleanup; do not override the recording result.
        }
    }

    private void collectNetworkRequests() {
        if (pageId == null) return;
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("pageId", pageId);
        args.put("pageSize", REQUEST_PAGE_SIZE);
        args.put("includePreservedRequests", true);
        // These are the browser resource types that normally carry application API traffic.
        args.put("resourceTypes", List.of("xhr", "fetch", "eventsource"));

        String response = callTool("list_network_requests", args);
        Matcher matcher = REQUEST_LINE.matcher(response);
        while (matcher.find()) {
            long requestId;
            try {
                requestId = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {
                continue;
            }
            NetworkRequest request = observedRequests.computeIfAbsent(requestId, ignored -> new NetworkRequest());
            request.setMethod(matcher.group(2));
            request.setUrl(matcher.group(3));
            try {
                request.setStatus(Integer.valueOf(matcher.group(4)));
            } catch (NumberFormatException ignored) {
                request.setStatus(null); // pending or failed requests have a non-numeric status
            }

            // Revisit pending requests at later checkpoints so their response body can settle.
            boolean pending = "pending".equalsIgnoreCase(matcher.group(4));
            if (!Boolean.TRUE.equals(detailComplete.get(requestId)) || pending) {
                String detail = callToolQuietly("get_network_request", Map.of("pageId", pageId, "reqid", requestId));
                if (detail != null) {
                    applyDetail(request, detail);
                    detailComplete.put(requestId, !pending && hasResponse(request));
                }
            }
        }
    }

    private void collectNetworkRequestsQuietly() {
        try {
            collectNetworkRequests();
        } catch (Exception ignored) {
            // Intermediate telemetry must not stop the agent from completing a browser action.
            // A final collection in getNetworkRequests() still reports persistent MCP failures.
        }
    }

    private boolean hasResponse(NetworkRequest request) {
        return request.getStatus() != null || hasText(request.getResponseHeaders()) || hasText(request.getResponseBody());
    }

    private void applyDetail(NetworkRequest request, String detail) {
        Matcher url = Pattern.compile("(?m)^## Request (.+)$").matcher(detail);
        if (url.find()) request.setUrl(url.group(1).trim());
        Matcher status = Pattern.compile("(?m)^Status:\\s*(\\d+)").matcher(detail);
        if (status.find()) request.setStatus(Integer.parseInt(status.group(1)));

        List<MatchResult> sections = SECTION.matcher(detail).results().toList();
        for (int i = 0; i < sections.size(); i++) {
            MatchResult section = sections.get(i);
            int start = section.end();
            int end = i + 1 < sections.size() ? sections.get(i + 1).start() : detail.length();
            String content = detail.substring(start, end).trim();
            switch (section.group(1).toLowerCase(Locale.ROOT)) {
                case "request headers" -> request.setRequestHeaders(parseHeaders(content));
                case "request body" -> request.setRequestBody(content);
                case "response headers" -> request.setResponseHeaders(parseHeaders(content));
                case "response body" -> request.setResponseBody(content);
                default -> { }
            }
        }
    }

    private String parseHeaders(String text) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String line : text.split("\\R")) {
            String item = line.trim();
            if (!item.startsWith("-")) continue;
            item = item.substring(1).trim();
            int separator = item.indexOf(':');
            if (separator > 0) headers.put(item.substring(0, separator).trim(), item.substring(separator + 1).trim());
        }
        if (headers.isEmpty()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(headers);
        } catch (Exception ignored) {
            return headers.toString();
        }
    }

    private Integer parsePageId(String response, String targetUrl) {
        Matcher matcher = PAGE_LINE.matcher(response == null ? "" : response);
        Integer first = null;
        while (matcher.find()) {
            int id;
            try {
                id = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (first == null) first = id;
            if (matcher.group(2).contains(targetUrl)) return id;
        }
        return first;
    }

    private String callPageTool(String name, Map<String, Object> arguments) {
        if (pageId == null) throw new BusinessException(50002, "Chrome DevTools 页面尚未初始化");
        Map<String, Object> args = new LinkedHashMap<>(arguments);
        args.put("pageId", pageId);
        return callTool(name, args);
    }

    private String callToolQuietly(String name, Map<String, Object> args) {
        try {
            return callTool(name, args);
        } catch (Exception ignored) {
            // A response body can expire while the page is still active; keep the URL/method.
            return null;
        }
    }

    private String callTool(String name, Map<String, Object> args) {
        try {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(name, args));
            if (Boolean.TRUE.equals(result.isError())) {
                throw new BusinessException(50002, "Chrome DevTools MCP 工具调用失败 " + name + "：" + result.content());
            }
            return result.content().stream()
                    .filter(content -> content instanceof McpSchema.TextContent)
                    .map(content -> ((McpSchema.TextContent) content).text())
                    .collect(Collectors.joining("\n"));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(50002, "Chrome DevTools MCP 工具调用失败 " + name + "：" + e.getMessage());
        }
    }

    private String firstValue(Map<String, Object> args, String... keys) {
        for (String key : keys) {
            Object value = args.get(key);
            if (value != null && !value.toString().isBlank()) return value.toString();
        }
        return null;
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) throw new BusinessException(40001, "缺少浏览器操作参数：" + label);
        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
