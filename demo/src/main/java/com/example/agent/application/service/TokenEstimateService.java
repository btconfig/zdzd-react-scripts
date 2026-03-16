package com.example.agent.application.service;

import com.example.agent.domain.model.chat.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TokenEstimateService {
    public int estimateMessages(List<ChatMessage> messages) {
        int total = 0;
        if (messages == null) {
            return total;
        }
        for (ChatMessage message : messages) {
            if (message == null) {
                continue;
            }
            total += 8;
            total += estimateText(message.getContent());
        }
        return total;
    }

    public int estimateText(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / 2.5d);
    }
}
