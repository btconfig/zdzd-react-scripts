package com.example.agent.infrastructure.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface QueryInstanceMapper {
    int insert(Map<String, Object> params);

    int updateStatus(Map<String, Object> params);

    Map<String, Object> findById(@Param("queryInstanceId") String queryInstanceId);

    int deleteResultItems(@Param("queryInstanceId") String queryInstanceId);

    int insertResultItems(@Param("items") List<Map<String, Object>> items);

    List<Map<String, Object>> findResultItems(@Param("queryInstanceId") String queryInstanceId,
                                              @Param("limit") int limit);
}
