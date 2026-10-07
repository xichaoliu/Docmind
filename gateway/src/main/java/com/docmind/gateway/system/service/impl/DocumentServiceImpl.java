package com.docmind.gateway.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.docmind.gateway.common.exception.BizException;
import com.docmind.gateway.common.security.CurrentUser;
import com.docmind.gateway.config.property.FileUploadProperties;
import com.docmind.gateway.system.client.PythonDocServiceClient;
import com.docmind.gateway.system.dto.DeleteResponse;
import com.docmind.gateway.system.dto.IngestResponse;
import com.docmind.gateway.system.dto.PythonDocRequest;
import com.docmind.gateway.system.entity.Document;
import com.docmind.gateway.system.mapper.DocumentMapper;
import com.docmind.gateway.system.service.DocumentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.docmind.gateway.system.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author xichaoliu
 * @since 2026-09-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {
    private final FileUploadProperties appProperties;
    private final PythonDocServiceClient pythonDocServiceClient;
    private final FileStorageService fileStorageService;
    @Override
    public Document createRecord(String docId,String fileName, String filePath) {
        Document doc = new Document();
        doc.setDocId(docId);
        doc.setUserId(CurrentUser.require());
        doc.setFileName(fileName);
        doc.setStatus("PROCESSING");
        doc.setCreatedAt(LocalDateTime.now());
        this.save(doc);
        return doc;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(String id) throws IOException {
        Document doc = getById(id);
        if (doc == null || !doc.getUserId().equals(CurrentUser.require())) {
            throw new BizException(401, "无权操作该文档");
        }

        String docId = doc.getDocId();
        // 1. 先删向量库（最可能失败，放最前）
        try {
            pythonDocServiceClient.deleteIndex(docId);
        } catch (Exception e) {
            log.error("删除向量索引失败，中止删除: docId={}", docId, e);
            throw new IllegalStateException("删除向量索引失败，请稍后重试: " + e.getMessage(), e);
        }

        // 2. 删本地文件
        fileStorageService.delete(docId);
        // 3. 删除记录（真删）
        removeById(id);
        log.info("删除成功 id={}, docId={}", id, doc.getDocId());
    }

    @Override
    public List<Document> listAll() {
        return this.list(Wrappers.<Document>lambdaQuery()
                        .eq(Document::getUserId, CurrentUser.require())
                        .orderByDesc(Document::getCreatedAt));
    }

    // DocumentServiceImpl
    @Async
    public void ingestAsync(PythonDocRequest pythonDocRequest) {
        try {
            pythonDocServiceClient.ingest(pythonDocRequest); // 给足超时，大文件解析慢
            updateStatus(pythonDocRequest.getDocId(), "READY");
        } catch (Exception e) {
            log.error("入库失败：docId={}", pythonDocRequest.getDocId(), e);
            updateStatus(pythonDocRequest.getDocId(), "FAILED");
        }
    }


    public void updateStatus(String docId, String status) {
        boolean ok = update(new LambdaUpdateWrapper<Document>()
                .eq(Document::getDocId, docId)
                .set(Document::getStatus, status));
        if (!ok) {
            throw new IllegalArgumentException("文档不存在: " + docId);
        }
    }
}
