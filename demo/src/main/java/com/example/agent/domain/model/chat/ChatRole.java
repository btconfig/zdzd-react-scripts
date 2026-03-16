package com.example.agent.domain.model.chat;

/**
 * Enumeration of chat roles exchanged between the planning agent and
 * large language model or tool outputs. The role indicates the
 * semantic meaning of the message in the conversation.
 */
public enum ChatRole {
    system,
    user,
    assistant,
    tool
}