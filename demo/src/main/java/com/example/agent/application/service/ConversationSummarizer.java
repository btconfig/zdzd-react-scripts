package com.example.agent.application.service;

import com.example.agent.domain.model.AgentExecutedStep;
import com.example.agent.domain.model.AgentSession;
import com.example.agent.domain.model.chat.ChatMessage;
import com.example.agent.domain.model.chat.ChatRole;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConversationSummarizer {
    public ChatMessage summarize(AgentSession session, List<ChatMessage> olderMessages) {
        List<String> lines = new ArrayList<String>();
        lines.add("Conversation summary so far:");
        int added = 0;
        if (olderMessages != null) {
            for (ChatMessage message : olderMessages) {
                if (message == null || message.getContent() == null || message.getContent().trim().isEmpty()) {
                    continue;
                }
                if (message.getRole() == ChatRole.user) {
                    lines.add("- User asked: " + truncate(message.getContent(), 160));
                    added++;
                } else if (message.getRole() == ChatRole.assistant && message.getToolCalls() == null) {
                    lines.add("- Assistant replied: " + truncate(message.getContent(), 160));
                    added++;
                } else if (message.getRole() == ChatRole.tool) {
                    lines.add("- Tool " + message.getName() + " returned compacted output.");
                    added++;
                }
                if (added >= 6) {
                    break;
                }
            }
        }
        if (session != null && session.getExecutedSteps() != null && !session.getExecutedSteps().isEmpty()) {
            lines.add("Executed steps:");
            int count = 0;
            for (AgentExecutedStep step : session.getExecutedSteps()) {
                if (step == null) {
                    continue;
                }
                lines.add("- " + step.getToolName() + ": " + truncate(step.getResultSummary(), 120));
                count++;
                if (count >= 6) {
                    break;
                }
            }
        }
        ChatMessage summary = new ChatMessage();
        summary.setRole(ChatRole.system);
        summary.setContent(String.join("\n", lines));
        return summary;
    }

    private String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars) + "...";
    }
}
