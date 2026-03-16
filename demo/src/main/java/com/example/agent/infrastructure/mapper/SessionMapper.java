package com.example.agent.infrastructure.mapper;

import com.example.agent.infrastructure.tool.SampleSessionItem;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface SessionMapper {
    List<SampleSessionItem> sampleSessionsByKeywords(Map<String, Object> params);
}
