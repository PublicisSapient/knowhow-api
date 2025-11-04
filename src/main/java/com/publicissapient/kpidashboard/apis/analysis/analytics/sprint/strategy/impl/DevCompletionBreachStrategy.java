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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.publicissapient.kpidashboard.apis.constant.Constant;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.SprintDataPoint;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.enums.SprintMetricType;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.model.SprintMetricContext;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.strategy.AbstractSprintMetricStrategy;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Dev Completion Breach Strategy
 * <p>
 * Tracks stories that failed to reach "Dev Done" status by their configured Dev
 * Completion Date.
 * 
 * <h3>Calculation Logic:</h3>
 * <ul>
 * <li>Track stories not dev-completed by their Dev Completion Date</li>
 * <li>Uses devDueDates and dev done statuses from field mapping</li>
 * <li>Checks statusUpdationLog from JiraIssueCustomHistory for status
 * transitions</li>
 * </ul>
 * 
 * <h3>Formula:</h3>
 * 
 * <pre>
 *   Value = Count of stories NOT dev-completed by Dev Completion Date
 *   Trend = (Not dev-completed / Stories with dev due date) × 100
 * </pre>
 * 
 * <h3>Example:</h3> <blockquote> <b>10 stories</b> have Dev Completion Date
 * set<br>
 * <b>3 stories</b> did NOT reach "Dev Done" status by their due date<br>
 * <b>Value</b> = 3<br>
 * <b>Trend</b> = (3/10) × 100 = <b>30%</b> </blockquote>
 * 
 * @see SprintMetricType#DEV_COMPLETION_BREACH
 */
@Slf4j
@Component
public class DevCompletionBreachStrategy extends AbstractSprintMetricStrategy {

	@Override
	public SprintMetricType getMetricType() {
		return SprintMetricType.DEV_COMPLETION_BREACH;
	}

	@Override
	protected SprintDataPoint calculateForSprint(SprintDetails sprintDetails, SprintMetricContext context,
			int sprintIndex) {

		if (!isValidFieldMapping(context.getFieldMapping(), context.getProjectName())) {
			return createNADataPoint(sprintDetails, "Field mapping not configured", sprintIndex, context);
		}

		FieldMapping fieldMapping = context.getFieldMapping();

		// Get dev done statuses from field mapping
		List<String> devDoneStatuses = fieldMapping.getJiraDevDoneStatusKPI128();
		if (CollectionUtils.isEmpty(devDoneStatuses)) {
			log.warn("Dev done statuses not configured for project: {}", context.getProjectName());
			return createNADataPoint(sprintDetails, "Dev done statuses not configured in field mapping", sprintIndex, context);
		}

		// Get all issues with devDueDate from sprint
		List<JiraIssue> issuesWithDevDueDate = getIssuesWithDevDueDate(sprintDetails, context);

		int totalWithDevDueDate = issuesWithDevDueDate.size();

		if (totalWithDevDueDate == 0) {
			log.info("No issues with dev due date in sprint: {}", getSprintName(sprintDetails));
			return createNADataPoint(sprintDetails, "No issues with dev due date", sprintIndex, context);
		}

		// Count issues that breached dev completion date
		int breachCount = countDevCompletionBreach(issuesWithDevDueDate, sprintDetails, context, devDoneStatuses);

		// Calculate percentage: (breach / total) * 100
		double percentage = calculatePercentage(breachCount, totalWithDevDueDate);

		return createDataPoint(sprintDetails, breachCount, percentage, sprintIndex, Constant.PERCENTAGE);
	}

	/**
	 * Gets issues with devDueDate from sprint
	 *
	 * @param sprintDetails
	 *            Sprint details
	 * @param context
	 *            Metric context
	 * @return List of issues with devDueDate
	 */
	private List<JiraIssue> getIssuesWithDevDueDate(SprintDetails sprintDetails, SprintMetricContext context) {
		return sprintDetails.getTotalIssues().stream().map(SprintIssue::getNumber)
				.map(issueNumber -> context.getJiraIssueByNumber(issueNumber).orElse(null)).filter(Objects::nonNull)
				.filter(issue -> StringUtils.isNotEmpty(issue.getDevDueDate())).collect(Collectors.toList());
	}

