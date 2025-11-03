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
import static com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.util.SprintAnalyticsUtil.isValidFieldMapping;

import java.util.List;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.SprintDataPoint;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.enums.SprintMetricType;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.model.SprintMetricContext;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.strategy.AbstractSprintMetricStrategy;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;

import lombok.extern.slf4j.Slf4j;

/**
 * Scope Change Percentage Strategy
 * <p>
 * Identifies stories with scope modifications during the sprint using
 * configured scope change labels.
 * 
 * <h3>Calculation Logic:</h3>
 * <ul>
 * <li>Identify stories with scope change labels during sprint</li>
 * <li>Uses configured scope change labels from field mapping
 * (jiraLabelsKPI120)</li>
 * </ul>
 * 
 * <h3>Formula:</h3>
 * 
 * <pre>
 *   Value = Count of issues with scope change labels
 *   Trend = (Scope change issues / Total issues) × 100
 * </pre>
 * 
 * <h3>Example:</h3> <blockquote> Sprint has <b>20 stories</b><br>
 * <b>4 stories</b> have "Scope Change" label<br>
 * <b>Value</b> = 4<br>
 * <b>Trend</b> = (4/20) × 100 = <b>20%</b> </blockquote>
 * 
 * @see SprintMetricType#SCOPE_CHANGE_PERCENTAGE
 */
@Slf4j
@Component
public class ScopeChangePercentageStrategy extends AbstractSprintMetricStrategy {

	@Override
	public SprintMetricType getMetricType() {
		return SprintMetricType.SCOPE_CHANGE_PERCENTAGE;
	}

	@Override
	protected SprintDataPoint calculateForSprint(SprintDetails sprintDetails, SprintMetricContext context,
			int sprintIndex) {
		if (!isValidFieldMapping(context.getFieldMapping(), context.getProjectName())) {
			return createNADataPoint(sprintDetails, "Field mapping not configured", sprintIndex);
		}

		FieldMapping fieldMapping = context.getFieldMapping();

		// Get scope change labels from field mapping
		List<String> scopeChangeLabels = fieldMapping.getJiraLabelsKPI120();
		if (CollectionUtils.isEmpty(scopeChangeLabels)) {
			log.warn("Scope change labels not configured for project: {}", context.getProjectName());
			return createNADataPoint(sprintDetails, "Scope change labels not configured in field mapping", sprintIndex);
		}

		// Get total issues count
		int totalIssuesCount = sprintDetails.getTotalIssues() != null ? sprintDetails.getTotalIssues().size() : 0;

		if (totalIssuesCount == 0) {
			log.info("No issues in sprint: {}", sprintDetails.getSprintName());
			return createNADataPoint(sprintDetails, "No issues in sprint", sprintIndex);
		}

		// Count scope change issues (issues with scope change labels)
		int scopeChangeCount = countScopeChangeIssues(sprintDetails, context, scopeChangeLabels);

		// Calculate percentage: (scopeChangeIssues / totalIssues) * 100
		double percentage = calculatePercentage(scopeChangeCount, totalIssuesCount);

		return createDataPoint(sprintDetails, scopeChangeCount, percentage, sprintIndex);
	}

	/**
	 * Counts issues with scope change labels
	 *
	 * @param sprintDetails
	 *            Sprint details
	 * @param context
	 *            Metric context
	 * @param scopeChangeLabels
	 *            List of scope change label values
	 * @return Count of issues with scope change labels
	 */
	private int countScopeChangeIssues(SprintDetails sprintDetails, SprintMetricContext context,
			List<String> scopeChangeLabels) {

		// Get all sprint issue numbers (total + punted)
		List<String> allIssueNumbers = sprintDetails.getTotalIssues().stream().map(SprintIssue::getNumber).toList();

		// Filter issues with scope change labels
		List<JiraIssue> scopeChangeIssues = allIssueNumbers.stream()
				.map(issueNumber -> context.getJiraIssueByNumber(issueNumber).orElse(null)).filter(Objects::nonNull)
				.filter(issue -> hasAnyScopeChangeLabel(issue, scopeChangeLabels)).distinct().toList();

		return scopeChangeIssues.size();
	}

	/**
	 * Checks if issue has any scope change label
	 *
	 * @param issue
	 *            Jira issue
	 * @param scopeChangeLabels
	 *            Configured scope change labels
	 * @return true if issue has any scope change label
	 */
	private boolean hasAnyScopeChangeLabel(JiraIssue issue, List<String> scopeChangeLabels) {
		if (issue.getLabels() == null || issue.getLabels().isEmpty()) {
			return false;
		}

		// Check if any issue label matches configured scope change labels
		return issue.getLabels().stream().anyMatch(scopeChangeLabels::contains);
	}
}
