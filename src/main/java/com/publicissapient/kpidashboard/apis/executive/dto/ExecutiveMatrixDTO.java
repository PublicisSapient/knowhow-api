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

import java.util.List;

/**
 * DTO representing the matrix data structure for the executive dashboard.
 * Contains a list of project metrics rows and column definitions.
 */
@Data
@Builder
public class ExecutiveMatrixDTO {
    /**
     * List of project metrics rows.
     * Each row contains metrics for a specific project.
     */
    private List<ProjectMetricsDTO> rows;
    
    /**
     * List of column definitions.
     * Defines the structure and display properties of each column.
     */
    private List<ColumnDefinitionDTO> columns;
}
