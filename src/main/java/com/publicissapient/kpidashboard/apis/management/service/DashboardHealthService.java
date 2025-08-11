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

import com.publicissapient.kpidashboard.apis.model.HealthResponseDto;

public interface DashboardHealthService {

	/**
	 * Generates health overview for all dashboard types.
	 *
	 * @return {@link HealthResponseDto} containing aggregated health status and
	 *         metrics for all dashboard types
	 */
	HealthResponseDto getDashboardHealth();

	/**
	 * Generates health details for a specific dashboard type.
	 *
	 * @param boardType
	 *            the dashboard type (e.g., scrum, kanban)
	 * @return {@link HealthResponseDto} containing health status and metrics for
	 *         the specified dashboard type
	 */
	HealthResponseDto getBoardTypeHealth(String boardType);

	/**
	 * Generates detailed health information for a specific dashboard within a board
	 * type.
	 *
	 * @param boardType
	 *            the dashboard type (e.g., scrum, kanban)
	 * @param dashboard
	 *            the dashboard name (e.g., speed, quality)
	 * @return {@link HealthResponseDto} containing detailed health status and
	 *         metrics for the specified dashboard
	 */
	HealthResponseDto getDashboardDetailHealth(String boardType, String dashboard);
}