package com.example.agent.domain.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一工具执行结果。
 */
@Data
public class ToolResult {
    private ToolResultType type = ToolResultType.INLINE_DATA;
    private Object inlineData;
    private String handleId;
    private String asyncJobId;
    private String callbackTopic;
    private String callbackUrl;
    private Map<String, Object> artifacts = new HashMap<>();
}
