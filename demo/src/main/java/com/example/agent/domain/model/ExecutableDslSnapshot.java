package com.example.agent.domain.model;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class ExecutableDslSnapshot {
    private String dslId;
    private String sessionId;
    private String title;
    private String status;
    private String summary;
    private String outputJson;
    private List<ExecutableDslStep> steps = new ArrayList<ExecutableDslStep>();
    private Instant createdAt = Instant.now();
}
