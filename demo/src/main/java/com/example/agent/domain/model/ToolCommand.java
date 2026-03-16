package com.example.agent.domain.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一工具命令。
 */
@Data
public class ToolCommand {
    private String toolName;
    private Map<String, Object> args = new HashMap<>();
    private String planInstanceId;
    private String turnId;
    private String nodeId;
    private String idempotencyKey;
}
