package com.example.agent.infrastructure.repository;

import com.example.agent.domain.model.CustomerSessionRecord;
import com.example.agent.infrastructure.mapper.QueryInstanceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class QueryInstanceRepository {
    private final QueryInstanceMapper mapper;
    private final ObjectMapper objectMapper;

    public QueryInstanceRepository(QueryInstanceMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public void insert(String queryInstanceId,
                       String scope,
                       String queryJson,
                       String filtersJson,
                       String status,
                       int totalCount,
                       java.time.LocalDateTime startTime,
                       java.time.LocalDateTime endTime) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("queryInstanceId", queryInstanceId);
        params.put("scope", scope);
        params.put("queryJson", queryJson);
        params.put("filtersJson", filtersJson);
        params.put("status", status);
        params.put("totalCount", totalCount);
        params.put("startTime", startTime);
        params.put("endTime", endTime);
        mapper.insert(params);
    }

    public void updateStatus(String queryInstanceId, String status, int totalCount) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("queryInstanceId", queryInstanceId);
        params.put("status", status);
        params.put("totalCount", totalCount);
        mapper.updateStatus(params);
    }

    public Map<String, Object> findById(String queryInstanceId) {
        return mapper.findById(queryInstanceId);
    }

    public void replaceResultItems(String queryInstanceId, List<CustomerSessionRecord> records) {
        mapper.deleteResultItems(queryInstanceId);
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (CustomerSessionRecord record : records) {
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("queryInstanceId", queryInstanceId);
            item.put("resultTime", record.getSessionTime());
            item.put("resultJson", writeJson(toResultPayload(record)));
            items.add(item);
        }
        mapper.insertResultItems(items);
    }

    public List<Map<String, Object>> findResultItems(String queryInstanceId, int limit) {
        List<Map<String, Object>> rows = mapper.findResultItems(queryInstanceId, limit);
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("result_time", row.get("result_time"));
            item.put("result", readJsonMap(row.get("result_json")));
            results.add(item);
        }
        return results;
    }

    private Map<String, Object> toResultPayload(CustomerSessionRecord record) {
        return objectMapper.convertValue(record, Map.class);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("failed to serialize query result item", e);
        }
    }

    private Map<String, Object> readJsonMap(Object raw) {
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            return new HashMap<String, Object>();
        }
        try {
            return objectMapper.readValue(String.valueOf(raw), Map.class);
        } catch (Exception e) {
            throw new RuntimeException("failed to deserialize query result item", e);
        }
    }
}
