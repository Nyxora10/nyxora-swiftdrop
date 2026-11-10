package com.nyxora.swiftdrop.ai_assistant_service.config;

import com.nyxora.swiftdrop.ai_assistant_service.tool.ParcelTrackingTool;
import com.nyxora.swiftdrop.ai_assistant_service.tool.RateQuoteTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerConfigTest {

    @Test
    @DisplayName("Should expose tracking and quotation operations via ToolCallbackProvider")
    void shouldProvideMcpToolCallbacks() {
        McpServerConfig config = new McpServerConfig();
        ParcelTrackingTool trackingTool = new ParcelTrackingTool();
        RateQuoteTool quoteTool = new RateQuoteTool();

        ToolCallbackProvider provider = config.mcpToolCallbackProvider(trackingTool, quoteTool);

        assertThat(provider).isNotNull();
        ToolCallback[] callbacks = provider.getToolCallbacks();
        assertThat(callbacks).isNotNull().hasSize(2);

        List<String> toolNames = Arrays.stream(callbacks)
                .map(cb -> cb.getToolDefinition().name())
                .toList();

        assertThat(toolNames).containsExactlyInAnyOrder("get_parcel_tracking", "calculate_rate_quote");
    }
}

