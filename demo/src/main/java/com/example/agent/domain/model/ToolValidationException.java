package com.example.agent.domain.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class ToolValidationException extends RuntimeException {
    private final String code;
    private final String hint;
    private final boolean retryable;
    private final Map<String, Object> details;

    public ToolValidationException(String code, String message, String hint) {
        this(code, message, hint, true, new LinkedHashMap<String, Object>());
    }

    public ToolValidationException(String code,
                                   String message,
                                   String hint,
                                   boolean retryable,
                                   Map<String, Object> details) {
        super(message);
        this.code = code;
        this.hint = hint;
        this.retryable = retryable;
        this.details = details == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(details);
    }

    public Map<String, Object> toPayload() {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("type", "VALIDATION");
        error.put("code", code);
        error.put("message", getMessage());
        error.put("hint", hint);
        error.put("retryable", retryable);
        error.put("details", details);

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("status", "FAILED");
        payload.put("error", error);
        return payload;
    }
}
