package com.docmind.gateway.system.client;

import com.docmind.gateway.system.dto.DeleteResponse;
import com.docmind.gateway.system.dto.IngestResponse;
import com.docmind.gateway.system.dto.PythonDocRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PythonDocServiceClient {
    private final WebClient pythonServiceClient;

    // 文档切片入库
    public void ingest( PythonDocRequest pythonDocRequest) {
            IngestResponse resp = pythonServiceClient.post()
                    .uri("/internal/ingest")
                    .bodyValue(pythonDocRequest)
                    .retrieve()
                    .bodyToMono(IngestResponse.class)
                    .block(Duration.ofMinutes(5));   // 给足超时，大文件解析慢
            if (resp == null) {
                throw new IllegalStateException("Python 入库无响应: " + pythonDocRequest.getDocId());
            }
            log.info("入库完成：docId={}, chunks={}", pythonDocRequest.getDocId(), resp.getChunkCount());
    }

    /** 删除向量索引 */
    public void deleteIndex(String docId) {
        DeleteResponse resp = pythonServiceClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/documents/{docId}")
                        .build(docId))
                .retrieve()
                .bodyToMono(DeleteResponse.class)
                .block(Duration.ofSeconds(30));

        if (resp == null || !Boolean.TRUE.equals(resp.getDeleted())) {
            throw new IllegalStateException("Python 侧删除失败: " + docId);
        }
        log.info("向量索引删除成功: docId={}", docId);
    }
}
