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

import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.SprintDataPoint;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.enums.SprintMetricType;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.model.SprintMetricContext;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.strategy.AbstractSprintMetricStrategy;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;

import lombok.extern.slf4j.Slf4j;

/**
 * Scope Change Post Start Strategy
 * <p>
 * Tracks net scope changes (added + removed) after sprint start. Uses
 * SprintDetails addedIssues and puntedIssues to calculate total scope
 * volatility.
 * 
 * <h3>Calculation Logic:</h3>
 * <ul>
 * <li>Count issues added to sprint after start (addedIssues)</li>
 * <li>Count issues removed/punted from sprint (puntedIssues)</li>
 * <li>Calculate net scope change = added + removed</li>
 * </ul>
 * 
 * <h3>Formula:</h3>
 * 
 * <pre>
 *   Net Change = Count of added issues + Count of punted issues
 *   Value = Net scope change count
 *   Trend = (Net change / Total issues) × 100
 * </pre>
 * 
 * <h3>Example:</h3> <blockquote> Sprint has <b>20 total stories</b><br>
 * <b>5 stories</b> added after sprint start<br>
 * <b>3 stories</b> removed/punted during sprint<br>
 * Net Change = 5 + 3 = <b>8 changes</b><br>
 * <b>Value</b> = 8<br>
 * <b>Trend</b> = (8/20) × 100 = <b>40%</b> </blockquote>
 * 
 * @see SprintMetricType#SCOPE_CHANGE_POST_START
 */
@Slf4j
@Component
public class ScopeChangePostStartStrategy extends AbstractSprintMetricStrategy {

	@Override
	public SprintMetricType getMetricType() {
		return SprintMetricType.SCOPE_CHANGE_POST_START;
	}

	@Override
	protected SprintDataPoint calculateForSprint(SprintDetails sprintDetails, SprintMetricContext context,
			int sprintIndex) {
		// Get total issues count
		int totalIssuesCount = sprintDetails.getTotalIssues() != null ? sprintDetails.getTotalIssues().size() : 0;

		if (totalIssuesCount == 0) {
			log.info("No issues in sprint: {}", sprintDetails.getSprintName());
			return createNADataPoint(sprintDetails, "No issues in sprint", sprintIndex, context);
		}

		// Count added issues (scope added after sprint start)
		int addedIssuesCount = sprintDetails.getAddedIssues() != null ? sprintDetails.getAddedIssues().size() : 0;

		// Count punted/removed issues (scope removed during sprint)
		int puntedIssuesCount = sprintDetails.getPuntedIssues() != null ? sprintDetails.getPuntedIssues().size() : 0;

		// Calculate net scope change (added + removed)
		int netScopeChange = addedIssuesCount + puntedIssuesCount;

		if (netScopeChange == 0) {
			log.info("No scope changes in sprint: {}", sprintDetails.getSprintName());
			return createDataPoint(sprintDetails, 0, 0.0, sprintIndex);
		}

		// Calculate percentage: (net change / total issues) * 100
		double percentage = calculatePercentage(netScopeChange, totalIssuesCount);

		log.debug("Sprint: {}, Added: {}, Punted: {}, Net Change: {}, Percentage: {}%", sprintDetails.getSprintName(),
				addedIssuesCount, puntedIssuesCount, netScopeChange, percentage);

		return createDataPoint(sprintDetails, netScopeChange, percentage, sprintIndex);
	}
}
