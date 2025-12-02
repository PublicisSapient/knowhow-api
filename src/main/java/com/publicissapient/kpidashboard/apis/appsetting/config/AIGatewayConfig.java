package com.publicissapient.kpidashboard.apis.appsetting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration properties for AI Gateway.
 */
@Component
@ConfigurationProperties(prefix = "ai-gateway-config")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AIGatewayConfig {

	private String audience;
	private String baseUrl;
	private String defaultAiProvider;
}