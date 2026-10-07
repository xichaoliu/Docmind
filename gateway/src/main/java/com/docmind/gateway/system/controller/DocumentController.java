package com.docmind.gateway.system.controller;

import com.docmind.gateway.common.result.Result;
import com.docmind.gateway.common.security.CurrentUser;
import com.docmind.gateway.config.property.FileUploadProperties;
import com.docmind.gateway.system.dto.PythonDocRequest;
import com.docmind.gateway.system.entity.Document;
import com.docmind.gateway.system.service.DocumentService;
import com.docmind.gateway.system.service.FileStorageService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author xichaoliu
 * @since 2026-09-24
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {
    private final DocumentService documentService;
    private final FileStorageService fileStorageService;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt", "md");

    @PostMapping("/upload")
    public Result<Document> upload(@RequestParam("file") MultipartFile file) throws IOException {
        validateFile(file);
        String docId = java.util.UUID.randomUUID().toString();
        Path savedPath = fileStorageService.save(file, docId);

        Document doc = documentService.createRecord(docId, file.getOriginalFilename(), savedPath.toString());
        PythonDocRequest docRequest = new PythonDocRequest();
        docRequest.setDocId(docId);
        docRequest.setFilePath(savedPath.toString());
        docRequest.setUserId(CurrentUser.require());
        documentService.ingestAsync(docRequest);

        return Result.success(doc);
    }

    /** 列表 */
    @GetMapping("/list")
    public Result<List<Document>> list() {
        return Result.success(documentService.listAll());
    }

    /** 详情  */
    @GetMapping("/{id}")
    public Result<Document> getById(@PathVariable String id) {
        return Result.success(documentService.getById(id));
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) throws IOException {
        documentService.deleteDocument(id);
        return Result.success();
    }


    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.contains(".")) {
            throw new IllegalArgumentException("文件缺少扩展名");
        }
        String filename = file.getOriginalFilename();
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("不支持的文件类型：" + ext);
        }
    }
}
