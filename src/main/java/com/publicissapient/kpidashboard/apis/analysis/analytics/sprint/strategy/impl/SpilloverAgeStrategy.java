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
import static com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.util.SprintAnalyticsUtil.isValidFieldMapping;
import static com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.util.SprintAnalyticsUtil.roundingOff;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.SprintDataPoint;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.enums.SprintMetricType;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.model.SprintMetricContext;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.strategy.AbstractSprintMetricStrategy;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;

import lombok.extern.slf4j.Slf4j;

/**
 * Spillover Age Strategy
 * <p>
 * Calculates the age of spillover stories (stories that were not completed in
 * the sprint).
 * 
 * <h3>Calculation Logic:</h3>
 * <ul>
 * <li>Calculate age of spillover stories (not completed in sprint)</li>
 * <li>Age = Days from FIRST sprint tagging until DONE (or TODAY if
 * incomplete)</li>
 * <li>Uses sprintUpdationLog from JiraIssueCustomHistory to find first sprint
 * tag</li>
 * </ul>
 * 
 * <h3>Formula:</h3>
 * 
 * <pre>
 *   Age = Completion Date (or Today) - First Sprint Tag Date
 *   Value = Count of spillover issues
 *   Trend = Average age in days
 * </pre>
 * 
 * <h3>Example:</h3> <blockquote> Story-1: Tagged Jan 1, Completed Jan 15 → Age
 * = <b>14 days</b><br>
 * Story-2: Tagged Jan 5, Still open (Today Jan 20) → Age = <b>15 days</b><br>
 * <b>Value</b> = 2 spillover stories<br>
 * <b>Trend</b> = (14 + 15) / 2 = <b>14.5 days</b> average </blockquote>
 * 
 * @see SprintMetricType#SPILLOVER_AGE
 */
@Slf4j
@Component
public class SpilloverAgeStrategy extends AbstractSprintMetricStrategy {

	@Override
	public SprintMetricType getMetricType() {
		return SprintMetricType.SPILLOVER_AGE;
	}

	@Override
	protected SprintDataPoint calculateForSprint(SprintDetails sprintDetails, SprintMetricContext context,
			int sprintIndex) {
		if (!isValidFieldMapping(context.getFieldMapping(), context.getProjectName())) {
			return createNADataPoint(sprintDetails, "Field mapping not configured", sprintIndex, context);
		}

		FieldMapping fieldMapping = context.getFieldMapping();

		// Get dev done statuses for completion check
		List<String> devDoneStatuses = fieldMapping.getJiraDevDoneStatusKPI128();
		if (CollectionUtils.isEmpty(devDoneStatuses)) {
			log.warn("Dev done statuses not configured for project: {}", context.getProjectName());
			return createNADataPoint(sprintDetails, "Dev done statuses not configured in field mapping", sprintIndex, context);
		}

		// Get spillover issues (not completed in current sprint)
		Set<SprintIssue> spilloverIssues = getSpilloverIssues(sprintDetails);

		int spilloverCount = spilloverIssues.size();

		if (spilloverCount == 0) {
			log.info("No spillover issues in sprint: {}", sprintDetails.getSprintName());
			return createNADataPoint(sprintDetails, "No spillover issues", sprintIndex, context);
		}

		// Calculate average age of spillover issues
		double averageAge = calculateAverageSpilloverAge(spilloverIssues, sprintDetails, context, devDoneStatuses);

		return createDataPoint(sprintDetails, spilloverCount, averageAge, sprintIndex);
	}

	/**
	 * Gets spillover issues from sprint (issues not completed) Uses SprintIssue
	 * directly from SprintDetails
	 *
	 * @param sprintDetails
	 *            Sprint details
	 * @return Set of spillover SprintIssues
	 */
	private Set<SprintIssue> getSpilloverIssues(SprintDetails sprintDetails) {
		if (sprintDetails.getNotCompletedIssues() == null) {
			return Set.of();
		}

		return sprintDetails.getNotCompletedIssues();
	}

