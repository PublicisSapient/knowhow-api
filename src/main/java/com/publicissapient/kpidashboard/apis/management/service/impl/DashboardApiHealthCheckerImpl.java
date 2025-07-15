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

import com.publicissapient.kpidashboard.apis.management.service.DashboardApiHealthChecker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardApiHealthCheckerImpl implements DashboardApiHealthChecker {

	private final MeterRegistry meterRegistry;

	@Override
	public Timer getTimer(String apiPath, String... extraTags) {
		var builder = meterRegistry.find("http.server.requests").tags("uri", apiPath);
		if (extraTags != null && extraTags.length > 0) {
			builder = builder.tags(extraTags);
		}
		return builder.timer();
	}

	@Override
	public boolean isApiHealthy(String apiPath) {
		log.info("Checking health for API: {}", apiPath);
		Timer timer = getTimer(apiPath);
		if (timer != null && timer.count() > 0) {
			long errorCount = Optional.ofNullable(getTimer(apiPath, "status", "500")).map(Timer::count).orElse(0L);
			double errorRate = (double) errorCount / timer.count();
			log.debug("API: {}, Error Count: {}, Total Count: {}, Error Rate: {}", apiPath, errorCount, timer.count(),
					errorRate);
			boolean isHealthy = errorRate < 0.1;
			log.info("API: {} is {} with error rate: {}", apiPath, isHealthy ? "healthy" : "unhealthy", errorRate);
			return isHealthy;
		}
		log.warn("No metrics available for API: {}", apiPath);
		return false;
	}

	@Override
	public Status getApiStatus(String api) {
		log.info("Fetching status for API: {}", api);
		Status status = isApiHealthy(api) ? Status.UP : Status.DOWN;
		log.info("Status for API {}: {}", api, status);
		return status;
	}
}
