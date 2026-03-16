package com.example.agent.interfaces.rest.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CustomerSessionQueryRequest {
    private List<String> keywords = new ArrayList<String>();
    private String businessLine;
    private String channel;
    private String customerId;
    private String startTime;
    private String endTime;
    private Integer limit = 20;
    private List<String> groupBy = new ArrayList<String>();
    private List<CustomerSessionAggregationSpec> aggregations = new ArrayList<CustomerSessionAggregationSpec>();
}
