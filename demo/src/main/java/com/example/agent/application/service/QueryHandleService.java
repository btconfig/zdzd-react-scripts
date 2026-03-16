package com.example.agent.application.service;

import com.example.agent.domain.model.CustomerSessionRecord;
import com.example.agent.infrastructure.repository.QueryInstanceRepository;
import com.example.agent.infrastructure.repository.CustomerSessionMysqlRepository;
import com.example.agent.interfaces.rest.dto.CustomerSessionQueryRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class QueryHandleService {
    private final CustomerSessionMysqlRepository customerSessionMysqlRepository;
    private final QueryInstanceRepository queryInstanceRepository;
    private final ObjectMapper objectMapper;
    private final int maxResultRows;

    public QueryHandleService(CustomerSessionMysqlRepository customerSessionMysqlRepository,
                              QueryInstanceRepository queryInstanceRepository,
                              ObjectMapper objectMapper,
                              @Value("${agent.analysis.max-query-result-rows:10000}") int maxResultRows) {
        this.customerSessionMysqlRepository = customerSessionMysqlRepository;
        this.queryInstanceRepository = queryInstanceRepository;
        this.objectMapper = objectMapper;
        this.maxResultRows = maxResultRows;
    }

    public Map<String, Object> createHandle(Map<String, Object> request) {
        String queryInstanceId = "query-" + UUID.randomUUID();
        CustomerSessionQueryRequest queryRequest = toQueryRequest(request);
        List<CustomerSessionRecord> records = customerSessionMysqlRepository.queryForHandle(queryRequest, maxResultRows);
        queryInstanceRepository.insert(
                queryInstanceId,
                stringValue(request.get("scope")),
                writeJson(queryRequest),
                writeJson(request.get("filters")),
                "READY",
                records.size(),
                parseStart(queryRequest.getStartTime()),
                parseEnd(queryRequest.getEndTime())
        );
        queryInstanceRepository.replaceResultItems(queryInstanceId, records);

        Map<String, Object> artifacts = new LinkedHashMap<String, Object>();
        artifacts.put("query_instance_id", queryInstanceId);
        artifacts.put("scope", request.get("scope"));
        artifacts.put("start_date", request.get("start_date"));
        artifacts.put("end_date", request.get("end_date"));
        artifacts.put("time_range", request.get("start_date") + " ~ " + request.get("end_date"));
        artifacts.put("query_total", records.size());
        return artifacts;
    }

    public Map<String, Object> findQueryInstance(String queryInstanceId) {
        return queryInstanceRepository.findById(queryInstanceId);
    }

    private CustomerSessionQueryRequest toQueryRequest(Map<String, Object> request) {
        CustomerSessionQueryRequest queryRequest = new CustomerSessionQueryRequest();
        Map<String, Object> filters = request.get("filters") instanceof Map
                ? new LinkedHashMap<String, Object>((Map<String, Object>) request.get("filters"))
                : new LinkedHashMap<String, Object>();
        if (filters.get("keywords") instanceof List) {
            queryRequest.setKeywords(new ArrayList<String>((List<String>) filters.get("keywords")));
        }
        queryRequest.setBusinessLine(stringValue(filters.get("business_line")));
        queryRequest.setChannel(stringValue(filters.get("channel")));
        queryRequest.setCustomerId(stringValue(filters.get("customer_id")));
        if (queryRequest.getBusinessLine() == null || queryRequest.getBusinessLine().trim().isEmpty()) {
            queryRequest.setBusinessLine(normalizeScope(stringValue(request.get("scope"))));
        }
        queryRequest.setStartTime(asStartTime(stringValue(request.get("start_date"))));
        queryRequest.setEndTime(asEndTime(stringValue(request.get("end_date"))));
        queryRequest.setLimit(maxResultRows);
        return queryRequest;
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.trim().isEmpty()) {
            return null;
        }
        String normalized = scope.trim();
        if ("all".equalsIgnoreCase(normalized) || "*".equals(normalized)) {
            return null;
        }
        if ("finance-demo".equalsIgnoreCase(scope)) {
            return "finance";
        }
        if ("precious-metal".equalsIgnoreCase(scope)) {
            return "finance";
        }
        if ("finance-support".equalsIgnoreCase(scope)) {
            return "finance";
        }
        return normalized;
    }

    private String asStartTime(String date) {
        if (date == null || date.trim().isEmpty()) {
            return null;
        }
        return date.trim() + " 00:00:00";
    }

    private String asEndTime(String date) {
        if (date == null || date.trim().isEmpty()) {
            return null;
        }
        return date.trim() + " 23:59:59";
    }

    private LocalDateTime parseStart(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return LocalDate.parse(value.substring(0, 10)).atStartOfDay();
    }

    private LocalDateTime parseEnd(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return LocalDate.parse(value.substring(0, 10)).atTime(LocalTime.MAX.withNano(0));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? new LinkedHashMap<String, Object>() : value);
        } catch (Exception e) {
            throw new RuntimeException("failed to serialize query handle payload", e);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
