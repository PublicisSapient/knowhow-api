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

package com.publicissapient.kpidashboard.apis.management.service;

import io.micrometer.core.instrument.Timer;
import org.springframework.boot.actuate.health.Status;

/**
 *
 * @author shunaray
 */
public interface DashboardApiHealthChecker {

	/**
	 * Returns a Timer for the given API path with optional extra tags.
	 *
	 * @param apiPath
	 *            api path to get the timer for
	 * @param extraTags
	 *            optional extra tags to add to the timer
	 * @return Timer instance for the specified API path with the given tags.
	 */
	Timer getTimer(String apiPath, String... extraTags);

	/**
	 * @param apiPath
	 *            api path to check health for
	 * @return Returns true if the error rate (status 500) is less than 10%, false
	 *         otherwise
	 */
	boolean isApiHealthy(String apiPath);

	/**
	 *
	 * @param api
	 *            api path to check health for
	 * @return Status of the API, UP if healthy, DOWN otherwise
	 */
	Status getApiStatus(String api);
}
