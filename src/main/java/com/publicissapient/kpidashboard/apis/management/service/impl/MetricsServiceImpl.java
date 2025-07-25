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

import com.publicissapient.kpidashboard.apis.management.configs.DashboardConfig;
import com.publicissapient.kpidashboard.apis.management.dto.ApiDetailDto;
import com.publicissapient.kpidashboard.apis.management.service.MetricsService;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetricsServiceImpl implements MetricsService {

	private final MeterRegistry meterRegistry;
	private final DashboardConfig dashboardConfig;

	private static final String STATUS_UP = Status.UP.getCode();
	private static final String STATUS_DOWN = Status.DOWN.getCode();
	private static final String STATUS_5XX = "5";

	@Override
	public ApiDetailDto getApiMetrics(String apiPath) {
		log.info("Fetching metrics for API: {}", apiPath);
		try {
			Optional<Timer> timerOpt = getTimer(apiPath);

			return timerOpt.map(timer -> ApiDetailDto.builder().name(apiPath)
					.status(isApiHealthy(apiPath) ? STATUS_UP : STATUS_DOWN).max(timer.max(TimeUnit.SECONDS))
					.count((int) timer.count()).totalTime(timer.totalTime(TimeUnit.SECONDS))
					.errorRate(getErrorRate(apiPath)).errorThreshold(dashboardConfig.getMaxApiErrorThreshold()).build())
					.orElseGet(() -> {
						log.warn("No metrics found for API: {}", apiPath);
						return buildDefaultApiDetail(apiPath, STATUS_UP);
					});
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
			double errorRate = getErrorRate(apiPath);
			return errorRate < dashboardConfig.getMaxApiErrorThreshold();
		} catch (Exception e) {
			log.error("Error checking health for API {}: {}", apiPath, e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Retrieves the {@link Timer} metric for a given API URI.
	 *
	 * @param uri
	 *            the API URI
	 * @return optional containing the Timer if found, empty otherwise
	 */
	private Optional<Timer> getTimer(String uri) {

		log.debug("Searching for timer metrics for API: {}", uri);
		return Optional.ofNullable(Search.in(meterRegistry).name("http.server.requests").tag("uri", uri).timer());
	}

	private Collection<Meter> getMetersForUri(String uri) {
		return meterRegistry.find("http.server.requests").tag("uri", uri).meters();
	}

	/**
	 * Calculates the error rate for a given API path.
	 *
	 * @param apiPath
	 *            the API path
	 * @return error rate as a percentage
	 */
	private double getErrorRate(String apiPath) {
		log.debug("Calculating error rate for API: {}", apiPath);

		Collection<Meter> meters = getMetersForUri(apiPath);
		double totalRequests = 0.0;
		double errors = 0.0;

		for (Meter meter : meters) {
			String status = meter.getId().getTag("status");
			if (status == null)
				continue;

			double count = StreamSupport.stream(meter.measure().spliterator(), false)
					.filter(m -> m.getStatistic().name().equals(Statistic.COUNT.getTagValueRepresentation()))
					.mapToDouble(Measurement::getValue).sum();

			totalRequests += count;
			if (status.startsWith(STATUS_5XX)) {
				errors += count;
			}
		}

		if (totalRequests == 0.0) {
			log.debug("No requests found for API {}. Returning error rate 0.0", apiPath);
			return 0.0;
		}

		double errorRate = (errors * 100.0) / totalRequests;
		log.debug("API {}: totalRequests={}, errorCount={}, errorRate={}", apiPath, totalRequests, errors, errorRate);
		return errorRate;
	}

	/**
	 * Builds a default {@link ApiDetailDto} for an API with no metrics.
	 *
	 * @param apiPath
	 *            the API path
	 * @return default API detail with status and zeroed metrics
	 */
	private ApiDetailDto buildDefaultApiDetail(String apiPath, String status) {
		return ApiDetailDto.builder().name(apiPath).status(status).max(0).count(0).totalTime(0).errorRate(0)
				.errorThreshold(dashboardConfig.getMaxApiErrorThreshold()).build();
	}

}