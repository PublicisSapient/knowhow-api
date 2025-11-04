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

package com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.strategy.impl;

import static com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.util.SprintAnalyticsUtil.calculatePercentage;
import static com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.util.SprintAnalyticsUtil.createDataPoint;
import static com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.util.SprintAnalyticsUtil.createNADataPoint;
import static com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.util.SprintAnalyticsUtil.getSprintName;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.SprintDataPoint;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.enums.SprintMetricType;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.model.SprintMetricContext;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.strategy.AbstractSprintMetricStrategy;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;

import lombok.extern.slf4j.Slf4j;

/**
 * Scope Added Percentage Strategy
 * <p>
 * Measures scope creep by tracking stories added to the sprint after sprint
 * planning/start.
 * 
 * <h3>Calculation Logic:</h3>
 * <ul>
 * <li>Track stories added to sprint after sprint planning/start</li>
 * <li>Uses addedIssues field from SprintDetails</li>
 * </ul>
 * 
 * <h3>Formula:</h3>
 * 
 * <pre>
 *   Value = Count of issues added after sprint start
 *   Trend = (Added issues / Total issues) × 100
 * </pre>
 * 
 * <h3>Example:</h3> <blockquote> Sprint started with <b>15 stories</b>, <b>5
 * more</b> added later (total = 20)<br>
 * <b>Value</b> = 5<br>
 * <b>Trend</b> = (5/20) × 100 = <b>25%</b> </blockquote>
 * 
 * @see SprintMetricType#SCOPE_ADDED_PERCENTAGE
 */
@Slf4j
@Component
public class ScopeAddedPercentageStrategy extends AbstractSprintMetricStrategy {

	@Override
	public SprintMetricType getMetricType() {
		return SprintMetricType.SCOPE_ADDED_PERCENTAGE;
	}

	@Override
	protected SprintDataPoint calculateForSprint(SprintDetails sprintDetails, SprintMetricContext context,
			int sprintIndex) {
		// Get total issues count
		int totalIssuesCount = sprintDetails.getTotalIssues() != null ? sprintDetails.getTotalIssues().size() : 0;

		if (totalIssuesCount == 0) {
			log.info("No issues in sprint: {}", getSprintName(sprintDetails));
			return createNADataPoint(sprintDetails, "No issues in sprint", sprintIndex, context);
		}

		// Get added issues from sprint details
		Set<String> addedIssuesNumbers = sprintDetails.getAddedIssues();

		// Count added issues
        int addedCount = (addedIssuesNumbers != null) ? addedIssuesNumbers.size() : 0;

		// Calculate percentage: (addedIssues / totalIssues) * 100
		double percentage = calculatePercentage(addedCount, totalIssuesCount);

		return createDataPoint(sprintDetails, addedCount, percentage, sprintIndex);
	}
}
