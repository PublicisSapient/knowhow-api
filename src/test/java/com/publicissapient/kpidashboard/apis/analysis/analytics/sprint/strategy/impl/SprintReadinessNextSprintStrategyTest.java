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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.SprintDataPoint;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.enums.SprintMetricType;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.model.SprintMetricContext;
import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;

@ExtendWith(MockitoExtension.class)
class SprintReadinessNextSprintStrategyTest {

	@Mock private ConfigHelperService configHelperService;

	@Mock private SprintRepository sprintRepository;

	@Mock private JiraIssueRepository jiraIssueRepository;

	@InjectMocks private SprintReadinessNextSprintStrategy strategy;

	private SprintDetails sprintDetails;
	private SprintMetricContext context;
	private FieldMapping fieldMapping;

	@BeforeEach
	void setUp() {
		sprintDetails = new SprintDetails();
		sprintDetails.setSprintID("sprint1");

		context = new SprintMetricContext();
		context.setBasicProjectConfigId(new ObjectId("507f1f77bcf86cd799439011"));

		fieldMapping = new FieldMapping();
		fieldMapping.setJiraIssueTypeNamesKPI188(Arrays.asList("Story", "Bug"));
	}

	@Test
	void shouldReturnNullForSprintIndexGreaterThanZero() {
		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 1);

		assertNull(result);
	}

	@Test
	void shouldReturnNAWhenNoFieldMapping() {
		when(configHelperService.getFieldMappingMap())
				.thenReturn(Map.of(new ObjectId("507f1f77bcf86cd799439011"), fieldMapping));
		fieldMapping.setJiraIssueTypeNamesKPI188(Collections.emptyList());

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals("NA", result.getValue());
		assertEquals("NA", result.getTrend());
	}

	@Test
	void shouldReturnNAWhenNoFutureSprints() {
		when(configHelperService.getFieldMappingMap())
				.thenReturn(Map.of(new ObjectId("507f1f77bcf86cd799439011"), fieldMapping));
		when(sprintRepository.findByBasicProjectConfigIdAndStateIgnoreCaseOrderByStartDateASC(
						new ObjectId("507f1f77bcf86cd799439011"), SprintDetails.SPRINT_STATE_FUTURE))
				.thenReturn(Collections.emptyList());

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals("NA", result.getValue());
		assertEquals("NA", result.getTrend());
	}

	@Test
	void shouldReturnNAWhenNoIssuesInNextSprint() {
		SprintDetails nextSprint = new SprintDetails();
		nextSprint.setSprintID("nextSprint");

		when(configHelperService.getFieldMappingMap())
				.thenReturn(Map.of(new ObjectId("507f1f77bcf86cd799439011"), fieldMapping));
		when(sprintRepository.findByBasicProjectConfigIdAndStateIgnoreCaseOrderByStartDateASC(
						new ObjectId("507f1f77bcf86cd799439011"), SprintDetails.SPRINT_STATE_FUTURE))
				.thenReturn(Arrays.asList(nextSprint));
		when(jiraIssueRepository.findBySprintID("nextSprint")).thenReturn(Collections.emptyList());

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals("NA", result.getValue());
		assertEquals("NA", result.getTrend());
	}

	@Test
	void shouldCalculateCorrectPercentageWithUnrefinedIssues() {
		SprintDetails nextSprint = new SprintDetails();
		nextSprint.setSprintID("nextSprint");

		JiraIssue issue1 = createJiraIssue("story", Set.of("unrefined"));
		JiraIssue issue2 = createJiraIssue("story", Collections.emptySet());
		JiraIssue issue3 = createJiraIssue("story", Set.of("unrefined"));

		when(configHelperService.getFieldMappingMap())
				.thenReturn(Map.of(new ObjectId("507f1f77bcf86cd799439011"), fieldMapping));
		when(sprintRepository.findByBasicProjectConfigIdAndStateIgnoreCaseOrderByStartDateASC(
						new ObjectId("507f1f77bcf86cd799439011"), SprintDetails.SPRINT_STATE_FUTURE))
				.thenReturn(Arrays.asList(nextSprint));
		when(jiraIssueRepository.findBySprintID("nextSprint"))
				.thenReturn(Arrays.asList(issue1, issue2, issue3));

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals("2", result.getValue());
		assertEquals("66.67", result.getTrend());
	}

	@Test
	void shouldHandleDefectTypeMapping() {
		fieldMapping.setJiraIssueTypeNamesKPI188(Arrays.asList("Defect"));
		SprintDetails nextSprint = new SprintDetails();
		nextSprint.setSprintID("nextSprint");

		JiraIssue bugIssue = createJiraIssue("bug", Set.of("unrefined"));
		JiraIssue defectIssue = createJiraIssue("defect", Collections.emptySet());

		when(configHelperService.getFieldMappingMap())
				.thenReturn(Map.of(new ObjectId("507f1f77bcf86cd799439011"), fieldMapping));
		when(sprintRepository.findByBasicProjectConfigIdAndStateIgnoreCaseOrderByStartDateASC(
						new ObjectId("507f1f77bcf86cd799439011"), SprintDetails.SPRINT_STATE_FUTURE))
				.thenReturn(Arrays.asList(nextSprint));
		when(jiraIssueRepository.findBySprintID("nextSprint"))
				.thenReturn(Arrays.asList(bugIssue, defectIssue));

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals("1", result.getValue());
		assertEquals("50.0", result.getTrend());
	}

	@Test
	void shouldReturnNAOnException() {
		when(configHelperService.getFieldMappingMap())
				.thenThrow(new RuntimeException("Database error"));

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals("NA", result.getValue());
		assertEquals("NA", result.getTrend());
	}

	@Test
	void shouldReturnCorrectMetricType() {
		assertEquals(SprintMetricType.SPRINT_READINESS_NEXT_SPRINT, strategy.getMetricType());
	}

	@Test
	void shouldHandleAllRefinedIssues() {
		SprintDetails nextSprint = new SprintDetails();
		nextSprint.setSprintID("nextSprint");

		JiraIssue issue1 = createJiraIssue("story", Collections.emptySet());
		JiraIssue issue2 = createJiraIssue("story", Collections.emptySet());

		when(configHelperService.getFieldMappingMap())
				.thenReturn(Map.of(new ObjectId("507f1f77bcf86cd799439011"), fieldMapping));
		when(sprintRepository.findByBasicProjectConfigIdAndStateIgnoreCaseOrderByStartDateASC(
						new ObjectId("507f1f77bcf86cd799439011"), SprintDetails.SPRINT_STATE_FUTURE))
				.thenReturn(Arrays.asList(nextSprint));
		when(jiraIssueRepository.findBySprintID("nextSprint"))
				.thenReturn(Arrays.asList(issue1, issue2));

		SprintDataPoint result = strategy.calculateForSprint(sprintDetails, context, 0);

		assertNotNull(result);
		assertEquals("0", result.getValue());
		assertEquals("0.0", result.getTrend());
	}

	private JiraIssue createJiraIssue(String typeName, Set<String> unrefinedValues) {
		JiraIssue issue = new JiraIssue();
		issue.setTypeName(typeName);
		issue.setUnRefinedValue188(unrefinedValues);
		return issue;
	}
}
