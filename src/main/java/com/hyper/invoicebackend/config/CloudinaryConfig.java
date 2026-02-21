package com.hyper.invoicebackend.config;

import com.cloudinary.Cloudinary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class CloudinaryConfig {

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Value("${cloudinary.api-key}")
    private String apiKey;

    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    @Bean
    public Cloudinary cloudinary() {
        log.info("[CloudinaryConfig] Initializing Cloudinary bean | cloud-name: {}, api-key: {}***",
                cloudName, apiKey != null && apiKey.length() > 4 ? apiKey.substring(0, 4) : "****");
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", cloudName);
        config.put("api_key", apiKey);
        config.put("api_secret", apiSecret);
        config.put("secure", "true");
        Cloudinary cloudinary = new Cloudinary(config);
        log.info("[CloudinaryConfig] Cloudinary bean initialized successfully | secure: true");
        return cloudinary;
    }
}

