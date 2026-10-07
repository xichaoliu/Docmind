// dto/ChatRequest.java
package com.docmind.gateway.system.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatRequest {
    private String conversationId;  // 可选：不传时表示新建会话
    private String question;        // 必填：用户问题
    private List<String> docIds;
}