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
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;

@RunWith(MockitoJUnitRunner.class)
public class DevCompletionBreachStrategyTest {

	@InjectMocks
	private DevCompletionBreachStrategy strategy;

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
		devDoneStatuses.add("Dev Done");
		devDoneStatuses.add("Ready for QA");
		fieldMapping.setJiraDevDoneStatusKPI128(devDoneStatuses);

		context = new SprintMetricContext();
		context.setProjectName("Test Project");
		context.setFieldMapping(fieldMapping);
	}

	@Test
	public void testGetMetricType() {
		assertEquals(SprintMetricType.DEV_COMPLETION_BREACH, strategy.getMetricType());
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
	public void testCalculateForSprint_NoIssuesWithDevDueDate() {
		Set<SprintIssue> totalIssues = new HashSet<>();
		totalIssues.add(createSprintIssue("ISSUE-1"));
		sprintDetails.setTotalIssues(totalIssues);

		Map<String, JiraIssue> issueMap = new HashMap<>();
		JiraIssue issue = createJiraIssue("ISSUE-1");
		issue.setDevDueDate(null); // No dev due date
		issueMap.put("ISSUE-1", issue);

		context.setJiraIssueMap(issueMap);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals(Constant.NOT_AVAILABLE, result.getValue());
	}

	@Test
	public void testCalculateForSprint_WithBreaches() {
		Set<SprintIssue> totalIssues = new HashSet<>();
		totalIssues.add(createSprintIssue("ISSUE-1"));
		totalIssues.add(createSprintIssue("ISSUE-2"));
		totalIssues.add(createSprintIssue("ISSUE-3"));
		sprintDetails.setTotalIssues(totalIssues);

		Map<String, JiraIssue> issueMap = new HashMap<>();
		Map<String, JiraIssueCustomHistory> historyMap = new HashMap<>();

		// ISSUE-1: Has dev due date, breached (not completed in sprint)
		JiraIssue issue1 = createJiraIssue("ISSUE-1");
		issue1.setDevDueDate("2024-01-10T00:00:00.000Z");
		issueMap.put("ISSUE-1", issue1);
		historyMap.put("ISSUE-1", createHistoryWithoutDevCompletion());

		// ISSUE-2: Has dev due date, completed in sprint
		JiraIssue issue2 = createJiraIssue("ISSUE-2");
		issue2.setDevDueDate("2024-01-10T00:00:00.000Z");
		issueMap.put("ISSUE-2", issue2);
		historyMap.put("ISSUE-2", createHistoryWithDevCompletion());

		// ISSUE-3: Has dev due date, breached
		JiraIssue issue3 = createJiraIssue("ISSUE-3");
		issue3.setDevDueDate("2024-01-12T00:00:00.000Z");
		issueMap.put("ISSUE-3", issue3);
		historyMap.put("ISSUE-3", createHistoryWithoutDevCompletion());

		context.setJiraIssueMap(issueMap);
		context.setHistoryMap(historyMap);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals("2", result.getValue()); // 2 breaches out of 3
		assertEquals("66.67", result.getTrend()); // (2/3) * 100 = 66.67%
	}

	@Test
	public void testCalculateForSprint_NoBreaches() {
		Set<SprintIssue> totalIssues = new HashSet<>();
		totalIssues.add(createSprintIssue("ISSUE-1"));
		sprintDetails.setTotalIssues(totalIssues);

		Map<String, JiraIssue> issueMap = new HashMap<>();
		Map<String, JiraIssueCustomHistory> historyMap = new HashMap<>();

		JiraIssue issue = createJiraIssue("ISSUE-1");
		issue.setDevDueDate("2024-01-10T00:00:00.000Z");
		issueMap.put("ISSUE-1", issue);
		historyMap.put("ISSUE-1", createHistoryWithDevCompletion());

		context.setJiraIssueMap(issueMap);
		context.setHistoryMap(historyMap);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals("0", result.getValue());
		assertEquals("0.0", result.getTrend());
	}

	private SprintIssue createSprintIssue(String number) {
		SprintIssue issue = new SprintIssue();
		issue.setNumber(number);
		return issue;
	}

	private JiraIssue createJiraIssue(String number) {
		JiraIssue issue = new JiraIssue();
		issue.setNumber(number);
		return issue;
	}

	private JiraIssueCustomHistory createHistoryWithDevCompletion() {
		JiraIssueCustomHistory history = new JiraIssueCustomHistory();
		List<JiraHistoryChangeLog> logs = new ArrayList<>();

		// Status changed to Dev Done within sprint
		JiraHistoryChangeLog log = new JiraHistoryChangeLog();
		log.setChangedFrom("In Progress");
		log.setChangedTo("Dev Done");
		log.setUpdatedOn(LocalDateTime.of(2024, 1, 11, 10, 0));
		logs.add(log);

		history.setStatusUpdationLog(logs);
		return history;
	}

	private JiraIssueCustomHistory createHistoryWithoutDevCompletion() {
		JiraIssueCustomHistory history = new JiraIssueCustomHistory();
		List<JiraHistoryChangeLog> logs = new ArrayList<>();

		// Status changed but not to Dev Done
		JiraHistoryChangeLog log = new JiraHistoryChangeLog();
		log.setChangedFrom("To Do");
		log.setChangedTo("In Progress");
		log.setUpdatedOn(LocalDateTime.of(2024, 1, 5, 10, 0));
		logs.add(log);

		history.setStatusUpdationLog(logs);
		return history;
	}
}
