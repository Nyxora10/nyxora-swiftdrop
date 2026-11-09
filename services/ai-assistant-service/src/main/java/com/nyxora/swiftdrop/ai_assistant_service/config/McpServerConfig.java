package com.nyxora.swiftdrop.ai_assistant_service.config;

import com.nyxora.swiftdrop.ai_assistant_service.tool.ParcelTrackingTool;
import com.nyxora.swiftdrop.ai_assistant_service.tool.RateQuoteTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Model Context Protocol (MCP) server configuration.
 * Exposes SwiftDrop AI tools (tracking and quotations) to connected MCP clients.
 */
@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(
            ParcelTrackingTool parcelTrackingTool,
            RateQuoteTool rateQuoteTool) {

        return MethodToolCallbackProvider.builder()
                .toolObjects(parcelTrackingTool, rateQuoteTool)
                .build();
    }
}

