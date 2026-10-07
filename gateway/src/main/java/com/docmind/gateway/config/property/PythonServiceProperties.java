// config/PythonServiceProperties.java
package com.docmind.gateway.config.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "python-service")
public class PythonServiceProperties {
    private String baseUrl;
    private int timeoutMinutes = 20;  // 默认值，yml 里没配置时用这个
}