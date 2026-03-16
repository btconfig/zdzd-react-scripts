package com.example.agent.infrastructure.repository;

import com.example.agent.infrastructure.mapper.AsyncJobMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
public class AsyncJobRepository {

    private final AsyncJobMapper mapper;

    public AsyncJobRepository(AsyncJobMapper mapper) {
        this.mapper = mapper;
    }

    public void insertPending(String planInstanceId, String nodeId, String externalJobId, String callbackTopic, String callbackUrl, String payloadJson) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("planInstanceId", planInstanceId);
        params.put("nodeId", nodeId);
        params.put("sessionId", null);
        params.put("turnId", null);
        params.put("toolCallId", null);
        params.put("externalJobId", externalJobId);
        params.put("status", "PENDING");
        params.put("callbackTopic", callbackTopic);
        params.put("callbackUrl", callbackUrl);
        params.put("payloadJson", payloadJson);
        mapper.insert(params);
    }

    public void insertPendingAgentJob(String sessionId,
                                      String turnId,
                                      String toolCallId,
                                      String externalJobId,
                                      String callbackTopic,
                                      String callbackUrl,
                                      String payloadJson) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("planInstanceId", sessionId);
        params.put("nodeId", toolCallId);
        params.put("sessionId", sessionId);
        params.put("turnId", turnId);
        params.put("toolCallId", toolCallId);
        params.put("externalJobId", externalJobId);
        params.put("status", "PENDING");
        params.put("callbackTopic", callbackTopic);
        params.put("callbackUrl", callbackUrl);
        params.put("payloadJson", payloadJson);
        mapper.insert(params);
    }

    public void updateStatusByExternalJobId(String externalJobId, String status, String payloadJson) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("externalJobId", externalJobId);
        params.put("status", status);
        params.put("payloadJson", payloadJson);
        mapper.updateStatusByExternalJobId(params);
    }

    public Map<String, Object> findByExternalJobId(String externalJobId) {
        return mapper.findByExternalJobId(externalJobId);
    }
}
