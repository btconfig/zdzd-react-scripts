package com.example.agent.infrastructure.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.Map;

public interface AnalysisJobMapper {
    int insert(Map<String, Object> params);

    int updateStatus(Map<String, Object> params);

    Map<String, Object> findById(@Param("analysisJobId") String analysisJobId);

    int upsertResult(Map<String, Object> params);

    Map<String, Object> findResultByJobId(@Param("analysisJobId") String analysisJobId);
}
