package com.docmind.gateway.config;

import com.docmind.gateway.config.property.PythonServiceProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;

import java.time.Duration;

// config/WebClientConfig.java
@Configuration
public class WebClientConfig {
    @Autowired
    private PythonServiceProperties properties;
    @Bean
    public WebClient pythonServiceClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMinutes(properties.getTimeoutMinutes()));

        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}