	/**
	 * Calculates average age of spillover issues in days Age = Days from FIRST
	 * sprint tagging until completion (or today if not done)
	 *
	 * @param spilloverIssues
	 *            List of spillover SprintIssues
	 * @param sprintDetails
	 *            Sprint details for current sprint
	 * @param context
	 *            Metric context
	 * @param devDoneStatuses
	 *            Dev done statuses for completion check
	 * @return Average age in days
	 */
	private double calculateAverageSpilloverAge(Set<SprintIssue> spilloverIssues, SprintDetails sprintDetails,
			SprintMetricContext context, List<String> devDoneStatuses) {

		long totalAge = 0;
		int validIssueCount = 0;

		for (SprintIssue sprintIssue : spilloverIssues) {
			Long age = calculateIssueAge(sprintIssue.getNumber(), sprintDetails, context, devDoneStatuses);
			if (age != null && age >= 0) {
				totalAge += age;
				validIssueCount++;
			}
		}

		return validIssueCount > 0 ? roundingOff((double) totalAge / validIssueCount) : 0.0;
	}

	/**
	 * Calculates age of a single spillover issue Age = Days from FIRST sprint
	 * tagging until completion (or today)
	 *
	 * @param storyId
	 *            Story ID (from SprintIssue.getNumber())
	 * @param sprintDetails
	 *            Sprint details for current sprint
	 * @param context
	 *            Metric context
	 * @param devDoneStatuses
	 *            Dev done statuses
	 * @return Age in days, or null if cannot be calculated
	 */
	private Long calculateIssueAge(String storyId, SprintDetails sprintDetails, SprintMetricContext context,
			List<String> devDoneStatuses) {
		JiraIssueCustomHistory history = context.getHistoryByStoryId(storyId).orElse(null);

		if (history == null || CollectionUtils.isEmpty(history.getSprintUpdationLog())) {
			return null;
		}

		// Find FIRST sprint tagging date for this sprint
		LocalDateTime firstSprintTagDate = getFirstSprintTaggingDate(history, sprintDetails);
		if (firstSprintTagDate == null) {
			return null;
		}

		// Find end date (completion date or today)
		LocalDateTime endDate = getEndDate(history, devDoneStatuses);

		// Calculate age in days
		return ChronoUnit.DAYS.between(firstSprintTagDate.toLocalDate(), endDate.toLocalDate());
	}

	/**
	 * Finds the FIRST time issue was tagged to the current sprint Uses
	 * sprintUpdationLog to find the earliest tagging date for this specific sprint
	 *
	 * @param history
	 *            Issue history
	 * @param sprintDetails
	 *            Sprint details for current sprint
	 * @return First sprint tagging date for this sprint, or null if not found
	 */
	private LocalDateTime getFirstSprintTaggingDate(JiraIssueCustomHistory history, SprintDetails sprintDetails) {
		if (CollectionUtils.isEmpty(history.getSprintUpdationLog())) {
			return null;
		}

		String currentSprintName = sprintDetails.getSprintName();

		// Find the earliest log where issue was tagged TO this specific sprint
		return history.getSprintUpdationLog().stream().filter(log -> log.getUpdatedOn() != null)
				.filter(log -> StringUtils.isNotEmpty(log.getChangedTo()))
				.filter(log -> log.getChangedTo().equalsIgnoreCase(currentSprintName))
				.map(JiraHistoryChangeLog::getUpdatedOn).min(Comparator.naturalOrder()).orElse(null);
	}

	/**
	 * Finds end date for age calculation If issue is completed (reached dev-done
	 * status), use completion date Otherwise, use current date (today)
	 *
	 * @param history
	 *            Issue history
	 * @param devDoneStatuses
	 *            Dev done statuses
	 * @return End date for age calculation
	 */
	private LocalDateTime getEndDate(JiraIssueCustomHistory history, List<String> devDoneStatuses) {
		// Check if issue is completed (reached dev-done status)
		LocalDateTime completionDate = getCompletionDate(history, devDoneStatuses);

		if (completionDate != null) {
			return completionDate;
		}

		// Issue not completed - use today
		return LocalDate.now().atStartOfDay();
	}

	/**
	 * Gets completion date from statusUpdationLog Logic from
	 * DevCompletionBreachStrategy/WorkStatusServiceImpl
	 *
	 * @param history
	 *            Issue history
	 * @param devDoneStatuses
	 *            Dev done statuses
	 * @return Completion date, or null if not completed
	 */
	private LocalDateTime getCompletionDate(JiraIssueCustomHistory history, List<String> devDoneStatuses) {
		if (CollectionUtils.isEmpty(history.getStatusUpdationLog())) {
			return null;
		}

		// Find when issue reached dev-done status (max date)
		return history.getStatusUpdationLog().stream()
				.filter(log -> devDoneStatuses.contains(log.getChangedTo()) && log.getUpdatedOn() != null)
				.map(JiraHistoryChangeLog::getUpdatedOn).min(Comparator.naturalOrder()).orElse(null);
	}
}
