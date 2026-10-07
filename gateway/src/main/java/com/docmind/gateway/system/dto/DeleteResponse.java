package com.docmind.gateway.system.dto;

import lombok.Data;

@Data
public class DeleteResponse {
    private String docId;
    private Boolean deleted;
}
