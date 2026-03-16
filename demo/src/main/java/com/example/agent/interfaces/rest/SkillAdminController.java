package com.example.agent.interfaces.rest;

import com.example.agent.application.service.SkillAdminService;
import com.example.agent.domain.model.SkillDefinition;
import com.example.agent.domain.model.ToolDefinition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class SkillAdminController {
    private final SkillAdminService skillAdminService;

    public SkillAdminController(SkillAdminService skillAdminService) {
        this.skillAdminService = skillAdminService;
    }

    @GetMapping("/skills")
    public ResponseEntity<List<SkillDefinition>> listSkills() {
        return ResponseEntity.ok(skillAdminService.listSkills());
    }

    @GetMapping("/tools")
    public ResponseEntity<List<ToolDefinition>> listTools() {
        return ResponseEntity.ok(skillAdminService.listTools());
    }

    @PostMapping("/skills")
    public ResponseEntity<SkillDefinition> createSkill(@RequestBody SkillUpsertRequest request) {
        return ResponseEntity.ok(save(request.getSkillId(), request));
    }

    @PutMapping("/skills/{skillId}")
    public ResponseEntity<SkillDefinition> updateSkill(@PathVariable String skillId, @RequestBody SkillUpsertRequest request) {
        return ResponseEntity.ok(save(skillId, request));
    }

    private SkillDefinition save(String skillId, SkillUpsertRequest request) {
        return skillAdminService.saveSkill(
                skillId,
                request.getName(),
                request.getDescription(),
                request.isEnabled(),
                request.getToolIds(),
                request.getPromptMarkdown()
        );
    }

    public static class SkillUpsertRequest {
        private String skillId;
        private String name;
        private String description;
        private boolean enabled = true;
        private List<String> toolIds;
        private String promptMarkdown;

        public String getSkillId() {
            return skillId;
        }

        public void setSkillId(String skillId) {
            this.skillId = skillId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getToolIds() {
            return toolIds;
        }

        public void setToolIds(List<String> toolIds) {
            this.toolIds = toolIds;
        }

        public String getPromptMarkdown() {
            return promptMarkdown;
        }

        public void setPromptMarkdown(String promptMarkdown) {
            this.promptMarkdown = promptMarkdown;
        }
    }
}
