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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.SprintDataPoint;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.enums.SprintMetricType;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.model.SprintMetricContext;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.strategy.AbstractSprintMetricStrategy;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Grooming Day One Strategy
 * <p>
 * Measures sprint readiness by tracking stories still in "Grooming" status on
 * Day 1 of the sprint.
 * 
 * <h3>Calculation Logic:</h3>
 * <ul>
 * <li>Count stories still in "Grooming" status on Day 1 of sprint</li>
 * <li>Uses statusUpdationLog from JiraIssueCustomHistory to check status at
 * sprint start</li>
 * </ul>
 * 
 * <h3>Formula:</h3>
 * 
 * <pre>
 *   Value = Count of issues in Grooming status on Day 1
 *   Trend = (Grooming stories / Total stories) × 100
 * </pre>
 * 
 * <h3>Example:</h3> <blockquote> Sprint has <b>20 total stories</b><br>
 * <b>5 stories</b> still in "Grooming" status on sprint Day 1<br>
 * <b>Value</b> = 5<br>
 * <b>Trend</b> = (5/20) × 100 = <b>25%</b> </blockquote>
 * 
 * @see SprintMetricType#GROOMING_DAY_ONE
 */
@Slf4j
@Component
public class GroomingDayOneStrategy extends AbstractSprintMetricStrategy {

	@Override
	public SprintMetricType getMetricType() {
		return SprintMetricType.GROOMING_DAY_ONE;
	}

	@Override
	protected SprintDataPoint calculateForSprint(SprintDetails sprintDetails, SprintMetricContext context,
			int sprintIndex) {
		if (!isValidFieldMapping(context.getFieldMapping(), context.getProjectName())) {
			return createNADataPoint(sprintDetails, "Field mapping not configured", sprintIndex);
		}

		FieldMapping fieldMapping = context.getFieldMapping();

		// Get Grooming status from field mapping
		List<String> groomingStatuses = fieldMapping.getJiraStatusKPI187();
		if (CollectionUtils.isEmpty(groomingStatuses)) {
			log.warn("Grooming statuses not configured for project: {}", context.getProjectName());
			return createNADataPoint(sprintDetails, "Grooming statuses not configured in field mapping", sprintIndex);
		}

		Set<String> groomingStatusSet = groomingStatuses.stream().map(status -> status.trim().toLowerCase())
				.collect(Collectors.toSet());

		// Get sprint start date (Day 1)
		LocalDateTime sprintStartDate = DateUtil.stringToLocalDateTime(sprintDetails.getStartDate(),
				DateUtil.TIME_FORMAT_WITH_SEC);
		if (sprintStartDate == null) {
			log.warn("Sprint start date is null for sprint: {}", sprintDetails.getSprintName());
			return createNADataPoint(sprintDetails, "Sprint start date not available", sprintIndex);
		}
		LocalDate dayOne = sprintStartDate.toLocalDate();

		// Get total sprint issues count
		if (sprintDetails.getTotalIssues() == null || sprintDetails.getTotalIssues().isEmpty()) {
			log.info("No issues found in sprint: {}", sprintDetails.getSprintName());
			return createNADataPoint(sprintDetails, "No issues in sprint", sprintIndex);
		}
		int totalIssuesCount = sprintDetails.getTotalIssues().size();

		// Count issues in Grooming status on Day 1
		int groomingCount = countIssuesInGroomingOnDayOne(sprintDetails, context, groomingStatusSet, dayOne);

		// Calculate percentage: (groomingCount / totalIssuesCount) * 100
		double percentage = calculatePercentage(groomingCount, totalIssuesCount);

		return createDataPoint(sprintDetails, groomingCount, percentage, sprintIndex);
	}

	/**
	 * Counts issues that were in Grooming status on Day 1 of sprint Logic: For each
	 * issue in sprint, check its status on sprint start date
	 *
	 * @param sprintDetails
	 *            Sprint details
	 * @param context
	 *            Metric context with issues and histories
	 * @param groomingStatusSet
	 *            Set of grooming status values (lowercase)
	 * @param dayOne
	 *            Sprint start date (Day 1)
	 * @return Count of issues in grooming status
	 */
	private int countIssuesInGroomingOnDayOne(SprintDetails sprintDetails, SprintMetricContext context,
			Set<String> groomingStatusSet, LocalDate dayOne) {

		int count = 0;

		// Get all issue numbers from sprint
		Set<String> sprintIssueNumbers = sprintDetails.getTotalIssues().stream().map(SprintIssue::getNumber)
				.collect(Collectors.toSet());

		// For each issue in sprint, check if it was in Grooming on Day 1
		for (String issueNumber : sprintIssueNumbers) {
			// Get issue from context using fast lookup
			JiraIssue issue = context.getJiraIssueByNumber(issueNumber).orElse(null);
			if (issue == null) {
				continue;
			}

			// Get history for this issue
			JiraIssueCustomHistory history = context.getHistoryByStoryId(issue.getNumber()).orElse(null);
			if (history == null || CollectionUtils.isEmpty(history.getStatusUpdationLog())) {
				continue;
			}

			// Check if issue was in Grooming status on Day 1
			if (wasInGroomingOnDate(history.getStatusUpdationLog(), groomingStatusSet, dayOne)) {
				count++;
			}
		}

		return count;
	}

	/**
	 * Checks if an issue was in Grooming status on a specific date Logic: Traverse
	 * status update logs and find the status on the target date
	 *
	 * @param statusUpdationLog
	 *            List of status change logs
	 * @param groomingStatusSet
	 *            Set of grooming status values
	 * @param targetDate
	 *            Date to check (Day 1 of sprint)
	 * @return true if issue was in grooming status on target date
	 */
	private boolean wasInGroomingOnDate(List<JiraHistoryChangeLog> statusUpdationLog, Set<String> groomingStatusSet,
			LocalDate targetDate) {

		if (CollectionUtils.isEmpty(statusUpdationLog)) {
			return false;
		}

		// Sort logs by date (oldest first)
		List<JiraHistoryChangeLog> sortedLogs = statusUpdationLog.stream().filter(log -> log.getUpdatedOn() != null)
				.sorted(Comparator.comparing(JiraHistoryChangeLog::getUpdatedOn)).toList();

		String statusOnTargetDate = null;

		// Find the status on target date by traversing logs
		for (JiraHistoryChangeLog log : sortedLogs) {
			LocalDate logDate = log.getUpdatedOn().toLocalDate();

			// If log is before or on target date, update status
			if (logDate.isBefore(targetDate) || logDate.isEqual(targetDate)) {
				statusOnTargetDate = log.getChangedTo();
			} else {
				// Log is after target date, stop searching
				break;
			}
		}

		// If no status found (no logs before target date), check first log's
		// changedFrom
		if (statusOnTargetDate == null && !sortedLogs.isEmpty()) {
			JiraHistoryChangeLog firstLog = sortedLogs.get(0);
			if (firstLog.getUpdatedOn().toLocalDate().isAfter(targetDate)) {
				statusOnTargetDate = firstLog.getChangedFrom();
			}
		}

		// Check if the status was in grooming status set
		if (statusOnTargetDate != null) {
			return groomingStatusSet.contains(statusOnTargetDate.trim().toLowerCase());
		}

		return false;
	}
}
