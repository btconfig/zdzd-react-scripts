package com.example.agent.domain.model.chat;

/**
 * ToolFunctionCall captures the name of the tool to invoke and the
 * arguments to pass. The arguments should be encoded as a JSON
 * string when returned by the large language model and parsed into
 * a structured Map by the orchestrator.
 */
import lombok.Data;

@Data
public class ToolFunctionCall {
    private String name;
    private String arguments;
}
