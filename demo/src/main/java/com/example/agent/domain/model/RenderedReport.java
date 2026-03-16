package com.example.agent.domain.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RenderedReport {
    private String title;
    private String markdown;
    private String summary;
    private List<String> sections = new ArrayList<String>();
}
