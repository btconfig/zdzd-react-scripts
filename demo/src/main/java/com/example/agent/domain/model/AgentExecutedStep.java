package com.example.agent.domain.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class AgentExecutedStep {
    private String turnId;
    private String stepId;
    private String toolCallId;
    private String toolName;
    private Map<String, Object> args = new HashMap<String, Object>();
    private ToolResultType resultType = ToolResultType.INLINE_DATA;
    private String asyncJobId;
    private String status;
    private String resultSummary;
    private Object outputData;
    private java.util.Map<String, Object> resultPayload = new java.util.HashMap<String, Object>();
    private Map<String, Object> artifacts = new HashMap<String, Object>();
}
