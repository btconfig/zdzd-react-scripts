package com.example.agent.application.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolArgumentResolverRegistry {
    private final Map<String, ToolArgumentResolver> resolvers = new LinkedHashMap<String, ToolArgumentResolver>();

    public ToolArgumentResolverRegistry(List<ToolArgumentResolver> resolvers) {
        for (ToolArgumentResolver resolver : resolvers) {
            this.resolvers.put(resolver.key(), resolver);
        }
    }

    public ToolArgumentResolver get(String key) {
        ToolArgumentResolver resolver = resolvers.get(key);
        if (resolver != null) {
            return resolver;
        }
        return resolvers.get("NONE");
    }
}
