package com.example.agent.domain.port;

import com.example.agent.domain.model.chat.ChatMessage;
import com.example.agent.domain.model.ToolDefinition;

import java.util.List;

/**
 * LlmClient defines a contract for interacting with a large language
 * model. Implementations may call an external service or provide
 * stubbed responses for testing. The client is used by the
 * agent runtime to request a model response given a chat history.
 */
public interface LlmClient {
    /**
     * Sends the accumulated chat history to the LLM and returns the
     * model's reply. When useTools is true the model may request
     * tool invocations via tool_calls field.
     *
     * @param messages the conversation so far
     * @param useTools whether the model may call tools
     * @return the next message from the model
     */
    ChatMessage chat(List<ChatMessage> messages, boolean useTools);

    default ChatMessage chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
        return chat(messages, tools != null && !tools.isEmpty());
    }
}
