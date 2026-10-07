package com.docmind.gateway;

import com.docmind.gateway.common.security.AuthInterceptor;
import com.docmind.gateway.system.controller.DocumentController;
import com.docmind.gateway.system.entity.Document;
import com.docmind.gateway.system.service.DocumentService;
import com.docmind.gateway.system.service.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders

@WebMvcTest(DocumentController.class)
public class DocumentControllerTest {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;


    @MockitoBean
    FileStorageService fileStorageService;
    @MockitoBean
    DocumentService documentService;
    @MockitoBean
    AuthInterceptor authInterceptor;
    private Document sample;
    @BeforeEach
    void setUp() {
        // 拦截器打桩 放行
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);

        sample = new Document();
        sample.setId("doc1");
        sample.setFileName("测试.txt");
    }

    @Test
    void 查询成功返回200() throws Exception{
        /**
         * 此用例排查出两个问题
         * 1、"doc1" 转不成 Long, 发现路径参数类型controller里定以的是long，实际应该是string,与Document里主键类型保持一致
         * 2、返回值没有用自定以Result类包装
         */
        when(documentService.getById("doc1")).thenReturn(sample);

        mockMvc.perform(get("/api/documents/{id}", "doc1")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("doc1"))
                .andExpect(jsonPath("$.data.fileName").value("测试.txt"));

        verify(documentService).getById("doc1");
    }
}
