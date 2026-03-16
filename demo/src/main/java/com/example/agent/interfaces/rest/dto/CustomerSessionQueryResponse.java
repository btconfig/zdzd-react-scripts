package com.example.agent.interfaces.rest.dto;

import com.example.agent.domain.model.CustomerSessionRecord;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class CustomerSessionQueryResponse {
    private Integer total;
    private String mode = "DETAIL";
    private List<CustomerSessionRecord> records = new ArrayList<CustomerSessionRecord>();
    private List<Map<String, Object>> aggregateRows = new ArrayList<Map<String, Object>>();
}
