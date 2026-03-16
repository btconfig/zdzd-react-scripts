package com.example.agent.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Data;

@Data
public class ReportSourceRef {
    private String label;
    private String tool;
    private String field;
    @JsonAlias("required")
    private Boolean required = Boolean.TRUE;
    @JsonAlias("prefer_latest")
    private Boolean preferLatest = Boolean.TRUE;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ReportSourceRef fromString(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        ReportSourceRef ref = new ReportSourceRef();
        int splitIndex = value.indexOf(':');
        if (splitIndex <= 0 || splitIndex >= value.length() - 1) {
            ref.setLabel(value);
            ref.setField(value);
            return ref;
        }
        ref.setTool(value.substring(0, splitIndex).trim());
        ref.setField(value.substring(splitIndex + 1).trim());
        ref.setLabel(value);
        return ref;
    }
}
