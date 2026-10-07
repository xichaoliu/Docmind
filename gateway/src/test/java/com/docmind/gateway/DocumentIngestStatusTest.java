package com.docmind.gateway;

import com.docmind.gateway.common.security.CurrentUser;
import com.docmind.gateway.system.client.PythonDocServiceClient;
import com.docmind.gateway.system.dto.PythonDocRequest;
import com.docmind.gateway.system.mapper.DocumentMapper;
import com.docmind.gateway.system.service.impl.DocumentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class DocumentIngestStatusTest {

    @Mock
    PythonDocServiceClient pythonClient;
    @Mock
    DocumentMapper documentMapper;
    @Spy
    @InjectMocks
    DocumentServiceImpl service;

    @Test
    void Python入库失败时_状态必须标记为FAILED() {
        // 构造请求...
        PythonDocRequest docRequest = new PythonDocRequest();
        docRequest.setDocId("doc1");
        docRequest.setFilePath("/path/to/file");
        docRequest.setUserId("user1");

        // 打桩：pythonClient.ingest 抛异常
        doThrow(new RuntimeException("连接超时"))
                .when(pythonClient).ingest(docRequest);

        // Mock 掉 updateStatus 的真实逻辑，避免触发 MyBatis-Plus
        doNothing().when(service).updateStatus(anyString(), anyString());

        service.ingestAsync(docRequest);

        verify(service).updateStatus("doc1", "FAILED");
        verify(service, never()).updateStatus("doc1", "READY"); // ← 关键断言
    }
}