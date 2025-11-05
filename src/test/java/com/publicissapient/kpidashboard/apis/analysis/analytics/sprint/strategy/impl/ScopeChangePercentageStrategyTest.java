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

import java.util.ArrayList;
import java.util.Arrays;
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
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;

@RunWith(MockitoJUnitRunner.class)
public class ScopeChangePercentageStrategyTest {

	@InjectMocks private ScopeChangePercentageStrategy strategy;

	private SprintDetails sprintDetails;
	private SprintMetricContext context;
	private FieldMapping fieldMapping;

	@Before
	public void setUp() {
		sprintDetails = new SprintDetails();
		sprintDetails.setSprintID("sprint-1");
		sprintDetails.setSprintName("Sprint 1");

		// Setup field mapping with scope change labels
		fieldMapping = new FieldMapping();
		List<String> scopeChangeLabels = Arrays.asList("Scope Change", "Requirement Change");
		fieldMapping.setJiraLabelsKPI120(scopeChangeLabels);

		context = new SprintMetricContext();
		context.setProjectName("Test Project");
		context.setFieldMapping(fieldMapping);
	}

	@Test
	public void testGetMetricType() {
		assertEquals(SprintMetricType.SCOPE_CHANGE_PERCENTAGE, strategy.getMetricType());
	}

	@Test
	public void testCalculateForSprint_NoFieldMapping() {
		context.setFieldMapping(null);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals(Constant.NOT_AVAILABLE, result.getValue());
	}

	@Test
	public void testCalculateForSprint_NoScopeChangeLabels() {
		fieldMapping.setJiraLabelsKPI120(null);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals(Constant.NOT_AVAILABLE, result.getValue());
	}

	@Test
	public void testCalculateForSprint_EmptyScopeChangeLabels() {
		fieldMapping.setJiraLabelsKPI120(new ArrayList<>());

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
	public void testCalculateForSprint_WithScopeChangeIssues() {
		Set<SprintIssue> totalIssues = new HashSet<>();
		for (int i = 1; i <= 20; i++) {
			totalIssues.add(createSprintIssue("ISSUE-" + i));
		}
		sprintDetails.setTotalIssues(totalIssues);

		// Setup Jira issues with scope change labels for first 4 issues
		Map<String, JiraIssue> issueMap = new HashMap<>();
		for (int i = 1; i <= 20; i++) {
			JiraIssue jiraIssue = new JiraIssue();
			jiraIssue.setNumber("ISSUE-" + i);
			if (i <= 4) {
				// First 4 issues have scope change labels
				jiraIssue.setLabels(Arrays.asList("Scope Change"));
			} else {
				// Other issues have different labels
				jiraIssue.setLabels(Arrays.asList("Bug"));
			}
			issueMap.put("ISSUE-" + i, jiraIssue);
		}
		context.setJiraIssueMap(issueMap);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals("4", result.getValue());
		assertEquals("20.0", result.getTrend()); // (4/20) * 100 = 20%
	}

	@Test
	public void testCalculateForSprint_NoScopeChangeIssues() {
		Set<SprintIssue> totalIssues = new HashSet<>();
		totalIssues.add(createSprintIssue("ISSUE-1"));
		sprintDetails.setTotalIssues(totalIssues);

		// Setup Jira issue without scope change labels
		Map<String, JiraIssue> issueMap = new HashMap<>();
		JiraIssue jiraIssue = new JiraIssue();
		jiraIssue.setNumber("ISSUE-1");
		jiraIssue.setLabels(Arrays.asList("Bug"));
		issueMap.put("ISSUE-1", jiraIssue);
		context.setJiraIssueMap(issueMap);

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
}
