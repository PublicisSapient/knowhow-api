package com.publicissapient.kpidashboard.apis.aiusage.service;

import com.publicissapient.kpidashboard.apis.aiusage.config.SharedDataServiceConfig;
import com.publicissapient.kpidashboard.apis.aiusage.dto.LatestAIUsageResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
@AllArgsConstructor
public class SharedDataService {

    private static final String UPLOAD_PATH = "/ai-usage/latest/values";

    private final RestTemplate restTemplate;
    private final SharedDataServiceConfig sharedDataServiceConfig;

    public LatestAIUsageResponse getAiUsageStatus(String email) {
        String url = UriComponentsBuilder.fromHttpUrl(sharedDataServiceConfig.getUrl() + UPLOAD_PATH)
                .queryParam("email", email)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(sharedDataServiceConfig.getApiKeyName(), sharedDataServiceConfig.getApiKeyValue());

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<LatestAIUsageResponse> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, LatestAIUsageResponse.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                log.error("Failed to fetch AI usage status for email {}: HTTP code {}", email, response.getStatusCode());
                throw new RuntimeException("Failed to fetch AI usage values");
            }
        } catch (Exception e) {
            log.error("Error fetching AI usage status for email {}: {}", email, e.getMessage());
            return null;
        }
    }
}
