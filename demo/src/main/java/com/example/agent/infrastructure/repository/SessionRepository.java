package com.example.agent.infrastructure.repository;

import com.example.agent.infrastructure.mapper.SessionMapper;
import com.example.agent.infrastructure.tool.SampleSessionItem;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SessionRepository provides query methods for the session_table. It
 * supports sampling records based on keywords and time range.
 */
@Repository
public class SessionRepository {
    private final SessionMapper mapper;

    public SessionRepository(SessionMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Samples sessions containing any of the provided keywords within
     * the specified date range. Matching is case-sensitive and
     * performed via LIKE. The result is limited to the provided
     * maximum number of rows.
     *
     * @param keywords list of keywords
     * @param startDate start date inclusive
     * @param endDate end date inclusive
     * @param limit maximum number of rows
     * @return list of sample session items
     */
    public List<SampleSessionItem> sampleSessionsByKeywords(List<String> keywords, LocalDate startDate, LocalDate endDate, int limit) {
        if (keywords == null || keywords.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("keywords", keywords);
        params.put("startDateTime", startDate.atStartOfDay());
        params.put("endDateTime", endDate.plusDays(1).atStartOfDay().minusNanos(1));
        params.put("limit", limit);
        return mapper.sampleSessionsByKeywords(params);
    }
}
