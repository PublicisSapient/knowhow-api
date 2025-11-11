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
import static com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.util.SprintAnalyticsUtil.isValidFieldMapping;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.SprintDataPoint;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.enums.SprintMetricType;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.model.SprintMetricContext;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.strategy.AbstractSprintMetricStrategy;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;

import lombok.extern.slf4j.Slf4j;

/**
 * Missing Estimation Strategy
 *
 * <p>Tracks estimation quality by counting stories without estimation values. Uses
 * estimationCriteria from field mapping to determine which field to check.
 *
 * <h3>Calculation Logic:</h3>
 *
 * <ul>
 *   <li>Count stories without estimation based on estimationCriteria
 *   <li>Story Point: Checks SprintIssue.storyPoints field (null or 0 = missing)
 *   <li>Original Estimate: Checks SprintIssue.originalEstimate field (null or 0 = missing)
 * </ul>
 *
 * <h3>Formula:</h3>
 *
 * <pre>
 *   Value = Count of issues with missing estimation
 *   Trend = (Missing estimation / Total issues) × 100
 * </pre>
 *
 * <h3>Example:</h3>
 *
 * <blockquote>
 *
 * Sprint has <b>20 stories</b><br>
 * Estimation Criteria = <b>Story Point</b><br>
 * <b>3 stories</b> have no story points (null or 0)<br>
 * <b>Value</b> = 3<br>
 * <b>Trend</b> = (3/20) × 100 = <b>15%</b>
 *
 * </blockquote>
 *
 * @see SprintMetricType#MISSING_ESTIMATION
 */
@Slf4j
@Component
public class MissingEstimationStrategy extends AbstractSprintMetricStrategy {

	@Override
	public SprintMetricType getMetricType() {
		return SprintMetricType.MISSING_ESTIMATION;
	}

	@Override
	protected SprintDataPoint calculateForSprint(
			SprintDetails sprintDetails, SprintMetricContext context, int sprintIndex) {
		if (!isValidFieldMapping(context.getFieldMapping(), context.getProjectName())) {
			return createNADataPoint(sprintDetails, "Field mapping not configured", sprintIndex, context);
		}

		FieldMapping fieldMapping = context.getFieldMapping();

		// Get estimation criteria from field mapping
		String estimationCriteria = fieldMapping.getEstimationCriteria();
		if (StringUtils.isEmpty(estimationCriteria)) {
			log.warn("Estimation criteria not configured for project: {}", context.getProjectName());
			return createNADataPoint(
					sprintDetails, "Estimation criteria not configured", sprintIndex, context);
		}

		// Handle missing total issues
		if (sprintDetails.getTotalIssues() == null || sprintDetails.getTotalIssues().isEmpty()) {
			log.info("No issues in sprint: {}", getSprintName(sprintDetails));
			return createNADataPoint(sprintDetails, "No issues in sprint", sprintIndex, context);
		}

		Set<SprintIssue> totalIssues = sprintDetails.getTotalIssues();
		int totalIssuesCount = totalIssues.size();

		// Count issues with missing estimation based on criteria
		int missingEstimationCount = countIssuesWithMissingEstimation(totalIssues, estimationCriteria);

		// Calculate percentage: (missing / total) * 100
		double percentage = calculatePercentage(missingEstimationCount, totalIssuesCount);

		log.debug(
				"Sprint: {}, Total issues: {}, Missing estimation count: {}, Percentage: {}",
				getSprintName(sprintDetails),
				totalIssuesCount,
				missingEstimationCount,
				percentage);

		return createDataPoint(
				sprintDetails, missingEstimationCount, percentage, sprintIndex, Constant.PERCENTAGE);
	}

	/**
	 * Counts issues with missing estimation based on estimation criteria
	 *
	 * @param sprintIssues List of sprint issues
	 * @param estimationCriteria Estimation criteria from field mapping (Story Point, Original
	 *     Estimate)
	 * @return Count of issues without estimation
	 */
	private int countIssuesWithMissingEstimation(
			Set<SprintIssue> sprintIssues, String estimationCriteria) {
		return (int)
				sprintIssues.stream()
						.filter(issue -> hasMissingEstimation(issue, estimationCriteria))
						.count();
	}

	/**
	 * Checks if SprintIssue has missing estimation based on criteria Missing = null or 0.0 for the
	 * configured estimation field
	 *
	 * @param sprintIssue Sprint issue
	 * @param estimationCriteria Estimation criteria (Story Point, Original Estimate)
	 * @return true if estimation is missing
	 */
	private boolean hasMissingEstimation(SprintIssue sprintIssue, String estimationCriteria) {
		if (StringUtils.isEmpty(estimationCriteria)) {
			return true;
		}

		// Check based on estimation criteria
		if (CommonConstant.STORY_POINT.equalsIgnoreCase(estimationCriteria)) {
			Double storyPoints = sprintIssue.getStoryPoints();
			log.debug(
					"Issue: {}, Estimation criteria: Story Point, Value: {}, Missing: {}",
					sprintIssue.getNumber(),
					storyPoints,
					(storyPoints == null || storyPoints == 0.0));
			return storyPoints == null || storyPoints == 0.0;
		} else {
			Double originalEstimate = sprintIssue.getOriginalEstimate();
			log.debug(
					"Issue: {}, Estimation criteria: Original Estimate, Value: {}, Missing: {}",
					sprintIssue.getNumber(),
					originalEstimate,
					(originalEstimate == null || originalEstimate == 0.0));
			return originalEstimate == null || originalEstimate == 0.0;
		}
	}
}
