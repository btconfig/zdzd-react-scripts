package com.example.agent.application.service;

import com.example.agent.domain.model.SkillDefinition;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RuleBasedSkillSelector {

    public List<SkillDefinition> select(String query, List<SkillDefinition> allSkills) {
        return allSkills;
    }
}
