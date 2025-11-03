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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single data point for a sprint containing metric value and trend
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SprintDataPoint {

	/** Normalized Sprint ID for multiple projects (e.g., "Sprint 1", "Sprint 2") */
	private String sprint;

	/** Sprint display name */
	private String name;

	/** Calculated metric value for this sprint */
	private String value;

	/** Trend value - percentage or absolute change */
	private String trend;
}
