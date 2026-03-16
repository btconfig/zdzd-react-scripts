package com.example.agent.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReportBlockBlueprint {
    @JsonAlias("block_type")
    private String blockType;
    private String instruction;
    @JsonAlias("source_refs")
    private List<ReportSourceRef> sourceRefs = new ArrayList<ReportSourceRef>();
}
