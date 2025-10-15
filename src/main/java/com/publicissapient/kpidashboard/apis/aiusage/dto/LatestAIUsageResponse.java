package com.publicissapient.kpidashboard.apis.aiusage.dto;

import java.util.List;

public record LatestAIUsageResponse(String email, List<AIUsageMetric> latestAIUsages) {
    public record AIUsageMetric(String key, Integer value, String timestamp) {
    }
}
