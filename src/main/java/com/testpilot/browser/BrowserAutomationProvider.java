package com.testpilot.browser;

import java.util.List;
import java.util.Map;

/**
 * 浏览器自动化 Provider 抽象（可插拔扩展点）。
 * <p>默认实现 {@link PlaywrightMcpProvider}（经 Spring AI MCP Client 对接 Playwright MCP），
 * 可扩展 Selenium、Puppeteer 等实现，主流程不感知具体实现差异。
 */
public interface BrowserAutomationProvider {

    /** Provider 名称（对应配置 testpilot.browser.provider） */
    String providerName();

    /** 打开页面 */
    void navigate(String url);

    /** 获取捕获的网络请求 */
    List<NetworkRequest> getNetworkRequests();

    /** 等待元素出现 / 等待毫秒 */
    void waitFor(String selectorOrMillis);

    /** Invoke a browser MCP action exposed to the recording agent. */
    void perform(String toolName, Map<String, Object> arguments);

    /** Read the current page's accessible snapshot for agent reasoning. */
    String snapshot();

    /** 释放浏览器资源 */
    void close();
}
