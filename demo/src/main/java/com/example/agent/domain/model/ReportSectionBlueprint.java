package com.example.agent.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReportSectionBlueprint {
    @JsonAlias("section_id")
    private String sectionId;
    private String title;
    private String purpose;
    private List<ReportBlockBlueprint> blocks = new ArrayList<ReportBlockBlueprint>();
}
