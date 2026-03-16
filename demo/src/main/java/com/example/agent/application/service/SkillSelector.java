package com.example.agent.application.service;

import com.example.agent.domain.model.SkillDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SkillSelector {
    private final SkillRegistry skillRegistry;
    private final LlmSkillRouter llmSkillRouter;
    private final RuleBasedSkillSelector ruleBasedSkillSelector;

    public SkillSelector(SkillRegistry skillRegistry,
                         LlmSkillRouter llmSkillRouter,
                         RuleBasedSkillSelector ruleBasedSkillSelector) {
        this.skillRegistry = skillRegistry;
        this.llmSkillRouter = llmSkillRouter;
        this.ruleBasedSkillSelector = ruleBasedSkillSelector;
    }

    public List<SkillDefinition> select(String query, Map<String, Object> globalContext) {
        List<SkillDefinition> all = skillRegistry.allEnabled();
        if (query == null || query.trim().isEmpty()) {
            return all;
        }
        try {
            List<String> selectedIds = llmSkillRouter.route(query, globalContext, all);
            List<SkillDefinition> selected = new ArrayList<SkillDefinition>();
            for (String selectedId : selectedIds) {
                for (SkillDefinition skill : all) {
                    if (skill != null && selectedId.equals(skill.getSkillId())) {
                        selected.add(skill);
                        break;
                    }
                }
            }
            if (!selected.isEmpty()) {
                return selected;
            }
        } catch (RuntimeException ignore) {
            // Fallback to deterministic routing when the LLM router is unavailable or returns invalid output.
        }
        return ruleBasedSkillSelector.select(query, all);
    }
}
