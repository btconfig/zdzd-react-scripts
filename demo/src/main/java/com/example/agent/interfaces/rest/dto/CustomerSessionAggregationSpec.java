package com.example.agent.interfaces.rest.dto;

import lombok.Data;

@Data
public class CustomerSessionAggregationSpec {
    private String function;
    private String field;
    private String alias;
}
