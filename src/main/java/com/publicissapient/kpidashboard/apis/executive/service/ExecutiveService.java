/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/
package com.publicissapient.kpidashboard.apis.executive.service;

import com.publicissapient.kpidashboard.apis.errors.ExecutiveDataException;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardRequestDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;

/** Service interface for executive dashboard operations. */
public interface ExecutiveService {

	/**
	 * Retrieves scrum metrics for the executive dashboard.
	 *
	 * @param request The executive dashboard request DTO containing filter criteria
	 * @return Executive dashboard response with scrum metrics
	 * @throws ExecutiveDataException if there is an error processing the request
	 * @throws IllegalArgumentException if the request parameters are invalid
	 */
	ExecutiveDashboardResponseDTO getExecutiveDashboardScrum(ExecutiveDashboardRequestDTO request)
			throws ExecutiveDataException, IllegalArgumentException;

	/**
	 * Retrieves kanban metrics for the executive dashboard.
	 *
	 * @param request The executive dashboard request DTO containing filter criteria
	 * @return Executive dashboard response with kanban metrics
	 * @throws ExecutiveDataException if there is an error processing the request
	 * @throws IllegalArgumentException if the request parameters are invalid
	 */
	ExecutiveDashboardResponseDTO getExecutiveDashboardKanban(ExecutiveDashboardRequestDTO request)
			throws ExecutiveDataException, IllegalArgumentException;
}
