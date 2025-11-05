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

import static com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.util.SprintAnalyticsUtil.isValidSprintDetails;

import java.util.ArrayList;
import java.util.List;

import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.ProjectSprintMetrics;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.SprintDataPoint;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.model.SprintMetricContext;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;

/**
 * Abstract base class for sprint metric strategies Provides common functionality for all metric
 * calculations
 */
public abstract class AbstractSprintMetricStrategy implements SprintMetricStrategy {

	@Override
	public ProjectSprintMetrics calculate(SprintMetricContext context) {
		List<SprintDataPoint> sprintDataPoints = new ArrayList<>();

		int sprintIndex = 0;
		for (SprintDetails sprintDetails : context.getSprintDetailsList()) {
			if (!isValidSprintDetails(sprintDetails)) {
				sprintIndex++;
				continue;
			}
			SprintDataPoint sprintDataPoint = calculateForSprint(sprintDetails, context, sprintIndex);
			if (sprintDataPoint != null) {
				sprintDataPoints.add(sprintDataPoint);
			}
			sprintIndex++;
		}

		return ProjectSprintMetrics.builder()
				.name(context.getProjectName())
				.projectBasicConfigId(context.getBasicProjectConfigId().toString())
				.sprints(sprintDataPoints)
				.build();
	}

	/**
	 * Calculate metric value for a single sprint Subclasses must implement this method
	 *
	 * @param sprintDetails Details of the sprint
	 * @param context Data context for calculation
	 * @param sprintIndex Index of sprint
	 * @return SprintDataPoint with calculated value and trend
	 */
	protected abstract SprintDataPoint calculateForSprint(
			SprintDetails sprintDetails, SprintMetricContext context, int sprintIndex);
}
