package com.docmind.gateway.system.entity;

public record Citation(String docId, Integer chunkIndex, String text, String userId) {
}