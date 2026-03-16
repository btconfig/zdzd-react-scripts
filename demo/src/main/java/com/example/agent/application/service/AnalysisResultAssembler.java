package com.example.agent.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalysisResultAssembler {
    private final ObjectMapper objectMapper;

    public AnalysisResultAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> assemble(String rawOutput, int topN) {
        Map<String, Object> parsed = parseJsonObject(rawOutput);
        List<String> keywords = normalizeStringList(parsed.get("keywords"), topN);
        List<Map<String, Object>> topics = normalizeTopicList(parsed.get("topics"), topN);
        List<Map<String, Object>> evidenceCases = normalizeTopicList(parsed.get("evidence_cases"), Math.min(topN, 10));
        String findingsSummary = stringValue(parsed.get("findings_summary"));
        if ((findingsSummary == null || findingsSummary.trim().isEmpty()) && !topics.isEmpty()) {
            findingsSummary = topics.get(0).get("topic") + " 等问题是当前客服会话中的主要关注点。";
        }

        Map<String, Object> structured = new LinkedHashMap<String, Object>();
        structured.put("keywords", keywords);
        structured.put("topics", topics);
        structured.put("findings_summary", findingsSummary);
        structured.put("evidence_cases", evidenceCases);
        return structured;
    }

    private Map<String, Object> parseJsonObject(String rawOutput) {
        if (rawOutput == null || rawOutput.trim().isEmpty()) {
            throw new IllegalArgumentException("analysis LLM output is empty");
        }
        String candidate = rawOutput.trim();
        if (candidate.startsWith("```")) {
            candidate = candidate.replaceFirst("^```json", "").replaceFirst("^```", "");
            candidate = candidate.replaceFirst("```\\s*$", "").trim();
        }
        try {
            return objectMapper.readValue(candidate, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to parse analysis JSON output", e);
        }
    }

    private List<String> normalizeStringList(Object raw, int topN) {
        List<String> result = new ArrayList<String>();
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (item != null && !String.valueOf(item).trim().isEmpty()) {
                    result.add(String.valueOf(item).trim());
                }
                if (result.size() >= topN) {
                    break;
                }
            }
        }
        return result;
    }

    private List<Map<String, Object>> normalizeTopicList(Object raw, int topN) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (!(raw instanceof List)) {
            return result;
        }
        for (Object item : (List<?>) raw) {
            if (item instanceof Map) {
                Map<String, Object> map = objectMapper.convertValue(item, new TypeReference<Map<String, Object>>() {});
                if (!map.isEmpty()) {
                    result.add(map);
                }
            } else if (item != null) {
                Map<String, Object> topic = new LinkedHashMap<String, Object>();
                topic.put("topic", String.valueOf(item));
                result.add(topic);
            }
            if (result.size() >= topN) {
                break;
            }
        }
        return result;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
