package com.example.agent.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;

@Data
public class ReportBlueprint {
    private String goal;
    private String summaryStyle;
    private String conclusionRules;
    private List<ReportSectionBlueprint> sections = new ArrayList<ReportSectionBlueprint>();

    @JsonSetter("goal")
    public void acceptGoal(Object value) {
        this.goal = normalizeText(value);
    }

    @JsonSetter("summary_style")
    @JsonAlias("summaryStyle")
    public void acceptSummaryStyle(Object value) {
        this.summaryStyle = normalizeText(value);
    }

    @JsonSetter("conclusion_rules")
    @JsonAlias("conclusionRules")
    public void acceptConclusionRules(Object value) {
        this.conclusionRules = normalizeText(value);
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Collection) {
            StringJoiner joiner = new StringJoiner("\n");
            for (Object item : (Collection<?>) value) {
                String normalized = normalizeText(item);
                if (normalized != null && !normalized.trim().isEmpty()) {
                    joiner.add(normalized);
                }
            }
            return joiner.toString();
        }
        return String.valueOf(value);
    }
}
