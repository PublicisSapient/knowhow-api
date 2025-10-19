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

import java.util.List;

import com.publicissapient.kpidashboard.apis.model.ApiDetailDto;

public interface MetricsService {

	/**
	 * Retrieves metrics for a specific API endpoint.
	 *
	 * @param apiPath the API path
	 * @return {@link ApiDetailDto} containing metrics details for the API
	 */
	ApiDetailDto getApiMetrics(String apiPath);

	/**
	 * Retrieves metrics for a list of API endpoints.
	 *
	 * @param apiPaths list of API paths
	 * @return list of {@link ApiDetailDto} containing metrics details for each API
	 */
	List<ApiDetailDto> getApisMetrics(List<String> apiPaths);

	/**
	 * Determines if an API is healthy based on its error rate.
	 *
	 * @param apiPath the API path
	 * @return true if the API is healthy, false otherwise
	 */
	boolean isApiHealthy(String apiPath);
}
