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

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
@Schema(description = "Data Transfer Object for Report Request")
public class ReportRequest {
	@Schema(
			description =
					"Unique identifier of the report. If updating an existing report, this field must be provided.",
			example = "64b8f0c2e1b2c3a4d5e6f7g8")
	private String id;

	@Schema(description = "Name of the report", example = "Sprint Velocity Report")
	@NotNull(message = "Report name cannot be null")
	@NotEmpty(message = "Report name cannot be empty")
	private String name;

	@Schema(description = "List of KPIs included in the report", implementation = KpiRequest.class)
	@NotNull(message = "KPIs cannot be null")
	@NotEmpty(message = "KPIs cannot be empty")
	@Valid
	private List<KpiRequest> kpis;
}
