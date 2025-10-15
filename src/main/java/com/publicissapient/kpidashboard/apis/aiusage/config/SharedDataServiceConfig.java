package com.publicissapient.kpidashboard.apis.aiusage.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class SharedDataServiceConfig {
    @Value("${shared.data.service.url}")
    String url;
    @Value("${shared.data.service.api-key.name}")
    String apiKeyName;
    @Value("${shared.data.service.api-key.value}")
    String apiKeyValue;
}
