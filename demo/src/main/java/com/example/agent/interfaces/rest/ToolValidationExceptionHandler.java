package com.example.agent.interfaces.rest;

import com.example.agent.domain.model.ToolValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ToolValidationExceptionHandler {
    @ExceptionHandler(ToolValidationException.class)
    public ResponseEntity<Map<String, Object>> handleToolValidation(ToolValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.toPayload());
    }
}
