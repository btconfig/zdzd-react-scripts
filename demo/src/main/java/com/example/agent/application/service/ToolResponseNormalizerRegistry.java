package com.example.agent.application.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ToolResponseNormalizerRegistry {
    private final Map<String, ToolResponseNormalizer> normalizers = new HashMap<String, ToolResponseNormalizer>();

    public ToolResponseNormalizerRegistry(List<ToolResponseNormalizer> normalizers) {
        for (ToolResponseNormalizer normalizer : normalizers) {
            this.normalizers.put(normalizer.key(), normalizer);
        }
    }

    public ToolResponseNormalizer resolve(String key) {
        ToolResponseNormalizer normalizer = normalizers.get(key);
        if (normalizer != null) {
            return normalizer;
        }
        return normalizers.get("STANDARD");
    }
}
