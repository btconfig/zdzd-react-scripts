package com.example.agent.application.service;

import com.example.agent.domain.model.SkillDefinition;
import com.example.agent.domain.model.ToolDefinition;
import com.example.agent.domain.model.chat.ChatMessage;
import com.example.agent.domain.model.chat.ChatRole;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PromptAssembler {
    private final AgentTimeContextService agentTimeContextService;
    private final AgentDomainProfileService agentDomainProfileService;

    public PromptAssembler(AgentTimeContextService agentTimeContextService,
                           AgentDomainProfileService agentDomainProfileService) {
        this.agentTimeContextService = agentTimeContextService;
        this.agentDomainProfileService = agentDomainProfileService;
    }

    public List<ChatMessage> buildInitialTranscript(String userMessage,
                                                    Map<String, Object> globalContext,
                                                    List<SkillDefinition> skills,
                                                    List<ToolDefinition> tools) {
        List<ChatMessage> transcript = new ArrayList<ChatMessage>();
        ChatMessage system = new ChatMessage();
        system.setRole(ChatRole.system);
        system.setContent(systemPrompt(globalContext, skills, tools));
        transcript.add(system);
        ChatMessage user = new ChatMessage();
        user.setRole(ChatRole.user);
        user.setContent(userMessage);
        transcript.add(user);
        return transcript;
    }

    public String systemPrompt(Map<String, Object> globalContext, List<SkillDefinition> skills, List<ToolDefinition> tools) {
        Map<String, Object> trustedTimeContext = agentTimeContextService.buildTrustedTimeContext();
        StringBuilder builder = new StringBuilder();
        builder.append(agentDomainProfileService.systemIdentityPrompt()).append("\n");
        builder.append("Use only allowed tools. Prefer grounded evidence and concise answers.\n");
        builder.append("If tool results are enough, answer directly. If more information is required, continue with another tool call.\n");
        builder.append("Runtime metadata:\n");
        builder.append(String.valueOf(globalContext)).append("\n");
        builder.append("Trusted current time context:\n");
        builder.append(String.valueOf(trustedTimeContext)).append("\n");
        builder.append("Time handling rules:\n");
        builder.append("- For relative time requests like 最近30天/最近7天/本月/上个月, prefer relative expressions such as now-30d, now, today, this_month, last_month.\n");
        builder.append("- Do not invent old calendar dates when the user asked for a relative time window.\n");
        builder.append("- When calling MySQL session query tools, prefer the trusted current time context above.\n");
        builder.append("- For report-style deliverables, first plan sections and evidence sources; if evidence is insufficient, continue calling tools instead of drafting the report.\n");
        builder.append("Skills:\n");
        for (SkillDefinition skill : skills) {
            builder.append("### ").append(skill.getSkillId()).append("\n");
            builder.append(skill.getPromptMarkdown()).append("\n\n");
        }
        builder.append("Allowed tools:\n");
        for (ToolDefinition tool : tools) {
            builder.append("- ").append(tool.getToolId()).append(": ").append(tool.getDescription()).append("\n");
        }
        return builder.toString();
    }
}
