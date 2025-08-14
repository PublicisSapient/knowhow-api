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

package com.publicissapient.kpidashboard.apis.executive.strategy;

import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;

/**
 * Strategy interface for different types of executive dashboards.
 */
public interface ExecutiveDashboardStrategy {

	/**
	 * Gets the executive dashboard data based on the provided KPI request.
	 *
	 * @param kpiRequest
	 *            the KPI request containing filter criteria
	 * @return the executive dashboard response DTO
	 */
	ExecutiveDashboardResponseDTO getExecutiveDashboard(KpiRequest kpiRequest);

	/**
	 * Gets the type of strategy (e.g., "kanban", "scrum").
	 *
	 * @return the strategy type
	 */
	String getStrategyType();
}
