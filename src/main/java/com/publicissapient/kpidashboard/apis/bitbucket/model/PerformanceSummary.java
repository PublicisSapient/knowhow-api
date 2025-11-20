package com.publicissapient.kpidashboard.apis.bitbucket.model;

import java.util.List;

public record PerformanceSummary(String label, List<MetricItem> value) {
}