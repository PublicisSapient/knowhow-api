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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.SprintDataPoint;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.enums.SprintMetricType;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.model.SprintMetricContext;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;

@RunWith(MockitoJUnitRunner.class)
public class SpilloverAgeStrategyTest {

	@InjectMocks private SpilloverAgeStrategy strategy;

	private SprintDetails sprintDetails;
	private SprintMetricContext context;
	private FieldMapping fieldMapping;

	@Before
	public void setUp() {
		sprintDetails = new SprintDetails();
		sprintDetails.setSprintID("sprint-1");
		sprintDetails.setSprintName("Sprint 1");
		sprintDetails.setStartDate("2024-01-01T00:00:00.000Z");
		sprintDetails.setEndDate("2024-01-15T23:59:59.999Z");
		sprintDetails.setState("CLOSED");

		fieldMapping = new FieldMapping();
		List<String> devDoneStatuses = new ArrayList<>();
		devDoneStatuses.add("Done");
		devDoneStatuses.add("Closed");
		fieldMapping.setJiraDevDoneStatusKPI128(devDoneStatuses);

		context = new SprintMetricContext();
		context.setProjectName("Test Project");
		context.setFieldMapping(fieldMapping);
	}

	@Test
	public void testGetMetricType() {
		assertEquals(SprintMetricType.SPILLOVER_AGE, strategy.getMetricType());
	}

	@Test
	public void testCalculateForSprint_NoFieldMapping() {
		context.setFieldMapping(null);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals(Constant.NOT_AVAILABLE, result.getValue());
	}

	@Test
	public void testCalculateForSprint_NoDevDoneStatuses() {
		fieldMapping.setJiraDevDoneStatusKPI128(null);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals(Constant.NOT_AVAILABLE, result.getValue());
	}

	@Test
	public void testCalculateForSprint_NoSpilloverIssues() {
		sprintDetails.setNotCompletedIssues(null);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals(Constant.NOT_AVAILABLE, result.getValue());
	}

	@Test
	public void testCalculateForSprint_WithSpilloverIssues() {
		Set<SprintIssue> notCompletedIssues = new HashSet<>();
		notCompletedIssues.add(createSprintIssue("ISSUE-1"));
		notCompletedIssues.add(createSprintIssue("ISSUE-2"));
		sprintDetails.setNotCompletedIssues(notCompletedIssues);

		Map<String, JiraIssueCustomHistory> historyMap = new HashMap<>();

		// ISSUE-1: Tagged on Jan 1, completed on Jan 10 (9 days)
		historyMap.put(
				"ISSUE-1",
				createHistoryWithSprintAndCompletion(
						LocalDateTime.of(2024, 1, 1, 10, 0), LocalDateTime.of(2024, 1, 10, 10, 0)));

		// ISSUE-2: Tagged on Jan 5, completed on Jan 14 (9 days)
		historyMap.put(
				"ISSUE-2",
				createHistoryWithSprintAndCompletion(
						LocalDateTime.of(2024, 1, 5, 10, 0), LocalDateTime.of(2024, 1, 14, 10, 0)));

		context.setHistoryMap(historyMap);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals("2", result.getValue()); // 2 spillover issues
		assertEquals("9.0", result.getTrend()); // Average age = (9 + 9) / 2 = 9 days
	}

	@Test
	public void testCalculateForSprint_SpilloverNotCompleted() {
		Set<SprintIssue> notCompletedIssues = new HashSet<>();
		notCompletedIssues.add(createSprintIssue("ISSUE-1"));
		sprintDetails.setNotCompletedIssues(notCompletedIssues);

		Map<String, JiraIssueCustomHistory> historyMap = new HashMap<>();

		// ISSUE-1: Tagged on Jan 1, not completed (age = today - Jan 1)
		historyMap.put("ISSUE-1", createHistoryWithSprintOnly(LocalDateTime.of(2024, 1, 1, 10, 0)));

		context.setHistoryMap(historyMap);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals("1", result.getValue());
		assertNotNull(result.getTrend()); // Age in days from Jan 1 to today
	}

	private SprintIssue createSprintIssue(String number) {
		SprintIssue issue = new SprintIssue();
		issue.setNumber(number);
		return issue;
	}

	private JiraIssueCustomHistory createHistoryWithSprintAndCompletion(
			LocalDateTime sprintTagDate, LocalDateTime completionDate) {
		JiraIssueCustomHistory history = new JiraIssueCustomHistory();

		// Sprint tagging log
		List<JiraHistoryChangeLog> sprintLogs = new ArrayList<>();
		JiraHistoryChangeLog sprintLog = new JiraHistoryChangeLog();
		sprintLog.setChangedFrom("");
		sprintLog.setChangedTo("Sprint 1");
		sprintLog.setUpdatedOn(sprintTagDate);
		sprintLogs.add(sprintLog);
		history.setSprintUpdationLog(sprintLogs);

		// Status update log (completion)
		List<JiraHistoryChangeLog> statusLogs = new ArrayList<>();
		JiraHistoryChangeLog statusLog = new JiraHistoryChangeLog();
		statusLog.setChangedFrom("In Progress");
		statusLog.setChangedTo("Done");
		statusLog.setUpdatedOn(completionDate);
		statusLogs.add(statusLog);
		history.setStatusUpdationLog(statusLogs);

		return history;
	}

	private JiraIssueCustomHistory createHistoryWithSprintOnly(LocalDateTime sprintTagDate) {
		JiraIssueCustomHistory history = new JiraIssueCustomHistory();

		// Sprint tagging log
		List<JiraHistoryChangeLog> sprintLogs = new ArrayList<>();
		JiraHistoryChangeLog sprintLog = new JiraHistoryChangeLog();
		sprintLog.setChangedFrom("");
		sprintLog.setChangedTo("Sprint 1");
		sprintLog.setUpdatedOn(sprintTagDate);
		sprintLogs.add(sprintLog);
		history.setSprintUpdationLog(sprintLogs);

		// No completion (empty status log)
		history.setStatusUpdationLog(new ArrayList<>());

		return history;
	}
}
