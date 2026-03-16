package com.example.agent.domain.model.chat;

/**
 * ToolCall represents a call to an external tool requested by the
 * assistant. The id uniquely identifies the call, and the function
 * holds the name and arguments for the tool invocation.
 */
import lombok.Data;

@Data
public class ToolCall {
    private String id;
    private ToolFunctionCall function;
}
