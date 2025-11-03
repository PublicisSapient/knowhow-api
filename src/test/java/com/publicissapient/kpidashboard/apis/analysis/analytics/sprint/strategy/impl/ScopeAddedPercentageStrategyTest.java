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

import java.util.HashSet;
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
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;

@RunWith(MockitoJUnitRunner.class)
public class ScopeAddedPercentageStrategyTest {

	@InjectMocks
	private ScopeAddedPercentageStrategy strategy;

	private SprintDetails sprintDetails;
	private SprintMetricContext context;

	@Before
	public void setUp() {
		sprintDetails = new SprintDetails();
		sprintDetails.setSprintID("sprint-1");
		sprintDetails.setSprintName("Sprint 1");
		sprintDetails.setStartDate("2024-01-01T00:00:00.000Z");
		sprintDetails.setEndDate("2024-01-15T23:59:59.999Z");
		sprintDetails.setState("CLOSED");

		context = new SprintMetricContext();
		context.setProjectName("Test Project");
	}

	@Test
	public void testGetMetricType() {
		assertEquals(SprintMetricType.SCOPE_ADDED_PERCENTAGE, strategy.getMetricType());
	}

	@Test
	public void testCalculateForSprint_NoTotalIssues() {
		sprintDetails.setTotalIssues(null);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals(Constant.NOT_AVAILABLE, result.getValue());
	}

	@Test
	public void testCalculateForSprint_WithAddedIssues() {
		Set<SprintIssue> totalIssues = new HashSet<>();
		for (int i = 1; i <= 20; i++) {
			totalIssues.add(createSprintIssue("ISSUE-" + i));
		}
		sprintDetails.setTotalIssues(totalIssues);

		Set<String> addedIssues = new HashSet<>();
		for (int i = 16; i <= 20; i++) {
			addedIssues.add("ISSUE-" + i);
		}
		sprintDetails.setAddedIssues(addedIssues);

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals("5", result.getValue());
		assertEquals("25.0", result.getTrend()); // (5/20) * 100 = 25%
	}

	@Test
	public void testCalculateForSprint_NoAddedIssues() {
		Set<SprintIssue> totalIssues = new HashSet<>();
		totalIssues.add(createSprintIssue("ISSUE-1"));
		sprintDetails.setTotalIssues(totalIssues);

		sprintDetails.setAddedIssues(null);

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
