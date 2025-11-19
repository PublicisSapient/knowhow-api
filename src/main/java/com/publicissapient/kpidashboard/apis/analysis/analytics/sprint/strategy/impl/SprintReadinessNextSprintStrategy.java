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

import static com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.util.SprintAnalyticsUtil.createDataPoint;
import static com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.util.SprintAnalyticsUtil.createNADataPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.SprintDataPoint;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.enums.SprintMetricType;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.model.SprintMetricContext;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.strategy.AbstractSprintMetricStrategy;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.util.SprintAnalyticsUtil;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Sprint Readiness Next Sprint Strategy
 *
 * <p>Tracks sprint readiness by analyzing unrefined stories in the next sprint after the active
 * sprint. Uses NextSprintLateRefinementServiceImpl to get next sprint data and calculates readiness
 * metrics.
 *
 * <h3>Calculation Logic:</h3>
 *
 * <ul>
 *   <li>Gets the first future sprint after active sprint
 *   <li>Counts stories with unrefined values (UnRefinedValue188 field)
 *   <li>Calculates percentage of unrefined stories vs total stories
 * </ul>
 *
 * <h3>Formula:</h3>
 *
 * <pre>
 *   Value = Count of unrefined stories in next sprint
 *   Trend = (Unrefined stories / Total stories) × 100
 * </pre>
 *
 * <h3>Validation Logic:</h3>
 *
 * <ul>
 *   <li><b>Sprint Index Validation:</b> Returns null for sprintIndex > 0 to ensure project-level
 *       aggregation
 *   <li><b>Field Mapping Validation:</b> Validates jiraIssueTypeNamesKPI188 configuration exists
 *   <li><b>Future Sprint Validation:</b> Checks if future sprints exist in FUTURE state
 *   <li><b>Issue Type Validation:</b> Filters issues by configured issue types (Story, Bug, Defect)
 *   <li><b>Data Availability:</b> Returns N/A if no issues found in next sprint
 *   <li><b>Error Handling:</b> Returns N/A with error message for any calculation failures
 * </ul>
 *
 * <h3>Return Conditions:</h3>
 *
 * <ul>
 *   <li><b>Success:</b> Valid count and percentage when next sprint has configured issue types
 *   <li><b>N/A:</b> No future sprints, no configured issue types, or no matching issues
 *   <li><b>Null:</b> Sprint index > 0 (project-level metric only)
 *   <li><b>Error N/A:</b> Exception during calculation with error message
 * </ul>
 *
 * <h3>Example:</h3>
 *
 * <blockquote>
 *
 * Next sprint has <b>15 stories</b><br>
 * <b>3 stories</b> are unrefined<br>
 * <b>Value</b> = 3<br>
 * <b>Trend</b> = (3/15) × 100 = <b>20%</b>
 *
 * </blockquote>
 *
 * @see SprintMetricType#SPRINT_READINESS_NEXT_SPRINT
 */
@Slf4j
@Component
public class SprintReadinessNextSprintStrategy extends AbstractSprintMetricStrategy {

	private final SprintRepository sprintRepository;
	private final JiraIssueRepository jiraIssueRepository;

	public SprintReadinessNextSprintStrategy(
			SprintRepository sprintRepository, JiraIssueRepository jiraIssueRepository) {
		this.sprintRepository = sprintRepository;
		this.jiraIssueRepository = jiraIssueRepository;
	}

	@Override
	protected SprintDataPoint calculateForSprint(
			SprintDetails sprintDetails, SprintMetricContext context, int sprintIndex) {
		if (sprintIndex > 0) {
			return null;
		}

		try {
			SprintRefinementData refinementData = getNextSprintRefinementData(context);

			if (CollectionUtils.isEmpty(refinementData.issues)) {
				String message =
						refinementData.nextSprintName != null
								? "No issues found in next sprint: " + refinementData.nextSprintName
								: "No issues found in next sprint";
				return createNADataPoint(sprintDetails, message, sprintIndex, context);
			}

			long unRefinedCount =
					refinementData.issues.stream()
							.filter(issue -> CollectionUtils.isNotEmpty(issue.getUnRefinedValue188()))
							.count();

			double percentage =
					SprintAnalyticsUtil.calculatePercentage(unRefinedCount, refinementData.issues.size());

			log.debug(
					"Sprint Readiness Next Sprint calculation - Total issues: {}, Unrefined count: {}, Percentage: {}%",
					refinementData.issues.size(), unRefinedCount, percentage);

			return createDataPoint(
					sprintDetails, unRefinedCount, percentage, sprintIndex, Constant.PERCENTAGE);

		} catch (Exception e) {
			log.error("Error calculating sprint readiness for next sprint: {}", e.getMessage(), e);
			return createNADataPoint(
					sprintDetails, "Error calculating metric: " + e.getMessage(), sprintIndex, context);
		}
	}

	@Override
	public SprintMetricType getMetricType() {
		return SprintMetricType.SPRINT_READINESS_NEXT_SPRINT;
	}

	/**
	 * Retrieves next sprint refinement data including issues and sprint name.
	 *
	 * @param context Sprint metric context containing field mapping and project config
	 * @return SprintRefinementData containing filtered issues and next sprint name
	 */
	private SprintRefinementData getNextSprintRefinementData(SprintMetricContext context) {
		FieldMapping fieldMapping = context.getFieldMapping();
		if (fieldMapping == null
				|| CollectionUtils.isEmpty(fieldMapping.getJiraIssueTypeNamesKPI188())) {
			return new SprintRefinementData(new ArrayList<>(), null);
		}

		List<SprintDetails> futureSprintList =
				sprintRepository.findByBasicProjectConfigIdAndStateIgnoreCaseOrderByStartDateASC(
						context.getBasicProjectConfigId(), SprintDetails.SPRINT_STATE_FUTURE);

		if (CollectionUtils.isEmpty(futureSprintList)) {
			return new SprintRefinementData(new ArrayList<>(), null);
		}

		SprintDetails nextSprint = futureSprintList.get(0);
		List<JiraIssue> issues =
				jiraIssueRepository.findBySprintID(nextSprint.getSprintID()).stream()
						.filter(issue -> getTypeNames(fieldMapping).contains(issue.getTypeName().toLowerCase()))
						.toList();
		return new SprintRefinementData(issues, nextSprint.getSprintName());
	}

	/**
	 * Converts configured issue type names to lowercase set for filtering. Maps 'Defect' to both
	 * 'defect' and 'bug' for compatibility.
	 *
	 * @param fieldMapping Field mapping containing issue type configuration
	 * @return Set of lowercase issue type names for filtering
	 */
	private Set<String> getTypeNames(FieldMapping fieldMapping) {
		return fieldMapping.getJiraIssueTypeNamesKPI188().stream()
				.flatMap(
						name ->
								"Defect".equalsIgnoreCase(name)
										? Stream.of("defect", "bug")
										: Stream.of(name.trim().toLowerCase()))
				.collect(Collectors.toSet());
	}

	private static class SprintRefinementData {
		final List<JiraIssue> issues;
		final String nextSprintName;

		SprintRefinementData(List<JiraIssue> issues, String nextSprintName) {
			this.issues = issues;
			this.nextSprintName = nextSprintName;
		}
	}
}
