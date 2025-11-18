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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.SprintDataPoint;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.enums.SprintMetricType;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.model.SprintMetricContext;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.strategy.AbstractSprintMetricStrategy;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.util.SprintAnalyticsUtil;
import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
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

	@Autowired private ConfigHelperService configHelperService;

	@Autowired private SprintRepository sprintRepository;

	@Autowired private JiraIssueRepository jiraIssueRepository;

	@Override
	protected SprintDataPoint calculateForSprint(
			SprintDetails sprintDetails, SprintMetricContext context, int sprintIndex) {
		if (sprintIndex > 0) {
			return null;
		}

		try {
			List<JiraIssue> totalIssues = getNextSprintRefinementData(context);

			if (CollectionUtils.isEmpty(totalIssues)) {
				return createNADataPoint(
						sprintDetails, "No issues found in next sprint", sprintIndex, context);
			}

			long unRefinedCount =
					totalIssues.stream()
							.filter(issue -> CollectionUtils.isNotEmpty(issue.getUnRefinedValue188()))
							.count();

			double percentage =
					SprintAnalyticsUtil.calculatePercentage(unRefinedCount, totalIssues.size());

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

	private List<JiraIssue> getNextSprintRefinementData(SprintMetricContext context) {
		FieldMapping fieldMapping =
				configHelperService.getFieldMappingMap().get(context.getBasicProjectConfigId());
		if (CollectionUtils.isEmpty(fieldMapping.getJiraIssueTypeNamesKPI188())) {
			return new ArrayList<>();
		}

		List<SprintDetails> futureSprintList =
				sprintRepository.findByBasicProjectConfigIdAndStateIgnoreCaseOrderByStartDateASC(
						context.getBasicProjectConfigId(), SprintDetails.SPRINT_STATE_FUTURE);

		if (CollectionUtils.isEmpty(futureSprintList)) {
			return new ArrayList<>();
		}

		SprintDetails nextSprint = futureSprintList.get(0);
		return jiraIssueRepository.findBySprintID(nextSprint.getSprintID()).stream()
				.filter(issue -> getTypeNames(fieldMapping).contains(issue.getTypeName().toLowerCase()))
				.toList();
	}

	private Set<String> getTypeNames(FieldMapping fieldMapping) {
		return fieldMapping.getJiraIssueTypeNamesKPI188().stream()
				.flatMap(
						name ->
								"Defect".equalsIgnoreCase(name)
										? Stream.of("defect", "bug")
										: Stream.of(name.trim().toLowerCase()))
				.collect(Collectors.toSet());
	}
}
