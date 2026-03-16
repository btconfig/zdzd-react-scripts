package com.example.agent.application.service;

import com.example.agent.domain.model.AgentExecutedStep;
import com.example.agent.domain.model.AgentSession;
import com.example.agent.domain.model.ReportBlockBlueprint;
import com.example.agent.domain.model.ReportBlueprint;
import com.example.agent.domain.model.ReportSectionBlueprint;
import com.example.agent.domain.model.ReportSourceRef;
import com.example.agent.domain.model.ToolDefinition;
import com.example.agent.domain.model.ToolValidationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ReportToolArgumentResolver implements ToolArgumentResolver {
    private final ObjectMapper objectMapper;

    public ReportToolArgumentResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String key() {
        return "REPORT_BLUEPRINT";
    }

    @Override
    public Map<String, Object> resolve(AgentSession session, ToolDefinition definition, Map<String, Object> args) {
        validateReportPlanning(args);
        Map<String, Object> executionArgs = new LinkedHashMap<String, Object>(args);
        executionArgs.put("resolved_report_inputs", resolveReportInputs(session, args));
        return executionArgs;
    }

    private void validateReportPlanning(Map<String, Object> args) {
        if (!(args.get("report_blueprint") instanceof Map)) {
            throw validation(
                    "REPORT_BLUEPRINT_MISSING",
                    "build_keyword_report validation failed: missing report_blueprint.",
                    "Rebuild the report call with a structured blueprint containing goal, summary_style, sections, and conclusion_rules."
            );
        }
        if (!(args.get("report_inputs") instanceof Map)) {
            throw validation(
                    "REPORT_INPUTS_MISSING",
                    "build_keyword_report validation failed: missing report_inputs.",
                    "Provide concrete report_inputs that map report fields to prior tool outputs."
            );
        }
        ReportBlueprint blueprint;
        try {
            blueprint = objectMapper.convertValue(args.get("report_blueprint"), ReportBlueprint.class);
        } catch (IllegalArgumentException e) {
            throw validation(
                    "REPORT_BLUEPRINT_INVALID_SHAPE",
                    "build_keyword_report validation failed: report_blueprint contains fields with invalid types.",
                    "Use strings for goal/summary_style/conclusion_rules, arrays only where the skill explicitly allows them, and provide sections as an array of section objects."
            );
        }
        if (blueprint.getSections() == null || blueprint.getSections().isEmpty()) {
            throw validation(
                    "REPORT_SECTIONS_EMPTY",
                    "build_keyword_report validation failed: report_blueprint.sections must not be empty.",
                    "Add at least one report section with blocks and evidence."
            );
        }
        for (ReportSectionBlueprint section : blueprint.getSections()) {
            if (section == null || section.getBlocks() == null || section.getBlocks().isEmpty()) {
                throw validation(
                        "REPORT_SECTION_BLOCKS_EMPTY",
                        "build_keyword_report validation failed: each report section must contain blocks.",
                        "Rebuild the blueprint so every section includes at least one block."
                );
            }
            for (ReportBlockBlueprint block : section.getBlocks()) {
                if (block == null || block.getSourceRefs() == null || block.getSourceRefs().isEmpty()) {
                    throw validation(
                            "REPORT_BLOCK_SOURCE_REFS_MISSING",
                            "build_keyword_report validation failed: each report block must contain source_refs.",
                            "Use 'tool:artifacts.xxx' or 'tool:data.xxx' format."
                    );
                }
                for (ReportSourceRef sourceRef : block.getSourceRefs()) {
                    validateSourceRefFormat(sourceRef);
                }
            }
        }
    }

    private Map<String, Object> resolveReportInputs(AgentSession session, Map<String, Object> args) {
        Map<String, Object> reportInputs = objectMapper.convertValue(
                args.get("report_inputs"),
                new TypeReference<Map<String, Object>>() {}
        );
        Map<String, Object> resolved = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : reportInputs.entrySet()) {
            resolved.put(entry.getKey(), resolveReportInputValue(session, entry.getValue()));
        }
        ReportBlueprint blueprint;
        try {
            blueprint = objectMapper.convertValue(args.get("report_blueprint"), ReportBlueprint.class);
        } catch (IllegalArgumentException e) {
            throw validation(
                    "REPORT_BLUEPRINT_INVALID_SHAPE",
                    "build_keyword_report validation failed: report_blueprint contains fields with invalid types.",
                    "Use strings for goal/summary_style/conclusion_rules, arrays only where the skill explicitly allows them, and provide sections as an array of section objects."
            );
        }
        for (ReportSectionBlueprint section : blueprint.getSections()) {
            for (ReportBlockBlueprint block : section.getBlocks()) {
                for (ReportSourceRef sourceRef : block.getSourceRefs()) {
                    if (!resolved.containsKey(sourceRef.getLabel())) {
                        Object value = resolveSourceRef(session, sourceRef);
                        if (value == null && Boolean.TRUE.equals(sourceRef.getRequired())) {
                            throw validation(
                                    "REPORT_BLOCK_SOURCE_MISSING",
                                    "build_keyword_report validation failed: missing source for report block '" + sourceRef.getLabel() + "'.",
                                    "Fetch the required evidence first or correct the source ref path."
                            );
                        }
                        if (value != null) {
                            resolved.put(sourceRef.getLabel(), value);
                        }
                    }
                }
            }
        }
        return resolved;
    }

    private Object resolveReportInputValue(AgentSession session, Object value) {
        if (!(value instanceof Map)) {
            return value;
        }
        Map<String, Object> raw = objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {});
        if (!raw.containsKey("tool") || !raw.containsKey("field")) {
            return value;
        }
        ReportSourceRef sourceRef = new ReportSourceRef();
        sourceRef.setLabel(String.valueOf(raw.getOrDefault("label", raw.get("field"))));
        sourceRef.setTool(String.valueOf(raw.get("tool")));
        sourceRef.setField(String.valueOf(raw.get("field")));
        sourceRef.setPreferLatest(!Boolean.FALSE.equals(raw.get("prefer_latest")));
        sourceRef.setRequired(!Boolean.FALSE.equals(raw.get("required")));
        validateSourceRefFormat(sourceRef);
        Object resolved = resolveSourceRef(session, sourceRef);
        if (resolved == null && Boolean.TRUE.equals(sourceRef.getRequired())) {
            throw validation(
                    "REPORT_INPUT_SOURCE_MISSING",
                    "build_keyword_report validation failed: missing report_inputs source '" + sourceRef.getTool() + ":" + sourceRef.getField() + "'.",
                    "Correct the path or run the prerequisite tool first."
            );
        }
        return resolved;
    }

    private Object resolveSourceRef(AgentSession session, ReportSourceRef sourceRef) {
        List<AgentExecutedStep> candidates = new ArrayList<AgentExecutedStep>();
        for (AgentExecutedStep step : session.getExecutedSteps()) {
            if (step == null || "FAILED".equalsIgnoreCase(step.getStatus())) {
                continue;
            }
            if (sourceRef.getTool() != null && sourceRef.getTool().equals(step.getToolName())) {
                candidates.add(step);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        if (Boolean.FALSE.equals(sourceRef.getPreferLatest())) {
            return resolveFieldFromStep(candidates.get(0), sourceRef.getField());
        }
        for (int i = candidates.size() - 1; i >= 0; i--) {
            Object value = resolveFieldFromStep(candidates.get(i), sourceRef.getField());
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object resolveFieldFromStep(AgentExecutedStep step, String field) {
        if (field == null || field.trim().isEmpty()) {
            return null;
        }
        if (field.startsWith("artifacts.")) {
            return resolveNestedValue(step.getArtifacts(), field.substring("artifacts.".length()));
        }
        if (field.startsWith("data.")) {
            Object outputData = step.getOutputData();
            if (outputData instanceof Map) {
                return resolveNestedValue((Map<String, Object>) outputData, field.substring("data.".length()));
            }
            if ("data.records".equals(field) && outputData instanceof List) {
                return outputData;
            }
            if ("data".equals(field)) {
                return outputData;
            }
            return null;
        }
        if ("data".equals(field)) {
            return step.getOutputData();
        }
        if ("resultPayload".equals(field)) {
            return step.getResultPayload();
        }
        return resolveNestedValue(step.getResultPayload(), field);
    }

    private void validateSourceRefFormat(ReportSourceRef sourceRef) {
        if (sourceRef == null) {
            throw validation(
                    "REPORT_SOURCE_REF_NULL",
                    "build_keyword_report validation failed: source_ref is null.",
                    "Use 'tool:artifacts.xxx' or 'tool:data.xxx'."
            );
        }
        if (sourceRef.getTool() == null || sourceRef.getTool().trim().isEmpty()) {
            throw validation(
                    "REPORT_SOURCE_REF_TOOL_MISSING",
                    "build_keyword_report validation failed: source_ref '" + sourceRef.getLabel() + "' is missing tool name.",
                    "Use 'tool:artifacts.xxx' or 'tool:data.xxx'."
            );
        }
        if (sourceRef.getField() == null || sourceRef.getField().trim().isEmpty()) {
            throw validation(
                    "REPORT_SOURCE_REF_FIELD_MISSING",
                    "build_keyword_report validation failed: source_ref '" + sourceRef.getLabel() + "' is missing field path.",
                    "Use 'tool:artifacts.xxx' or 'tool:data.xxx'."
            );
        }
        if (!sourceRef.getField().startsWith("artifacts.") && !sourceRef.getField().startsWith("data.")) {
            throw validation(
                    "REPORT_SOURCE_REF_INVALID_FORMAT",
                    "build_keyword_report validation failed: source_ref '" + sourceRef.getLabel() + "' must use 'tool:artifacts.xxx' or 'tool:data.xxx'.",
                    "Rewrite the source ref using a concrete path such as 'query_customer_sessions_mysql:data.records' or 'analyze_keywords_async:artifacts.findings_summary'."
            );
        }
        if ("artifacts".equals(sourceRef.getField()) || "data".equals(sourceRef.getField())) {
            throw validation(
                    "REPORT_SOURCE_REF_TOO_BROAD",
                    "build_keyword_report validation failed: source_ref '" + sourceRef.getLabel() + "' is too broad.",
                    "Point to a concrete field like 'tool:artifacts.findings_summary' or 'tool:data.records'."
            );
        }
    }

    private ToolValidationException validation(String code, String message, String hint) {
        return new ToolValidationException(code, message, hint);
    }

    private Object resolveNestedValue(Map<String, Object> source, String path) {
        if (source == null || path == null || path.trim().isEmpty()) {
            return null;
        }
        Object current = source;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}
