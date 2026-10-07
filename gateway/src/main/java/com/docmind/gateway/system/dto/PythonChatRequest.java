package com.docmind.gateway.system.dto;

import lombok.Data;

import java.util.List;

// dto/PythonChatRequest.java
@Data
public class PythonChatRequest {
    private String question;
    private String userId;
    private List<HistoryItem> history;
    private List<String> docIds;
    public record HistoryItem(String role, String content) {}
}