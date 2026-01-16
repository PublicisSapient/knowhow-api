/*
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Data Transfer Object for KPI Request")
public class KpiRequest {

	@NotNull(message = "KPI ID cannot be null")
	@NotEmpty(message = "KPI ID cannot be empty")
	@Schema(description = "Unique identifier of the KPI", example = "kpi12345")
	private String id;

	@Schema(description = "Name of the KPI", example = "Sprint Velocity")
	private String chartData;
	@Schema(description = "Metadata associated with the KPI", example = "{\"unit\":\"points\",\"threshold\":80}")
	private Object metadata;
}
