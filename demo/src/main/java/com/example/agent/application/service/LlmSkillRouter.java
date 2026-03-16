package com.example.agent.application.service;

import com.example.agent.domain.model.SkillDefinition;
import com.example.agent.domain.model.chat.ChatMessage;
import com.example.agent.domain.model.chat.ChatRole;
import com.example.agent.domain.port.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmSkillRouter {
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final AgentDomainProfileService agentDomainProfileService;

    public LlmSkillRouter(LlmClient llmClient,
                          ObjectMapper objectMapper,
                          AgentDomainProfileService agentDomainProfileService,
                          @Value("${llm.routing.enabled:true}") boolean enabled) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.agentDomainProfileService = agentDomainProfileService;
        this.enabled = enabled;
    }

    public List<String> route(String query, Map<String, Object> globalContext, List<SkillDefinition> allSkills) {
        if (!enabled || query == null || query.trim().isEmpty() || allSkills == null || allSkills.isEmpty()) {
            return new ArrayList<String>();
        }
        List<ChatMessage> messages = new ArrayList<ChatMessage>();
        ChatMessage system = new ChatMessage();
        system.setRole(ChatRole.system);
        system.setContent(agentDomainProfileService.routerIdentityPrompt() + "\n"
                + "Select the minimum set of skillIds required for the user request.\n"
                + "Return JSON only with the shape: "
                + "{\"selectedSkillIds\":[\"skill-id\"],\"reason\":\"short reason\"}.\n"
                + "Use only skillIds from the provided catalog.\n"
                + "Prefer the minimum skill set that can complete the task while preserving evidence and output quality.");
        messages.add(system);

        ChatMessage user = new ChatMessage();
        user.setRole(ChatRole.user);
        user.setContent(buildUserPrompt(query, globalContext, allSkills));
        messages.add(user);

        ChatMessage reply = llmClient.chat(messages, false);
        return parseSkillIds(reply == null ? null : reply.getContent(), allSkills);
    }

    private String buildUserPrompt(String query, Map<String, Object> globalContext, List<SkillDefinition> allSkills) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("query", query);
        payload.put("globalContext", globalContext);
        List<Map<String, Object>> catalog = new ArrayList<Map<String, Object>>();
        for (SkillDefinition skill : allSkills) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("skillId", skill.getSkillId());
            item.put("description", skill.getDescription());
            item.put("toolIds", skill.getToolIds());
            item.put("skillMarkdown", skill.getPromptMarkdown());
            catalog.add(item);
        }
        payload.put("skills", catalog);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("failed to serialize skill router prompt", e);
        }
    }

    private List<String> parseSkillIds(String content, List<SkillDefinition> allSkills) {
        List<String> selected = new ArrayList<String>();
        if (content == null || content.trim().isEmpty()) {
            return selected;
        }
        try {
            JsonNode root = objectMapper.readTree(stripMarkdownFence(content));
            JsonNode skillIdsNode = root.isObject() ? root.path("selectedSkillIds") : root;
            if (!skillIdsNode.isArray()) {
                return selected;
            }
            for (JsonNode node : skillIdsNode) {
                String skillId = node.asText(null);
                if (skillId != null && isKnownSkill(skillId, allSkills) && !selected.contains(skillId)) {
                    selected.add(skillId);
                }
            }
            return selected;
        } catch (Exception e) {
            throw new RuntimeException("failed to parse LLM skill routing response: " + content, e);
        }
    }

    private boolean isKnownSkill(String skillId, List<SkillDefinition> allSkills) {
        for (SkillDefinition skill : allSkills) {
            if (skill != null && skillId.equals(skill.getSkillId())) {
                return true;
            }
        }
        return false;
    }

    private String stripMarkdownFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring("```json".length()).trim();
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring("```".length()).trim();
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }
        return trimmed;
    }
}
