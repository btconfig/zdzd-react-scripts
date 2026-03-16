package com.example.agent.infrastructure.tool;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * SampleSessionItem holds a subset of session_table fields when
 * sampling conversations. It is used to return sample results from
 * the SessionRepository.
 */
@Data
public class SampleSessionItem {
    private String sessionId;
    private LocalDateTime sessionDate;
    private String content;
}
