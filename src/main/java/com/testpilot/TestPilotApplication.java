package com.testpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TestPilot 启动入口。
 * <p>Web 多 Agent LLM ReAct Agent 系统，基于 Spring Boot 3 + Spring AI，
 * 对接 Playwright MCP 完成「录制 → 编排 → 用例生成与保存」闭环。
 */
@SpringBootApplication
public class TestPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestPilotApplication.class, args);
    }
}
