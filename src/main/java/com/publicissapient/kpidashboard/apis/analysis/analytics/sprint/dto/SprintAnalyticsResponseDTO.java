/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for Sprint Analytics Contains all calculated sprint metrics
 * across projects with metadata and warnings
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SprintAnalyticsResponseDTO {

	/** List of all sprint metrics with project-wise data */
	private List<SprintMetricDTO> analytics;

	/** List of warnings for user transparency (e.g., "Project X has no sprints") */
	@Builder.Default
	private List<String> warnings = new java.util.ArrayList<>();

	public void addWarning(String warning) {
		this.warnings.add(warning);
	}
}
