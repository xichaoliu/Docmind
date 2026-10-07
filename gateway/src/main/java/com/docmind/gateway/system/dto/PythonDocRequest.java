package com.docmind.gateway.system.dto;

import lombok.Data;

@Data
public class PythonDocRequest {
    private String docId;
    private String filePath;
    private String userId;
}
