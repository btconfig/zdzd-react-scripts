package com.example.agent.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AgentDomainProfileService {
    private final String systemIdentityPrompt;
    private final String routerIdentityPrompt;

    public AgentDomainProfileService(
            @Value("${agent.domain.system-identity-prompt:You are a constrained analysis agent operating in a bounded tool environment.}") String systemIdentityPrompt,
            @Value("${agent.domain.router-identity-prompt:You are a skill router for a constrained tool-using analysis agent.}") String routerIdentityPrompt) {
        this.systemIdentityPrompt = systemIdentityPrompt;
        this.routerIdentityPrompt = routerIdentityPrompt;
    }

    public String systemIdentityPrompt() {
        return systemIdentityPrompt;
    }

    public String routerIdentityPrompt() {
        return routerIdentityPrompt;
    }
}
