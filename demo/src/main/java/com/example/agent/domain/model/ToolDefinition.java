package com.example.agent.domain.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ToolDefinition {
    private String toolId;
    private String name;
    private String description;
    private String endpoint;
    private Map<String, Object> inputSchema = new HashMap<String, Object>();
    private ToolResultType resultType = ToolResultType.INLINE_DATA;
    private boolean async;
    private int timeoutSeconds = 30;
    private RiskLevel riskLevel = RiskLevel.LOW;
    private TimeNormalizationMode timeNormalizationMode = TimeNormalizationMode.NONE;
    private String requestPreprocessor = "NONE";
    private String responseNormalizer = "STANDARD";
    private String handleArtifactKey;
    private String asyncJobArtifactKey;
}
