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
package com.publicissapient.kpidashboard.apis.executive.dto;

import lombok.Builder;
import lombok.Data;

/**
 * The root DTO for the executive dashboard API response.
 * Wraps all the dashboard data in a 'data' field.
 */
@Data
@Builder
public class ExecutiveDashboardResponseDTO {
    /**
     * The main data container for the executive dashboard response.
     */
    private ExecutiveDashboardDataDTO data;
}
