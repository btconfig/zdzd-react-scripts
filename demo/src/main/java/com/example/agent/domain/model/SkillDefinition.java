package com.example.agent.domain.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SkillDefinition {
    private String skillId;
    private String name;
    private String description;
    private boolean enabled = true;
    private String promptMarkdown;
    private List<String> toolIds = new ArrayList<String>();
}
