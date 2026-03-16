package com.example.agent.domain.port;

import com.example.agent.domain.model.ToolCommand;
import com.example.agent.domain.model.ToolResult;

import java.util.Map;

/**
 * ToolExecutor defines an abstraction for executing tools requested by
 * the planning agent. A tool takes a name and a map of arguments
 * and returns an arbitrary result (often JSON data). Implementations
 * may call remote services or perform local operations.
 */
public interface ToolExecutor {
    /**
     * 新协议：命令式调用，支持 mode/异步结果/handle。
     */
    ToolResult execute(ToolCommand command);

    /**
     * Executes the named tool with the provided arguments.
     *
     * @param toolName the name of the tool to invoke
     * @param args arguments to the tool, keyed by parameter name
     * @return the tool result, which will be serialized to JSON
     */
    default Object execute(String toolName, Map<String, Object> args) {
        return executeLegacy(toolName, args);
    }

    /**
     * 兼容旧接口：默认适配到新协议。
     */
    default Object executeLegacy(String toolName, Map<String, Object> args) {
        ToolCommand cmd = new ToolCommand();
        cmd.setToolName(toolName);
        cmd.setArgs(args);
        ToolResult result = execute(cmd);
        if (result == null) {
            return null;
        }
        if (result.getType() == null || result.getType().name().equals("INLINE_DATA")) {
            return result.getInlineData();
        }
        if (result.getType().name().equals("HANDLE")) {
            return result.getHandleId();
        }
        return result.getAsyncJobId();
    }
}