	/**
	 * Counts issues that breached dev completion date Logic Active Sprint:
	 * devDueDate < today AND NOT dev-completed - Closed Sprint: devDueDate <=
	 * sprintEndDate AND NOT dev-completed
	 *
	 * @param issuesWithDevDueDate
	 *            Issues with dev due date
	 * @param sprintDetails
	 *            Sprint details
	 * @param context
	 *            Metric context
	 * @param devDoneStatuses
	 *            Dev done statuses from field mapping
	 * @return Count of breached issues
	 */
	private int countDevCompletionBreach(List<JiraIssue> issuesWithDevDueDate, SprintDetails sprintDetails,
			SprintMetricContext context, List<String> devDoneStatuses) {

		boolean isActiveSprint = SprintDetails.SPRINT_STATE_ACTIVE.equalsIgnoreCase(sprintDetails.getState());
		LocalDate today = LocalDate.now();
		LocalDate sprintEndDate = DateUtil.stringToLocalDate(sprintDetails.getEndDate(), DateUtil.TIME_FORMAT_WITH_SEC);

		return (int) issuesWithDevDueDate.stream().filter(issue -> {
			LocalDate devDueDate = DateUtil.stringToLocalDate(issue.getDevDueDate(), DateUtil.TIME_FORMAT_WITH_SEC);

			// Check if issue should be dev-completed by now
			boolean shouldBeCompleted = false;
			if (isActiveSprint) {
				// Active sprint: devDueDate < today
				shouldBeCompleted = devDueDate.isBefore(today);
			} else {
				// Closed sprint: devDueDate <= sprintEndDate
				shouldBeCompleted = devDueDate.isBefore(sprintEndDate.plusDays(1));
			}

			if (!shouldBeCompleted) {
				return false; // Not yet due
			}

			// Check if issue is dev-completed within sprint window
			boolean isDevCompleted = isDevCompleted(issue, context, devDoneStatuses, sprintDetails);

			// Breach if not dev-completed when required
			return !isDevCompleted;
		}).count();
	}

	/**
	 * Checks if issue is dev-completed Uses Find when issue reached dev-done status
	 * from statusUpdationLog
	 *
	 * @param issue
	 *            Jira issue
	 * @param context
	 *            Metric context
	 * @param devDoneStatuses
	 *            Dev done statuses
	 * @param sprintDetails
	 *            Sprint details for date window
	 * @return true if issue reached dev-done status within sprint window
	 */
	private boolean isDevCompleted(JiraIssue issue, SprintMetricContext context, List<String> devDoneStatuses,
			SprintDetails sprintDetails) {
		JiraIssueCustomHistory history = context.getHistoryByStoryId(issue.getNumber()).orElse(null);

		if (history == null || CollectionUtils.isEmpty(history.getStatusUpdationLog())) {
			return false;
		}

		LocalDateTime sprintStartDate = DateUtil.stringToLocalDateTime(sprintDetails.getStartDate(),
				DateUtil.TIME_FORMAT_WITH_SEC);
		LocalDateTime sprintEndDate = DateUtil.stringToLocalDateTime(sprintDetails.getEndDate(),
				DateUtil.TIME_FORMAT_WITH_SEC);

		// Check if issue reached dev-done status within sprint window
		String devCompleteDate = history.getStatusUpdationLog().stream()
				.filter(log -> devDoneStatuses.contains(log.getChangedTo()) && log.getUpdatedOn() != null)
				.map(JiraHistoryChangeLog::getUpdatedOn)
				.filter(updatedOn -> DateUtil.isWithinDateTimeRange(updatedOn, sprintStartDate, sprintEndDate))
				.max(Comparator.naturalOrder()).map(LocalDateTime::toString).orElse(CommonConstant.BLANK);

		return StringUtils.isNotEmpty(devCompleteDate) && !CommonConstant.BLANK.equals(devCompleteDate);
	}
}
