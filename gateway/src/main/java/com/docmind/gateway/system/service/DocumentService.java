package com.docmind.gateway.system.service;

import com.docmind.gateway.system.dto.PythonDocRequest;
import com.docmind.gateway.system.entity.Document;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author xichaoliu
 * @since 2026-09-24
 */
public interface DocumentService extends IService<Document> {

    /** 上传文件：落盘 + 写库 */
    Document createRecord(String docId, String fileName, String filePath);

    /** 删除：删目录 + 删库 */
    void deleteDocument(String id) throws IOException;


    void ingestAsync(PythonDocRequest pythonDocRequest);
    /** 查询文档列表*/
    List<Document> listAll();

    void updateStatus(String docId, String status);
}
