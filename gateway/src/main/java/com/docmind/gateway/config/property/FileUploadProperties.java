package com.docmind.gateway.config.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Data
@Component
@ConfigurationProperties(prefix = "app")
public class FileUploadProperties {

    /** 存储根目录，支持相对路径和绝对路径 */
    private String uploadDir;

}