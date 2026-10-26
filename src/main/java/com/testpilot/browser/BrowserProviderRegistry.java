package com.testpilot.browser;

import com.testpilot.common.BusinessException;
import io.modelcontextprotocol.client.transport.ServerParameters;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 浏览器 Provider 注册表：按配置 {@code testpilot.browser.provider} 创建 Provider 实例。
 * <p>新增实现只需在 {@link #create(String)} 注册，主流程不改动（可扩展原则）。
 */
@Component
public class BrowserProviderRegistry {

    private final ServerParameters playwrightServerParameters;
    private final ServerParameters chromeDevtoolsServerParameters;

    public BrowserProviderRegistry(
            @Qualifier("playwrightServerParameters") ServerParameters playwrightServerParameters,
            @Qualifier("chromeDevtoolsServerParameters") ServerParameters chromeDevtoolsServerParameters) {
        this.playwrightServerParameters = playwrightServerParameters;
        this.chromeDevtoolsServerParameters = chromeDevtoolsServerParameters;
    }

    public BrowserAutomationProvider create(String providerName) {
        if (providerName == null || providerName.isBlank()
                || ChromeDevtoolsMcpProvider.PROVIDER_NAME.equalsIgnoreCase(providerName)) {
            return new ChromeDevtoolsMcpProvider(chromeDevtoolsServerParameters);
        }
        if (PlaywrightMcpProvider.PROVIDER_NAME.equalsIgnoreCase(providerName)) {
            return new PlaywrightMcpProvider(playwrightServerParameters);
        }
        throw new BusinessException(40002, "未注册的浏览器 Provider：" + providerName
                + "（当前支持：chrome-devtools-mcp、playwright-mcp）");
    }
}
