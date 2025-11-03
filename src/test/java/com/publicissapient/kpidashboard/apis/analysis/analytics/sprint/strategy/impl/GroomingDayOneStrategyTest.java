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
public class GroomingDayOneStrategyTest {

	@InjectMocks
	private GroomingDayOneStrategy strategy;

	private SprintDetails sprintDetails;
	private SprintMetricContext context;
	private FieldMapping fieldMapping;

	@Before
	public void setUp() {
		// Setup sprint details
		sprintDetails = new SprintDetails();
		sprintDetails.setSprintID("sprint-1");
		sprintDetails.setSprintName("Sprint 1");
		sprintDetails.setStartDate("2024-01-01T00:00:00.000Z");
		sprintDetails.setEndDate("2024-01-15T23:59:59.999Z");
		sprintDetails.setState("CLOSED");

		// Setup field mapping
		fieldMapping = new FieldMapping();
		List<String> groomingStatuses = new ArrayList<>();
		groomingStatuses.add("Grooming");
		groomingStatuses.add("Backlog");
		fieldMapping.setJiraStatusKPI187(groomingStatuses);

		// Setup context
		context = new SprintMetricContext();
		context.setProjectName("Test Project");
		context.setFieldMapping(fieldMapping);
	}

	@Test
	public void testGetMetricType() {
		assertEquals(SprintMetricType.GROOMING_DAY_ONE, strategy.getMetricType());
	}

	@Test
	public void testCalculateForSprint_NoFieldMapping() {
		context.setFieldMapping(null);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals(Constant.NOT_AVAILABLE, result.getValue());
	}

	@Test
	public void testCalculateForSprint_NoGroomingStatuses() {
		fieldMapping.setJiraStatusKPI187(null);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals(Constant.NOT_AVAILABLE, result.getValue());
	}

	@Test
	public void testCalculateForSprint_NoTotalIssues() {
		sprintDetails.setTotalIssues(null);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals(Constant.NOT_AVAILABLE, result.getValue());
	}

	@Test
	public void testCalculateForSprint_WithGroomingIssues() {
		// Setup sprint issues
		Set<SprintIssue> totalIssues = new HashSet<>();
		totalIssues.add(createSprintIssue("ISSUE-1"));
		totalIssues.add(createSprintIssue("ISSUE-2"));
		totalIssues.add(createSprintIssue("ISSUE-3"));
		sprintDetails.setTotalIssues(totalIssues);

		// Setup issue and history maps
		Map<String, JiraIssue> issueMap = new HashMap<>();
		Map<String, JiraIssueCustomHistory> historyMap = new HashMap<>();

		// ISSUE-1: In Grooming on Day 1
		issueMap.put("ISSUE-1", createJiraIssue("ISSUE-1"));
		historyMap.put("ISSUE-1", createHistoryWithGroomingOnDayOne("Grooming"));

		// ISSUE-2: Not in Grooming on Day 1
		issueMap.put("ISSUE-2", createJiraIssue("ISSUE-2"));
		historyMap.put("ISSUE-2", createHistoryWithoutGrooming());

		// ISSUE-3: In Backlog on Day 1
		issueMap.put("ISSUE-3", createJiraIssue("ISSUE-3"));
		historyMap.put("ISSUE-3", createHistoryWithGroomingOnDayOne("Backlog"));

		context.setJiraIssueMap(issueMap);
		context.setHistoryMap(historyMap);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals("2", result.getValue()); // 2 issues in grooming
		assertEquals("66.67", result.getTrend()); // (2/3) * 100 = 66.67%
	}

	@Test
	public void testCalculateForSprint_NoGroomingIssues() {
		Set<SprintIssue> totalIssues = new HashSet<>();
		totalIssues.add(createSprintIssue("ISSUE-1"));
		sprintDetails.setTotalIssues(totalIssues);

		Map<String, JiraIssue> issueMap = new HashMap<>();
		Map<String, JiraIssueCustomHistory> historyMap = new HashMap<>();

		issueMap.put("ISSUE-1", createJiraIssue("ISSUE-1"));
		historyMap.put("ISSUE-1", createHistoryWithoutGrooming());

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

	private JiraIssueCustomHistory createHistoryWithGroomingOnDayOne(String status) {
		JiraIssueCustomHistory history = new JiraIssueCustomHistory();
		List<JiraHistoryChangeLog> logs = new ArrayList<>();

		// Status change before Day 1 to Grooming
		JiraHistoryChangeLog log = new JiraHistoryChangeLog();
		log.setChangedFrom("To Do");
		log.setChangedTo(status);
		log.setUpdatedOn(LocalDateTime.of(2023, 12, 31, 10, 0));
		logs.add(log);

		history.setStatusUpdationLog(logs);
		return history;
	}

	private JiraIssueCustomHistory createHistoryWithoutGrooming() {
		JiraIssueCustomHistory history = new JiraIssueCustomHistory();
		List<JiraHistoryChangeLog> logs = new ArrayList<>();

		// Status change to In Progress
		JiraHistoryChangeLog log = new JiraHistoryChangeLog();
		log.setChangedFrom("To Do");
		log.setChangedTo("In Progress");
		log.setUpdatedOn(LocalDateTime.of(2023, 12, 31, 10, 0));
		logs.add(log);

		history.setStatusUpdationLog(logs);
		return history;
	}
}
