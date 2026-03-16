package com.example.agent.domain.model.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * ChatMessage holds the content exchanged in a conversation. It may
 * optionally include tool call information for assistant messages.
 */
@Data
public class ChatMessage {
    private ChatRole role;
    private String content;

    // For tool messages
    private String name;

    @JsonProperty("tool_call_id")
    private String toolCallId;

    // For assistant messages when requesting tool calls
    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;
}
