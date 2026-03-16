package com.example.agent.application.service;

import com.example.agent.domain.model.AgentSession;
import com.example.agent.domain.model.ToolDefinition;
import com.example.agent.domain.model.chat.ChatMessage;
import com.example.agent.domain.model.chat.ChatRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ContextBudgetService {
    private final TokenEstimateService tokenEstimateService;
    private final ToolMessageCompactor toolMessageCompactor;
    private final ConversationSummarizer conversationSummarizer;
    private final int maxInputTokens;
    private final int reserveOutputTokens;
    private final int keepRecentTurns;

    public ContextBudgetService(TokenEstimateService tokenEstimateService,
                                ToolMessageCompactor toolMessageCompactor,
                                ConversationSummarizer conversationSummarizer,
                                @Value("${llm.context.max-input-tokens:24000}") int maxInputTokens,
                                @Value("${llm.context.reserve-output-tokens:4000}") int reserveOutputTokens,
                                @Value("${llm.context.keep-recent-turns:3}") int keepRecentTurns) {
        this.tokenEstimateService = tokenEstimateService;
        this.toolMessageCompactor = toolMessageCompactor;
        this.conversationSummarizer = conversationSummarizer;
        this.maxInputTokens = maxInputTokens;
        this.reserveOutputTokens = reserveOutputTokens;
        this.keepRecentTurns = keepRecentTurns;
    }

    public List<ChatMessage> prepareMessagesForLlm(AgentSession session, List<ToolDefinition> allowedTools) {
        List<ChatMessage> original = session == null || session.getTranscript() == null
                ? Collections.<ChatMessage>emptyList()
                : cloneMessages(session.getTranscript());
        int budget = Math.max(128, maxInputTokens - reserveOutputTokens);
        if (tokenEstimateService.estimateMessages(original) <= budget) {
            return original;
        }

        List<ChatMessage> compacted = compactToolMessages(original);
        if (tokenEstimateService.estimateMessages(compacted) <= budget) {
            return compacted;
        }

        List<ChatMessage> summarized = summarizeOlderMessages(session, compacted);
        if (tokenEstimateService.estimateMessages(summarized) <= budget) {
            return summarized;
        }

        return hardTrimToBudget(summarized, budget);
    }

    private List<ChatMessage> compactToolMessages(List<ChatMessage> messages) {
        List<ChatMessage> compacted = new ArrayList<ChatMessage>();
        for (ChatMessage message : messages) {
            compacted.add(toolMessageCompactor.compact(message));
        }
        return compacted;
    }

    private List<ChatMessage> summarizeOlderMessages(AgentSession session, List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return messages;
        }
        int startIndex = findRecentWindowStart(messages);
        if (startIndex <= 1) {
            return messages;
        }
        List<ChatMessage> result = new ArrayList<ChatMessage>();
        result.add(messages.get(0));
        List<ChatMessage> older = new ArrayList<ChatMessage>(messages.subList(1, startIndex));
        result.add(conversationSummarizer.summarize(session, older));
        result.addAll(messages.subList(startIndex, messages.size()));
        return result;
    }

    private int findRecentWindowStart(List<ChatMessage> messages) {
        int userTurnsSeen = 0;
        for (int i = messages.size() - 1; i >= 1; i--) {
            ChatMessage message = messages.get(i);
            if (message != null && message.getRole() == ChatRole.user) {
                userTurnsSeen++;
                if (userTurnsSeen >= keepRecentTurns) {
                    return i;
                }
            }
        }
        return 1;
    }

    private List<ChatMessage> hardTrimToBudget(List<ChatMessage> messages, int budget) {
        if (messages.isEmpty()) {
            return messages;
        }
        List<ChatMessage> result = new ArrayList<ChatMessage>();
        result.add(messages.get(0));
        int used = tokenEstimateService.estimateMessages(result);
        List<ChatMessage> tail = new ArrayList<ChatMessage>();
        for (int i = messages.size() - 1; i >= 1; i--) {
            ChatMessage message = messages.get(i);
            int estimate = tokenEstimateService.estimateMessages(Collections.singletonList(message));
            if (used + estimate > budget) {
                continue;
            }
            tail.add(0, message);
            used += estimate;
        }
        result.addAll(tail);
        return result;
    }

    private List<ChatMessage> cloneMessages(List<ChatMessage> source) {
        List<ChatMessage> copies = new ArrayList<ChatMessage>();
        for (ChatMessage message : source) {
            if (message == null) {
                continue;
            }
            ChatMessage copy = new ChatMessage();
            copy.setRole(message.getRole());
            copy.setContent(message.getContent());
            copy.setName(message.getName());
            copy.setToolCallId(message.getToolCallId());
            copy.setToolCalls(message.getToolCalls());
            copies.add(copy);
        }
        return copies;
    }
}
