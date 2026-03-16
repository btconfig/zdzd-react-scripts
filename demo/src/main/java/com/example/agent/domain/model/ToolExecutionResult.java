package com.example.agent.domain.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ToolExecutionResult {
    private String toolCallId;
    private String toolName;
    private Map<String, Object> args = new HashMap<String, Object>();
    private ToolResult toolResult;
    private boolean success;
    private boolean waitingAsync;
    private String error;
    private String summary;
    private Map<String, Object> normalizedPayload = new HashMap<String, Object>();
}
