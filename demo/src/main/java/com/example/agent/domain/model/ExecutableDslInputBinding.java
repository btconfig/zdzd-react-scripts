package com.example.agent.domain.model;

import lombok.Data;

@Data
public class ExecutableDslInputBinding {
    private String targetArg;
    private String fromStep;
    private String sourcePath;
}
