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

import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardRequestDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;

/**
 * Service interface for executive dashboard operations.
 */
public interface ExecutiveService {

    /**
     * Retrieves scrum metrics for the executive dashboard.
     *
     * @param request The executive dashboard request DTO
     * @return Executive dashboard response with scrum metrics
     */
    ExecutiveDashboardResponseDTO getExecutiveDashboardScrum( ExecutiveDashboardRequestDTO request);

    /**
     * Retrieves kanban metrics for the executive dashboard.
     *
     * @param request The executive dashboard request DTO
     * @return Executive dashboard response with kanban metrics
     */
    ExecutiveDashboardResponseDTO getExecutiveDashboardKanban(ExecutiveDashboardRequestDTO request);
}
