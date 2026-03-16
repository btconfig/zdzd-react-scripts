package com.example.agent.application.service;

import com.example.agent.domain.model.RenderedReport;
import com.example.agent.domain.model.ReportBlockBlueprint;
import com.example.agent.domain.model.ReportBlueprint;
import com.example.agent.domain.model.ReportSectionBlueprint;
import com.example.agent.domain.model.ReportSourceRef;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Service
public class ReportAssemblyService {
    private final ObjectMapper objectMapper;

    public ReportAssemblyService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RenderedReport assemble(String reportTitle, Object rawBlueprint, Map<String, Object> resolvedInputs) {
        ReportBlueprint blueprint = convertBlueprint(rawBlueprint);
        Map<String, Object> safeInputs = resolvedInputs == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(resolvedInputs);

        RenderedReport rendered = new RenderedReport();
        rendered.setTitle(reportTitle);

        List<String> markdownSections = new ArrayList<String>();
        markdownSections.add("# " + valueOrDefault(reportTitle, "分析报告"));
        if (hasText(blueprint.getGoal())) {
            markdownSections.add(blueprint.getGoal());
        }

        String summary = renderSummary(blueprint, safeInputs);
        if (hasText(summary)) {
            markdownSections.add("## 执行摘要\n\n" + summary);
        }

        for (ReportSectionBlueprint section : blueprint.getSections()) {
            String sectionMarkdown = renderSection(section, safeInputs);
            if (hasText(sectionMarkdown)) {
                markdownSections.add(sectionMarkdown);
                rendered.getSections().add(sectionMarkdown);
            }
        }

        if (hasText(blueprint.getConclusionRules())) {
            markdownSections.add("## 结论与建议\n\n" + blueprint.getConclusionRules());
        }

        String markdown = String.join("\n\n", markdownSections).trim();
        rendered.setMarkdown(markdown);
        rendered.setSummary(summary);
        return rendered;
    }

    private ReportBlueprint convertBlueprint(Object rawBlueprint) {
        if (rawBlueprint == null) {
            throw new IllegalArgumentException("report_blueprint is required");
        }
        try {
            return objectMapper.convertValue(rawBlueprint, ReportBlueprint.class);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("report_blueprint contains fields with invalid types", e);
        }
    }

    private String renderSummary(ReportBlueprint blueprint, Map<String, Object> inputs) {
        StringJoiner joiner = new StringJoiner("\n");
        if (hasText(blueprint.getSummaryStyle())) {
            joiner.add("- 摘要要求: " + blueprint.getSummaryStyle());
        }
        Object findingsSummary = inputs.get("findings_summary");
        if (findingsSummary != null) {
            joiner.add("- 核心发现: " + flattenValue(findingsSummary));
        }
        Object timeRange = inputs.get("time_range");
        if (timeRange != null) {
            joiner.add("- 时间范围: " + flattenValue(timeRange));
        }
        Object queryTotal = inputs.get("query_total");
        if (queryTotal != null) {
            joiner.add("- 样本量: " + flattenValue(queryTotal));
        }
        return joiner.toString().trim();
    }

    private String renderSection(ReportSectionBlueprint section, Map<String, Object> inputs) {
        List<String> blocks = new ArrayList<String>();
        for (ReportBlockBlueprint block : section.getBlocks()) {
            String blockMarkdown = renderBlock(block, inputs);
            if (hasText(blockMarkdown)) {
                blocks.add(blockMarkdown);
            }
        }
        if (blocks.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("## ").append(valueOrDefault(section.getTitle(), section.getSectionId())).append("\n\n");
        if (hasText(section.getPurpose())) {
            builder.append(section.getPurpose()).append("\n\n");
        }
        builder.append(String.join("\n\n", blocks));
        return builder.toString().trim();
    }

    private String renderBlock(ReportBlockBlueprint block, Map<String, Object> inputs) {
        List<String> resolvedValues = new ArrayList<String>();
        for (ReportSourceRef sourceRef : block.getSourceRefs()) {
            Object value = inputs.get(sourceRef.getLabel());
            if (value != null) {
                resolvedValues.add(flattenValue(value));
            }
        }
        String instruction = valueOrDefault(block.getInstruction(), "");
        String blockType = valueOrDefault(block.getBlockType(), "paragraph");
        if ("bullet_list".equals(blockType)) {
            List<String> bullets = new ArrayList<String>();
            if (hasText(instruction)) {
                bullets.add("- " + instruction);
            }
            for (String value : resolvedValues) {
                for (String line : value.split("\\n")) {
                    if (hasText(line)) {
                        bullets.add("- " + line.trim());
                    }
                }
            }
            return String.join("\n", bullets).trim();
        }
        if ("evidence_cases".equals(blockType)) {
            StringBuilder builder = new StringBuilder();
            if (hasText(instruction)) {
                builder.append(instruction).append("\n\n");
            }
            for (String value : resolvedValues) {
                builder.append("- ").append(value.replace("\n", "\n  ")).append("\n");
            }
            return builder.toString().trim();
        }
        if ("table".equals(blockType)) {
            return renderTable(instruction, resolvedValues);
        }
        StringBuilder builder = new StringBuilder();
        if (hasText(instruction)) {
            builder.append(instruction);
        }
        if (!resolvedValues.isEmpty()) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(String.join("\n", resolvedValues));
        }
        return builder.toString().trim();
    }

    private String renderTable(String instruction, List<String> resolvedValues) {
        StringBuilder builder = new StringBuilder();
        if (hasText(instruction)) {
            builder.append(instruction).append("\n\n");
        }
        builder.append("| 要素 | 内容 |\n");
        builder.append("| --- | --- |\n");
        for (int i = 0; i < resolvedValues.size(); i++) {
            builder.append("| 输入").append(i + 1).append(" | ")
                    .append(resolvedValues.get(i).replace("\n", "<br/>"))
                    .append(" |\n");
        }
        return builder.toString().trim();
    }

    private String flattenValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Collection) {
            List<String> parts = new ArrayList<String>();
            for (Object item : (Collection<?>) value) {
                parts.add(flattenValue(item));
            }
            return String.join("\n", parts);
        }
        if (value instanceof Map) {
            Map<String, Object> map = objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {});
            if (map.containsKey("userMessage") || map.containsKey("agentReply")) {
                StringBuilder builder = new StringBuilder();
                if (map.get("userMessage") != null) {
                    builder.append("用户: ").append(map.get("userMessage"));
                }
                if (map.get("agentReply") != null) {
                    if (builder.length() > 0) {
                        builder.append("\n");
                    }
                    builder.append("客服: ").append(map.get("agentReply"));
                }
                return builder.toString();
            }
            return map.toString();
        }
        return String.valueOf(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String valueOrDefault(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }
}
