package com.testpilot.browser;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Map;

/** Restricted browser actions exposed only to the task-scoped recording agent. */
public class RecordingBrowserTools {
    private final BrowserAutomationProvider browser;

    public RecordingBrowserTools(BrowserAutomationProvider browser) {
        this.browser = browser;
    }

    @Tool(description = "Click a visible page element using its snapshot reference and accessible label.")
    public String click(@ToolParam(description = "Element reference from the latest snapshot") String ref,
                        @ToolParam(description = "Accessible element description") String element) {
        browser.perform("browser_click", Map.of("ref", ref, "element", element));
        return "Click completed";
    }

    @Tool(description = "Enter text into a visible input using its snapshot reference and accessible label.")
    public String typeText(@ToolParam(description = "Element reference from the latest snapshot") String ref,
                           @ToolParam(description = "Accessible element description") String element,
                           @ToolParam(description = "Text to enter") String text) {
        browser.perform("browser_type", Map.of("ref", ref, "element", element, "text", text));
        return "Text entered";
    }

    @Tool(description = "Press a keyboard key, for example Enter or Tab.")
    public String pressKey(@ToolParam(description = "Key name") String key) {
        browser.perform("browser_press_key", Map.of("key", key));
        return "Key pressed";
    }

    @Tool(description = "Wait for page text or a duration expressed in seconds.")
    public String waitFor(@ToolParam(description = "Text to appear or duration in seconds") String text) {
        browser.waitFor(text);
        return "Wait completed";
    }

    @Tool(description = "Read the current page's accessible snapshot after navigation or an interaction.")
    public String snapshot() {
        return browser.snapshot();
    }
}
