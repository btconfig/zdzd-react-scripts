package com.example.agent.application.service;

import com.example.agent.domain.model.CustomerSessionRecord;
import com.example.agent.infrastructure.repository.CustomerSessionMysqlRepository;
import com.example.agent.interfaces.rest.dto.CustomerSessionQueryRequest;
import com.example.agent.interfaces.rest.dto.CustomerSessionQueryResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CustomerSessionQueryService {
    private final CustomerSessionMysqlRepository repository;

    public CustomerSessionQueryService(CustomerSessionMysqlRepository repository) {
        this.repository = repository;
    }

    public CustomerSessionQueryResponse query(CustomerSessionQueryRequest request) {
        CustomerSessionQueryResponse response = new CustomerSessionQueryResponse();
        if (request.getAggregations() != null && !request.getAggregations().isEmpty()) {
            List<Map<String, Object>> aggregateRows = repository.queryAggregates(request);
            response.setMode("AGGREGATE");
            response.setTotal(aggregateRows.size());
            response.setAggregateRows(aggregateRows);
            return response;
        }
        List<CustomerSessionRecord> records = repository.query(request);
        response.setMode("DETAIL");
        response.setTotal(records.size());
        response.setRecords(records);
        return response;
    }
}
