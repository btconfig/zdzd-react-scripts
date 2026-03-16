package com.example.agent.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerSessionRecord {
    private Long id;
    private String teamName;
    private String myModelInfo;
    private String queueId;
    private String agentId;
    private String bizType;
    private String groupId;
    private String deviceIn;
    private String bizTypeName;
    private String custUId;
    private String callSwitchInfos;
    private Integer transferCount;
    private String callType;
    private String callerNo;
    private String modelInfo;
    private String orgCalleeNo;
    private String callId;
    private String calledNo;
    private Integer totalCallTime;
    private String agentName;
    private String diff;
    private LocalDateTime sessionTime;
    private String groupName;
    private String teamId;
    private String dataSource;
    private Integer callTime;
    private String localCallId;
    private String label;
    private Long logdate;
    private String staffText;
    private String customerText;
    private String allText;

    public String getSessionCode() {
        return callId != null ? callId : localCallId;
    }

    public void setSessionCode(String sessionCode) {
        this.callId = sessionCode;
    }

    public String getChannel() {
        return deviceIn;
    }

    public String getCustomerId() {
        return custUId;
    }

    public String getBusinessLine() {
        return bizTypeName != null ? bizTypeName : bizType;
    }

    public void setBusinessLine(String businessLine) {
        this.bizTypeName = businessLine;
    }

    public String getUserMessage() {
        return customerText;
    }

    public void setUserMessage(String userMessage) {
        this.customerText = userMessage;
    }

    public String getAgentReply() {
        return staffText;
    }

    public void setAgentReply(String agentReply) {
        this.staffText = agentReply;
    }

    public String getTags() {
        return label;
    }

    public void setTags(String tags) {
        this.label = tags;
    }

    public LocalDateTime getOccurredAt() {
        return sessionTime;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.sessionTime = occurredAt;
    }
}
