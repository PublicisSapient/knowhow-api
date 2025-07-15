/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.management.service.impl;

import com.publicissapient.kpidashboard.apis.management.model.DashboardConfiguration;
import com.publicissapient.kpidashboard.apis.management.configs.DashboardProperties;
import com.publicissapient.kpidashboard.apis.management.service.DashboardApiHealthChecker;
import com.publicissapient.kpidashboard.apis.management.service.DashboardHealthService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardHealthServiceImpl implements DashboardHealthService {

	private final MeterRegistry meterRegistry;
	private final HealthEndpoint healthEndpoint;
	private final DashboardProperties dashboardProperties;
	private final DashboardApiHealthChecker apiHealthChecker;

	@Override
	public Map<String, Object> healthDetails() {
		log.info("Starting to fetch overall health details for the application.");
		Map<String, Object> healthDetails = Map.of("appHealth", healthEndpoint.health(), "metrics", getAppMetrics(),
				"dashboardStatuses", getAllDashboardStatuses(), "dashboards", getDashboardLinks());
		log.info("Successfully fetched overall health details for the application.");
		return healthDetails;
	}

	@Override
	public Map<String, Object> dashboardHealth(String dashboardName) {
		log.info("Fetching health details for dashboard: {}", dashboardName);
		DashboardConfiguration dashboard = findDashboardByName(dashboardName);
		Map<String, Object> dashboardHealth = Map.of("dashboardName", dashboardName, "status",
				getDashboardStatus(dashboard), "apiMetrics", getApiMetrics(dashboard.getApis()));
		log.info("Successfully fetched health details for dashboard: {}", dashboardName);
		return dashboardHealth;
	}

	private DashboardConfiguration findDashboardByName(String dashboardName) {
		log.debug("Searching for dashboard with name: {}", dashboardName);
		return dashboardProperties.getDashboards().stream().filter(d -> d.getName().equalsIgnoreCase(dashboardName))
				.findFirst().orElseThrow(() -> {
					log.error("Dashboard not found: {}", dashboardName);
					return new IllegalArgumentException("Dashboard not found: " + dashboardName);
				});
	}

	// --- Delegate API Health Check to helper ---
	private Status getApiStatus(String api) {
		return apiHealthChecker.getApiStatus(api);
	}

	private Status getDashboardStatus(DashboardConfiguration dashboard) {
		log.debug("Calculating status for dashboard: {}", dashboard.getName());
		Status status = dashboard.getApis().stream().map(this::getApiStatus)
				.anyMatch(apiStatus -> apiStatus.equals(Status.DOWN)) ? Status.DOWN : Status.UP;
		log.debug("Status for dashboard {}: {}", dashboard.getName(), status);
		return status;
	}

	// --- Metric Retrieval Helpers ---
	// Fetch global metrics without filtering by URI.
	public Map<String, Object> getAppMetrics() {
		log.debug("Fetching application-level metrics.");
		Timer timer = meterRegistry.find("http.server.requests").timer();
		Map<String, Object> metrics = createMetricsMap(timer);
		log.debug("Application metrics: {}", metrics);
		return metrics;
	}

	/**
	 * Creates a metrics map from the provided Timer. This consolidates logic
	 * previously duplicated in buildMetricsMap and getApiMetricsForSingleApi.
	 */
	private Map<String, Object> createMetricsMap(Timer timer) {
		Map<String, Object> metrics = new HashMap<>();
		if (timer != null) {
			metrics.put(Statistic.COUNT.name().toLowerCase(), timer.count());
			metrics.put(Statistic.TOTAL_TIME.name().toLowerCase(), timer.totalTime(timer.baseTimeUnit()));
			metrics.put(Statistic.MAX.name().toLowerCase(), timer.max(timer.baseTimeUnit()));
		} else {
			log.warn("No metrics available for the application");
			metrics.put("message", "No metrics available");
		}
		return metrics;
	}

	// --- Fetch Metrics for All APIs ---
	private List<Map<String, Object>> getApiMetrics(List<String> apis) {
		return apis.stream().map(this::getApiMetricsForSingleApi).collect(Collectors.toList());
	}

	// --- Fetch Metrics for a Single API ---
	private Map<String, Object> getApiMetricsForSingleApi(String api) {
		log.debug("Fetching metrics for API: {}", api);
		Map<String, Object> apiMetrics = new HashMap<>();
		apiMetrics.put("uri", api);
		Timer timer = apiHealthChecker.getTimer(api);
		Map<String, Object> timerMetrics = createMetricsMap(timer);
		apiMetrics.put("status", timer != null ? getApiStatus(api).getCode() : Status.DOWN.getCode());
		apiMetrics.putAll(timerMetrics);
		log.debug("Metrics for API {}: {}", api, apiMetrics);
		return apiMetrics;
	}

	// --- Dashboard Links ---
	private Map<String, Object> getDashboardLinks() {
		log.debug("Generating dashboard links.");
		Map<String, Object> dashboardLinks = new HashMap<>();
		String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
		dashboardProperties.getDashboards().forEach(dashboard -> {
			String link = baseUrl + "/actuator/dashboard-health/" + dashboard.getName();
			dashboardLinks.put(dashboard.getName(), link);
		});
		log.debug("Generated dashboard links: {}", dashboardLinks);
		return dashboardLinks;
	}

	// --- Dashboard Statuses ---
	private Map<String, Object> getAllDashboardStatuses() {
		return dashboardProperties.getDashboards().stream()
				.collect(Collectors.toMap(DashboardConfiguration::getName, this::getDashboardStatus));
	}
}
