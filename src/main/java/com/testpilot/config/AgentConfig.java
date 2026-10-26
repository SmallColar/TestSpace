package com.testpilot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds dedicated chat clients for browser recording and structured case generation. */
@Configuration
public class AgentConfig {

    /** Recording browser actions must not inherit database or test-case tools. */
    @Bean
    public ChatClient recordingAgent(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    /** Structured scene-to-case generation has no side-effect tools. */
    @Bean
    public ChatClient caseDraftAgent(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

}
