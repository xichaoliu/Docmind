package com.docmind.gateway.system.service;

import com.docmind.gateway.config.property.FileUploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {
    private final FileUploadProperties appProperties;

    public Path save(MultipartFile file, String docId) {
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                throw new IllegalArgumentException("文件名不能为空");
            }
            String ext = getExtension(originalFilename);
            String storedFilename = docId + ext;             // 实际存储用的文件名，完全由系统生成
            Path dir = root().resolve(docId);
            Files.createDirectories(dir);
            Path target = dir.resolve(storedFilename);
            file.transferTo(target);
            log.info("文件落盘成功 路径={}", target);
            return target;
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败", e);
        }
    }

    public void delete(String docId) {
        Path dir = root().resolve(docId);
        try {
            if (Files.exists(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted(Comparator.reverseOrder())   // 先删文件再删目录
                            .forEach(p -> {
                                try {
                                    Files.delete(p);
                                } catch (IOException e) {
                                    log.warn("删除文件失败：{}", p, e);
                                }
                            });
                }
            }
        } catch (IOException e) {
            log.error("删除目录失败：docId={}", docId, e);
        }
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex >= 0 ? filename.substring(dotIndex) : "";
    }

    /** 统一在这里把配置的相对路径转成绝对路径 */
    private Path root() {
        return Paths.get(appProperties.getUploadDir()).toAbsolutePath().normalize();
    }

}
