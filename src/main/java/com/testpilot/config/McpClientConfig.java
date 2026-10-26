package com.testpilot.config;

import io.modelcontextprotocol.client.transport.ServerParameters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/** Declares isolated stdio connections to supported browser MCP servers. */
@Configuration
public class McpClientConfig {

    @Bean
    public ServerParameters playwrightServerParameters(
            @Value("${testpilot.browser.command:npx}") String command,
            @Value("${testpilot.browser.args:@playwright/mcp@latest}") String args) {
        // 逗号分隔的启动参数列表（去除折叠换行带来的空白）
        String[] argArray = Arrays.stream(args.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        return ServerParameters.builder(command).args(argArray).build();
    }

    @Bean
    public ServerParameters chromeDevtoolsServerParameters(
            @Value("${testpilot.browser.chrome-command:npx}") String command,
            @Value("${testpilot.browser.chrome-args:-y,chrome-devtools-mcp@1.11.0,--headless,--isolated,--no-usage-statistics,--no-performance-crux,--redactNetworkHeaders,--maxNavigationSaved=25}") String args) {
        String[] argArray = Arrays.stream(args.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        return ServerParameters.builder(command).args(argArray).build();
    }
}
