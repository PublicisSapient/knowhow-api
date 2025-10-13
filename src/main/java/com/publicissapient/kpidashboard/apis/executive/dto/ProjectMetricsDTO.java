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

/** DTO representing the metrics for a single project in the executive dashboard. */
@Data
@Builder
public class ProjectMetricsDTO {
	/** The unique identifier for the project. */
	private String id;

	/** The display name of the project. */
	private String name;

	/** The completion percentage of the project. Format: "XX%" (e.g., "73%") */
	private String completion;

	/** The health status of the project. Example: "Healthy", "At Risk", "Critical" */
	private String health;

	/**
	 * The board maturity metrics for the project. Contains key-value pairs of board names and their
	 * maturity levels.
	 */
	private BoardMaturityDTO boardMaturity;
}
