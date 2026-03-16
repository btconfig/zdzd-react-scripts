package com.example.agent.domain.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ExecutableDslStep {
    private String stepId;
    private String toolName;
    private Map<String, Object> argsTemplate = new HashMap<String, Object>();
    private List<String> dependsOn = new ArrayList<String>();
    private List<ExecutableDslInputBinding> inputBindings = new ArrayList<ExecutableDslInputBinding>();
    private List<String> outputSchema = new ArrayList<String>();
}
