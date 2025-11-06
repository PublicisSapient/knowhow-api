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

package com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.strategy;

import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.ProjectSprintMetrics;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.enums.SprintMetricType;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.model.SprintMetricContext;

/**
 * Strategy interface for calculating different sprint metrics Each metric implements this interface
 * to provide its calculation logic
 */
public interface SprintMetricStrategy {

	/**
	 * Calculate the sprint metric for a given project
	 *
	 * @param context Context containing all required data for calculation
	 * @return ProjectSprintMetrics with calculated values for all sprints
	 */
	ProjectSprintMetrics calculate(SprintMetricContext context);

	/**
	 * Get the metric type this strategy handles
	 *
	 * @return SprintMetricType
	 */
	SprintMetricType getMetricType();
}
