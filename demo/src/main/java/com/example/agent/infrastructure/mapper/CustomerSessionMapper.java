package com.example.agent.infrastructure.mapper;

import com.example.agent.domain.model.CustomerSessionRecord;
import com.example.agent.interfaces.rest.dto.CustomerSessionQueryRequest;

import java.util.List;
import java.util.Map;

public interface CustomerSessionMapper {
    List<CustomerSessionRecord> query(CustomerSessionQueryRequest request);

    List<Map<String, Object>> queryAggregates(Map<String, Object> params);
}
