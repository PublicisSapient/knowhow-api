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

import com.publicissapient.kpidashboard.apis.config.DashboardConfig;
import com.publicissapient.kpidashboard.apis.model.ApiDetailDto;
import com.publicissapient.kpidashboard.apis.management.service.MetricsService;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetricsServiceImpl implements MetricsService {

	private final MeterRegistry meterRegistry;
	private final DashboardConfig dashboardConfig;
	private final MetricsEndpoint metricsEndpoint;

	private static final String STATUS_UP = Status.UP.getCode();
	private static final String STATUS_DOWN = Status.DOWN.getCode();
	private static final String STATUS_5XX = "5";
	private static final String METRIC_NAME = "http.server.requests";
	private static final String URI_TAG = "uri";

	@Override
	public ApiDetailDto getApiMetrics(String apiPath) {
		log.info("Fetching metrics for API: {}", apiPath);
		try {
			MetricsEndpoint.MetricDescriptor metricDescriptor = fetchMetricDescriptor(apiPath);

			if (metricDescriptor == null || metricDescriptor.getMeasurements().isEmpty()) {
				log.warn("No metrics found for API: {}", apiPath);
				return buildDefaultApiDetail(apiPath, STATUS_UP);
			}

			double max = extractMetricValue(metricDescriptor, Statistic.MAX.name()).orElse(0.0);
			double count = extractMetricValue(metricDescriptor, Statistic.COUNT.name()).orElse(0.0);
			double totalTime = extractMetricValue(metricDescriptor, Statistic.TOTAL_TIME.name()).orElse(0.0);

			double errorRate = calculateErrorRate(apiPath);
			boolean isHealthy = isErrorRateBelowThreshold(errorRate);

			return ApiDetailDto.builder().name(apiPath).status(isHealthy ? STATUS_UP : STATUS_DOWN).max(max)
					.count((int) count).totalTime(totalTime).errorRate(errorRate)
					.errorThreshold(dashboardConfig.getMaxApiErrorThreshold()).build();

		} catch (Exception e) {
			log.error("Error retrieving metrics for API {}: {}", apiPath, e.getMessage(), e);
			return buildDefaultApiDetail(apiPath, STATUS_DOWN);
		}
	}

	@Override
	public List<ApiDetailDto> getApisMetrics(List<String> apiPaths) {
		log.info("Fetching metrics for multiple APIs: {}", apiPaths);
		return apiPaths.stream().map(this::getApiMetrics).collect(Collectors.toList());
	}

	@Override
	public boolean isApiHealthy(String apiPath) {
		log.debug("Checking health status for API: {}", apiPath);
		try {
			double errorRate = calculateErrorRate(apiPath);
			return isErrorRateBelowThreshold(errorRate);
		} catch (Exception e) {
			log.error("Error checking health for API {}: {}", apiPath, e.getMessage(), e);
			return false;
		}
	}

	private MetricsEndpoint.MetricDescriptor fetchMetricDescriptor(String apiPath) {
		return metricsEndpoint.metric(METRIC_NAME, Collections.singletonList(URI_TAG + ":" + apiPath));
	}

	private Optional<Double> extractMetricValue(MetricsEndpoint.MetricDescriptor metricDescriptor, String statistic) {
		return metricDescriptor.getMeasurements().stream().filter(m -> statistic.equals(m.getStatistic().name()))
				.findFirst().map(MetricsEndpoint.Sample::getValue);
	}

	private double calculateErrorRate(String apiPath) {
		log.debug("Calculating error rate for API: {}", apiPath);

		Collection<Meter> meters = getMetersForUri(apiPath);
		double totalRequests = meters.stream().mapToDouble(this::getMeterCount).sum();
		double errors = meters.stream().filter(meter -> {
			String status = meter.getId().getTag("status");
			return status != null && status.startsWith(STATUS_5XX);
		}).mapToDouble(this::getMeterCount).sum();

		if (totalRequests == 0.0) {
			log.debug("No requests found for API {}. Returning error rate 0.0", apiPath);
			return 0.0;
		}

		double errorRate = (errors * 100.0) / totalRequests;
		log.debug("API {}: totalRequests={}, errorCount={}, errorRate={}", apiPath, totalRequests, errors, errorRate);
		return errorRate;
	}

	private Collection<Meter> getMetersForUri(String uri) {
		return meterRegistry.find(METRIC_NAME).tag(URI_TAG, uri).meters();
	}

	private double getMeterCount(Meter meter) {
		return StreamSupport.stream(meter.measure().spliterator(), false)
				.filter(m -> Statistic.COUNT.name().equalsIgnoreCase(m.getStatistic().name()))
				.mapToDouble(Measurement::getValue).sum();
	}

	private boolean isErrorRateBelowThreshold(double errorRate) {
		return errorRate < dashboardConfig.getMaxApiErrorThreshold();
	}

	private ApiDetailDto buildDefaultApiDetail(String apiPath, String status) {
		return ApiDetailDto.builder().name(apiPath).status(status).max(0).count(0).totalTime(0).errorRate(0)
				.errorThreshold(dashboardConfig.getMaxApiErrorThreshold()).build();
	}

}