package com.example.agent.infrastructure.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface SkillDefinitionMapper {
    List<Map<String, Object>> findAll();

    Map<String, Object> findById(@Param("skillId") String skillId);

    int insert(Map<String, Object> params);

    int updateBySkillId(Map<String, Object> params);
}
