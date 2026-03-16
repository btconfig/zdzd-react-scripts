package com.example.agent.infrastructure.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.Map;

public interface AsyncJobMapper {
    int insert(Map<String, Object> params);

    int updateStatusByExternalJobId(Map<String, Object> params);

    Map<String, Object> findByExternalJobId(@Param("externalJobId") String externalJobId);
}
