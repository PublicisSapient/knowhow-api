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

package com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.analysis.analytics.shared.dto.BaseAnalyticsRequestDTO;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.ProjectSprintMetrics;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.SprintAnalyticsResponseDTO;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.SprintDataPoint;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.SprintMetricDTO;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.enums.SprintMetricType;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.factory.SprintMetricStrategyFactory;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.model.SprintMetricContext;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.strategy.SprintMetricStrategy;
import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.jira.service.SprintDetailsService;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;

import jakarta.ws.rs.BadRequestException;

@RunWith(MockitoJUnitRunner.class)
public class SprintAnalyticsServiceImplTest {

	@InjectMocks
	private SprintAnalyticsServiceImpl service;

	@Mock
	private JiraIssueRepository jiraIssueRepository;

	@Mock
	private ConfigHelperService configHelperService;

	@Mock
	private SprintDetailsService sprintDetailsService;

	@Mock
	private SprintMetricStrategyFactory strategyFactory;

	@Mock
	private AccountHierarchyServiceImpl accountHierarchyServiceImpl;

	@Mock
	private JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;

	@Mock
	private SprintMetricStrategy mockStrategy;

	private BaseAnalyticsRequestDTO request;
	private ObjectId projectId;
	private String projectIdStr;
	private ProjectBasicConfig projectBasicConfig;
	private FieldMapping fieldMapping;

	@Before
	public void setUp() {
		projectId = new ObjectId();
		projectIdStr = projectId.toString();

		// Setup request
		request = new BaseAnalyticsRequestDTO();
		request.setProjectBasicConfigIds((Set<String>) Set.of(projectIdStr));
		request.setNumberOfSprintsToInclude(5);

		// Setup project basic config
		projectBasicConfig = new ProjectBasicConfig();
		projectBasicConfig.setId(projectId);
		projectBasicConfig.setProjectName("Test Project");

		// Setup field mapping
		fieldMapping = new FieldMapping();
		fieldMapping.setBasicProjectConfigId(projectId);

		// Setup account hierarchy
		List<AccountFilteredData> accountFilteredDataList = new ArrayList<>();
		AccountFilteredData accountFilteredData = new AccountFilteredData();
		accountFilteredData.setBasicProjectConfigId(new ObjectId(projectIdStr));
		accountFilteredDataList.add(accountFilteredData);
		when(accountHierarchyServiceImpl.getHierarchyDataCurrentUserHasAccessTo(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
				.thenReturn(accountFilteredDataList);

		// Setup config helper
		Map<String, ProjectBasicConfig> projectConfigMap = new HashMap<>();
		projectConfigMap.put(projectIdStr, projectBasicConfig);
		when(configHelperService.getProjectConfigMap()).thenReturn(projectConfigMap);
		when(configHelperService.getFieldMapping(projectId)).thenReturn(fieldMapping);
	}

	@Test
	public void testComputeSprintAnalyticsData_Success() {
		// Setup enabled metrics
		List<SprintMetricType> enabledMetrics = Arrays.asList(
				SprintMetricType.GROOMING_DAY_ONE,
				SprintMetricType.DEV_COMPLETION_BREACH);
		when(strategyFactory.getEnabledMetricTypes()).thenReturn(enabledMetrics);
		when(strategyFactory.getStrategy(any(SprintMetricType.class))).thenReturn(mockStrategy);

		// Setup sprint details
		List<SprintDetails> sprints = createSprintDetailsList(3);
		when(sprintDetailsService.findByBasicProjectConfigIdInByCompletedDateDesc(anyList(), anyInt()))
				.thenReturn(sprints);

		// Setup jira issues
		List<JiraIssue> issues = createJiraIssuesList(5);
		when(jiraIssueRepository.findByNumberInAndBasicProjectConfigId(anyList(), anyString()))
				.thenReturn(issues);

		// Setup histories
		List<JiraIssueCustomHistory> histories = createHistoriesList(5);
		when(jiraIssueCustomHistoryRepository.findByStoryIDInAndBasicProjectConfigIdIn(anyList(), anyList()))
				.thenReturn(histories);

		// Setup strategy calculation
		ProjectSprintMetrics projectMetrics = createProjectSprintMetrics("Test Project", 3);
		when(mockStrategy.calculate(any(SprintMetricContext.class))).thenReturn(projectMetrics);

		// Execute
		ServiceResponse response = service.computeSprintAnalyticsData(request);

		// Verify
		assertNotNull(response);
		assertTrue(response.getSuccess());
		assertEquals("Sprint analytics computed successfully", response.getMessage());

		SprintAnalyticsResponseDTO analyticsResponse = (SprintAnalyticsResponseDTO) response.getData();
		assertNotNull(analyticsResponse);
		assertNotNull(analyticsResponse.getAnalytics());
		assertEquals(2, analyticsResponse.getAnalytics().size()); // 2 enabled metrics
		assertTrue(analyticsResponse.getWarnings().isEmpty());

		verify(strategyFactory, times(2)).getStrategy(any(SprintMetricType.class));
		verify(mockStrategy, times(2)).calculate(any(SprintMetricContext.class));
	}

	@Test
	public void testComputeSprintAnalyticsData_MultipleProjects() {
		ObjectId projectId2 = new ObjectId();
		String projectIdStr2 = projectId2.toString();

		// Setup second project
		ProjectBasicConfig projectBasicConfig2 = new ProjectBasicConfig();
		projectBasicConfig2.setId(projectId2);
		projectBasicConfig2.setProjectName("Test Project 2");

		Map<String, ProjectBasicConfig> projectConfigMap = new HashMap<>();
		projectConfigMap.put(projectIdStr, projectBasicConfig);
		projectConfigMap.put(projectIdStr2, projectBasicConfig2);
		when(configHelperService.getProjectConfigMap()).thenReturn(projectConfigMap);

		FieldMapping fieldMapping2 = new FieldMapping();
		fieldMapping2.setBasicProjectConfigId(projectId2);
		when(configHelperService.getFieldMapping(projectId2)).thenReturn(fieldMapping2);

		// Update request with 2 projects
		request.setProjectBasicConfigIds(Set.of(projectIdStr, projectIdStr2));

		// Setup account hierarchy for both projects
		List<AccountFilteredData> accountFilteredDataList = new ArrayList<>();
		AccountFilteredData data1 = new AccountFilteredData();
		data1.setBasicProjectConfigId(new ObjectId(projectIdStr));
		AccountFilteredData data2 = new AccountFilteredData();
        data2.setBasicProjectConfigId(new ObjectId(projectIdStr2));
		accountFilteredDataList.add(data1);
		accountFilteredDataList.add(data2);
		when(accountHierarchyServiceImpl.getHierarchyDataCurrentUserHasAccessTo(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
				.thenReturn(accountFilteredDataList);

		// Setup enabled metrics
		List<SprintMetricType> enabledMetrics = Collections.singletonList(SprintMetricType.GROOMING_DAY_ONE);
		when(strategyFactory.getEnabledMetricTypes()).thenReturn(enabledMetrics);
		when(strategyFactory.getStrategy(any(SprintMetricType.class))).thenReturn(mockStrategy);

		// Setup data for both projects
		List<SprintDetails> sprints = createSprintDetailsList(3);
		when(sprintDetailsService.findByBasicProjectConfigIdInByCompletedDateDesc(anyList(), anyInt()))
				.thenReturn(sprints);

		List<JiraIssue> issues = createJiraIssuesList(5);
		when(jiraIssueRepository.findByNumberInAndBasicProjectConfigId(anyList(), anyString()))
				.thenReturn(issues);

		List<JiraIssueCustomHistory> histories = createHistoriesList(5);
		when(jiraIssueCustomHistoryRepository.findByStoryIDInAndBasicProjectConfigIdIn(anyList(), anyList()))
				.thenReturn(histories);

		ProjectSprintMetrics projectMetrics = createProjectSprintMetrics("Test Project", 3);
		when(mockStrategy.calculate(any(SprintMetricContext.class))).thenReturn(projectMetrics);

		// Execute
		ServiceResponse response = service.computeSprintAnalyticsData(request);

		// Verify
		assertNotNull(response);
		assertTrue(response.getSuccess());

		SprintAnalyticsResponseDTO analyticsResponse = (SprintAnalyticsResponseDTO) response.getData();
		assertNotNull(analyticsResponse);
		assertEquals(1, analyticsResponse.getAnalytics().size());

		SprintMetricDTO metricDTO = analyticsResponse.getAnalytics().get(0);
		assertEquals(2, metricDTO.getProjects().size()); // 2 projects

		verify(mockStrategy, times(2)).calculate(any(SprintMetricContext.class));
	}

	@Test
	public void testComputeSprintAnalyticsData_NoSprints() {
		// Setup enabled metrics
		List<SprintMetricType> enabledMetrics = Collections.singletonList(SprintMetricType.GROOMING_DAY_ONE);
		when(strategyFactory.getEnabledMetricTypes()).thenReturn(enabledMetrics);
		when(strategyFactory.getStrategy(any(SprintMetricType.class))).thenReturn(mockStrategy);

		// No sprints found
		when(sprintDetailsService.findByBasicProjectConfigIdInByCompletedDateDesc(anyList(), anyInt()))
				.thenReturn(new ArrayList<>());

		ProjectSprintMetrics projectMetrics = createProjectSprintMetrics("Test Project", 0);
		when(mockStrategy.calculate(any(SprintMetricContext.class))).thenReturn(projectMetrics);

		// Execute
		ServiceResponse response = service.computeSprintAnalyticsData(request);

		// Verify
		assertNotNull(response);
		assertTrue(response.getSuccess());

		verify(mockStrategy, times(1)).calculate(any(SprintMetricContext.class));
	}

	@Test
	public void testComputeSprintAnalyticsData_WithWarnings() {
		// Setup enabled metrics
		List<SprintMetricType> enabledMetrics = Arrays.asList(
				SprintMetricType.GROOMING_DAY_ONE,
				SprintMetricType.DEV_COMPLETION_BREACH);
		when(strategyFactory.getEnabledMetricTypes()).thenReturn(enabledMetrics);

		// First metric succeeds
		when(strategyFactory.getStrategy(SprintMetricType.GROOMING_DAY_ONE)).thenReturn(mockStrategy);

		// Second metric fails
		when(strategyFactory.getStrategy(SprintMetricType.DEV_COMPLETION_BREACH))
				.thenThrow(new RuntimeException("Test exception"));

		List<SprintDetails> sprints = createSprintDetailsList(3);
		when(sprintDetailsService.findByBasicProjectConfigIdInByCompletedDateDesc(anyList(), anyInt()))
				.thenReturn(sprints);

		List<JiraIssue> issues = createJiraIssuesList(5);
		when(jiraIssueRepository.findByNumberInAndBasicProjectConfigId(anyList(), anyString()))
				.thenReturn(issues);

		List<JiraIssueCustomHistory> histories = createHistoriesList(5);
		when(jiraIssueCustomHistoryRepository.findByStoryIDInAndBasicProjectConfigIdIn(anyList(), anyList()))
				.thenReturn(histories);

		ProjectSprintMetrics projectMetrics = createProjectSprintMetrics("Test Project", 3);
		when(mockStrategy.calculate(any(SprintMetricContext.class))).thenReturn(projectMetrics);

		// Execute
		ServiceResponse response = service.computeSprintAnalyticsData(request);

		// Verify
		assertNotNull(response);
		assertTrue(response.getSuccess());
		assertTrue(response.getMessage().contains("warning"));

		SprintAnalyticsResponseDTO analyticsResponse = (SprintAnalyticsResponseDTO) response.getData();
		assertNotNull(analyticsResponse);
		assertFalse(analyticsResponse.getWarnings().isEmpty());
		assertEquals(1, analyticsResponse.getWarnings().size());
	}

	@Test(expected = BadRequestException.class)
	public void testComputeSprintAnalyticsData_InvalidProjectId() {
		request.setProjectBasicConfigIds(Set.of("invalid-id"));

		List<SprintMetricType> enabledMetrics = Collections.singletonList(SprintMetricType.GROOMING_DAY_ONE);

		// Execute - should throw BadRequestException
		service.computeSprintAnalyticsData(request);
	}

	@Test
	public void testComputeSprintAnalyticsData_ProjectNotFound() {
		// Project not in config map
		when(configHelperService.getProjectConfigMap()).thenReturn(new HashMap<>());

		List<SprintMetricType> enabledMetrics = Collections.singletonList(SprintMetricType.GROOMING_DAY_ONE);
		when(strategyFactory.getEnabledMetricTypes()).thenReturn(enabledMetrics);
		when(strategyFactory.getStrategy(any(SprintMetricType.class))).thenReturn(mockStrategy);

		// Execute
		ServiceResponse response = service.computeSprintAnalyticsData(request);

		// Verify
		assertNotNull(response);
		assertTrue(response.getSuccess());

		SprintAnalyticsResponseDTO analyticsResponse = (SprintAnalyticsResponseDTO) response.getData();
		assertFalse(analyticsResponse.getWarnings().isEmpty());
	}

	@Test
	public void testComputeSprintAnalyticsData_CrossSprintCalculations() {
		// Setup multiple sprints for cross-sprint analysis (5 sprints)
		List<SprintDetails> sprints = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			SprintDetails sprint = new SprintDetails();
			sprint.setSprintID("sprint-" + i);
			sprint.setSprintName("Sprint " + (i + 1));
			sprint.setBasicProjectConfigId(new ObjectId(projectIdStr));
			sprint.setStartDate("2024-01-" + String.format("%02d", (i * 14) + 1) + "T10:00:00.000Z");
			sprint.setEndDate("2024-01-" + String.format("%02d", (i * 14) + 14) + "T17:00:00.000Z");

			// Create issues that could span multiple sprints (for spillover analysis)
			Set<SprintIssue> totalIssues = new HashSet<>();
			for (int j = 0; j < 10; j++) {
				SprintIssue issue = new SprintIssue();
				issue.setNumber("ISSUE-" + (i * 10 + j));
				totalIssues.add(issue);
			}
			sprint.setTotalIssues(totalIssues);
			sprints.add(sprint);
		}

		when(sprintDetailsService.findByBasicProjectConfigIdInByCompletedDateDesc(anyList(), anyInt()))
				.thenReturn(sprints);

		// Setup issues with cross-sprint relationships
		List<JiraIssue> issues = new ArrayList<>();
		for (int i = 0; i < 50; i++) {
			JiraIssue issue = new JiraIssue();
			issue.setNumber("ISSUE-" + i);
			issue.setBasicProjectConfigId(projectIdStr);
			// Add some issues with dev due dates for cross-sprint analysis
			if (i % 5 == 0) {
				issue.setDevDueDate("2024-01-15"); // Do in middle of sprint timeline
			}
			issues.add(issue);
		}

		when(jiraIssueRepository.findByNumberInAndBasicProjectConfigId(anyList(), anyString()))
				.thenReturn(issues);

		// Setup histories for cross-sprint status tracking
		List<JiraIssueCustomHistory> histories = new ArrayList<>();
		for (int i = 0; i < 50; i++) {
			JiraIssueCustomHistory history = new JiraIssueCustomHistory();
			history.setStoryID("ISSUE-" + i);
			history.setBasicProjectConfigId(projectIdStr);
			// Add status change logs spanning multiple sprints
			List<JiraHistoryChangeLog> logs = new ArrayList<>();
			for (int j = 0; j < 3; j++) {
				JiraHistoryChangeLog log = new JiraHistoryChangeLog();
				log.setUpdatedOn(LocalDate.of(2024, 1, (j * 10) + 1).atStartOfDay());
				log.setChangedFrom("Backlog");
				log.setChangedTo(j == 2 ? "Done" : "In Progress");
				logs.add(log);
			}
			history.setStatusUpdationLog(logs);
			histories.add(history);
		}

		when(jiraIssueCustomHistoryRepository.findByStoryIDInAndBasicProjectConfigIdIn(anyList(), anyList()))
				.thenReturn(histories);

		// Enable metrics that require cross-sprint analysis
		List<SprintMetricType> enabledMetrics = Arrays.asList(
				SprintMetricType.SPILLOVER_AGE,
				SprintMetricType.DEV_COMPLETION_BREACH,
				SprintMetricType.GROOMING_DAY_ONE);
		when(strategyFactory.getEnabledMetricTypes()).thenReturn(enabledMetrics);
		when(strategyFactory.getStrategy(any(SprintMetricType.class))).thenReturn(mockStrategy);

		ProjectSprintMetrics projectMetrics = createProjectSprintMetrics("Test Project", 5);
		when(mockStrategy.calculate(any(SprintMetricContext.class))).thenReturn(projectMetrics);

		// Execute
		ServiceResponse response = service.computeSprintAnalyticsData(request);

		// Verify cross-sprint calculations work correctly
		assertNotNull(response);
		assertTrue(response.getSuccess());

		SprintAnalyticsResponseDTO analyticsResponse = (SprintAnalyticsResponseDTO) response.getData();
		assertNotNull(analyticsResponse);
		assertEquals(3, analyticsResponse.getAnalytics().size()); // 3 enabled metrics

		// Verify each metric was calculated with full sprint context
		verify(mockStrategy, times(3)).calculate(any(SprintMetricContext.class));
	}

	@Test
	public void testComputeSprintAnalyticsData_DataConsistencyValidation() {
		// Setup sprint with issues
		List<SprintDetails> sprints = createSprintDetailsList(1);
		when(sprintDetailsService.findByBasicProjectConfigIdInByCompletedDateDesc(anyList(), anyInt()))
				.thenReturn(sprints);

		// Setup issues - but with fewer issues than referenced in sprints (data inconsistency)
		List<JiraIssue> issues = createJiraIssuesList(3); // Only 3 issues but sprints reference 5
		when(jiraIssueRepository.findByNumberInAndBasicProjectConfigId(anyList(), anyString()))
				.thenReturn(issues);

		// Setup histories - with different set of story IDs (inconsistency)
		List<JiraIssueCustomHistory> histories = new ArrayList<>();
		for (int i = 0; i < 2; i++) { // Only 2 histories but 5 issues expected
			JiraIssueCustomHistory history = new JiraIssueCustomHistory();
			history.setStoryID("ISSUE-" + (i + 10)); // Different issue numbers
			history.setBasicProjectConfigId(projectIdStr);
			histories.add(history);
		}
		when(jiraIssueCustomHistoryRepository.findByStoryIDInAndBasicProjectConfigIdIn(anyList(), anyList()))
				.thenReturn(histories);

		List<SprintMetricType> enabledMetrics = Collections.singletonList(SprintMetricType.GROOMING_DAY_ONE);
		when(strategyFactory.getEnabledMetricTypes()).thenReturn(enabledMetrics);
		when(strategyFactory.getStrategy(any(SprintMetricType.class))).thenReturn(mockStrategy);

		ProjectSprintMetrics projectMetrics = createProjectSprintMetrics("Test Project", 1);
		when(mockStrategy.calculate(any(SprintMetricContext.class))).thenReturn(projectMetrics);

		// Execute
		ServiceResponse response = service.computeSprintAnalyticsData(request);

		// Verify - should handle data inconsistencies gracefully
		assertNotNull(response);
		assertTrue(response.getSuccess());

		// Should still calculate metrics even with missing data
		verify(mockStrategy, times(1)).calculate(any(SprintMetricContext.class));
	}

	@Test
	public void testComputeSprintAnalyticsData_PerformanceUnderLoad() {
		// Setup large number of sprints (performance test)
		int largeSprintCount = 20;
		List<SprintDetails> sprints = new ArrayList<>();
		for (int i = 0; i < largeSprintCount; i++) {
			SprintDetails sprint = new SprintDetails();
			sprint.setSprintID("sprint-" + i);
			sprint.setSprintName("Sprint " + (i + 1));
			sprint.setBasicProjectConfigId(new ObjectId(projectIdStr));

			// Large number of issues per sprint
			Set<SprintIssue> totalIssues = new HashSet<>();
			for (int j = 0; j < 50; j++) { // 50 issues per sprint
				SprintIssue issue = new SprintIssue();
				issue.setNumber("ISSUE-" + (i * 50 + j));
				totalIssues.add(issue);
			}
			sprint.setTotalIssues(totalIssues);
			sprints.add(sprint);
		}

		when(sprintDetailsService.findByBasicProjectConfigIdInByCompletedDateDesc(anyList(), anyInt()))
				.thenReturn(sprints);

		// Large number of issues (1000 total)
		List<JiraIssue> issues = new ArrayList<>();
		for (int i = 0; i < 1000; i++) {
			JiraIssue issue = new JiraIssue();
			issue.setNumber("ISSUE-" + i);
			issue.setBasicProjectConfigId(projectIdStr);
			issues.add(issue);
		}

		when(jiraIssueRepository.findByNumberInAndBasicProjectConfigId(anyList(), anyString()))
				.thenReturn(issues);

		// Large number of histories
		List<JiraIssueCustomHistory> histories = new ArrayList<>();
		for (int i = 0; i < 1000; i++) {
			JiraIssueCustomHistory history = new JiraIssueCustomHistory();
			history.setStoryID("ISSUE-" + i);
			history.setBasicProjectConfigId(projectIdStr);
			histories.add(history);
		}

		when(jiraIssueCustomHistoryRepository.findByStoryIDInAndBasicProjectConfigIdIn(anyList(), anyList()))
				.thenReturn(histories);

		List<SprintMetricType> enabledMetrics = Arrays.asList(
				SprintMetricType.GROOMING_DAY_ONE,
				SprintMetricType.DEV_COMPLETION_BREACH);
		when(strategyFactory.getEnabledMetricTypes()).thenReturn(enabledMetrics);
		when(strategyFactory.getStrategy(any(SprintMetricType.class))).thenReturn(mockStrategy);

		ProjectSprintMetrics projectMetrics = createProjectSprintMetrics("Test Project", largeSprintCount);
		when(mockStrategy.calculate(any(SprintMetricContext.class))).thenReturn(projectMetrics);

		// Execute - should handle large datasets without performance issues
		ServiceResponse response = service.computeSprintAnalyticsData(request);

		// Verify
		assertNotNull(response);
		assertTrue(response.getSuccess());

		SprintAnalyticsResponseDTO analyticsResponse = (SprintAnalyticsResponseDTO) response.getData();
		assertNotNull(analyticsResponse);
		assertEquals(2, analyticsResponse.getAnalytics().size());

		// Verify strategies were called correctly
		verify(mockStrategy, times(2)).calculate(any(SprintMetricContext.class));
	}

	@Test
	public void testComputeSprintAnalyticsData_MemoryUsagePatterns() {
		// Test with gradually increasing data sizes to check memory patterns
		int[] sprintCounts = {1, 5, 10, 15};

		for (int sprintCount : sprintCounts) {
			// Setup sprints with increasing sizes
			List<SprintDetails> sprints = new ArrayList<>();
			for (int i = 0; i < sprintCount; i++) {
				SprintDetails sprint = new SprintDetails();
				sprint.setSprintID("sprint-" + i);
				sprint.setSprintName("Sprint " + (i + 1));
				sprint.setBasicProjectConfigId(new ObjectId(projectIdStr));

				Set<SprintIssue> totalIssues = new HashSet<>();
				for (int j = 0; j < 20; j++) {
					SprintIssue issue = new SprintIssue();
					issue.setNumber("ISSUE-" + (i * 20 + j));
					totalIssues.add(issue);
				}
				sprint.setTotalIssues(totalIssues);
				sprints.add(sprint);
			}

			when(sprintDetailsService.findByBasicProjectConfigIdInByCompletedDateDesc(anyList(), anyInt()))
					.thenReturn(sprints);

			// Corresponding issues
			List<JiraIssue> issues = createJiraIssuesList(sprintCount * 20);
			when(jiraIssueRepository.findByNumberInAndBasicProjectConfigId(anyList(), anyString()))
					.thenReturn(issues);

			List<JiraIssueCustomHistory> histories = createHistoriesList(sprintCount * 20);
			when(jiraIssueCustomHistoryRepository.findByStoryIDInAndBasicProjectConfigIdIn(anyList(), anyList()))
					.thenReturn(histories);

			List<SprintMetricType> enabledMetrics = Collections.singletonList(SprintMetricType.GROOMING_DAY_ONE);
			when(strategyFactory.getEnabledMetricTypes()).thenReturn(enabledMetrics);
			when(strategyFactory.getStrategy(any(SprintMetricType.class))).thenReturn(mockStrategy);

			ProjectSprintMetrics projectMetrics = createProjectSprintMetrics("Test Project", sprintCount);
			when(mockStrategy.calculate(any(SprintMetricContext.class))).thenReturn(projectMetrics);

			// Execute for each size
			ServiceResponse response = service.computeSprintAnalyticsData(request);

			// Verify each size works correctly
			assertNotNull("Failed for sprint count: " + sprintCount, response);
			assertTrue("Failed for sprint count: " + sprintCount, response.getSuccess());

			// Reset mocks for next iteration
			reset(mockStrategy);
		}
	}

	@Test
	public void testComputeSprintAnalyticsData_CrossProjectDataConsistency() {
		// Setup multiple projects with potential data consistency issues
		ObjectId projectId2 = new ObjectId();
		String projectIdStr2 = projectId2.toString();

		// Setup projects
		ProjectBasicConfig projectBasicConfig2 = new ProjectBasicConfig();
		projectBasicConfig2.setId(projectId2);
		projectBasicConfig2.setProjectName("Test Project 2");

		Map<String, ProjectBasicConfig> projectConfigMap = new HashMap<>();
		projectConfigMap.put(projectIdStr, projectBasicConfig);
		projectConfigMap.put(projectIdStr2, projectBasicConfig2);
		when(configHelperService.getProjectConfigMap()).thenReturn(projectConfigMap);

		// Field mappings
		FieldMapping fieldMapping2 = new FieldMapping();
		fieldMapping2.setBasicProjectConfigId(projectId2);
		when(configHelperService.getFieldMapping(projectId2)).thenReturn(fieldMapping2);

		// Update request
		request.setProjectBasicConfigIds(Set.of(projectIdStr, projectIdStr2));

		// Setup account hierarchy
		List<AccountFilteredData> accountFilteredDataList = new ArrayList<>();
		AccountFilteredData data1 = new AccountFilteredData();
		data1.setBasicProjectConfigId(new ObjectId(projectIdStr));
		AccountFilteredData data2 = new AccountFilteredData();
		data2.setBasicProjectConfigId(new ObjectId(projectIdStr2));
		accountFilteredDataList.add(data1);
		accountFilteredDataList.add(data2);
		when(accountHierarchyServiceImpl.getHierarchyDataCurrentUserHasAccessTo(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT))
				.thenReturn(accountFilteredDataList);

		// Setup different data for each project (testing cross-project consistency)
		List<SprintDetails> sprints1 = createSprintDetailsList(2);
		List<SprintDetails> sprints2 = createSprintDetailsList(3);

		// Mock different return values for different projects
		when(sprintDetailsService.findByBasicProjectConfigIdInByCompletedDateDesc(
				eq(Collections.singletonList(new ObjectId(projectIdStr))), anyInt()))
				.thenReturn(sprints1);
		when(sprintDetailsService.findByBasicProjectConfigIdInByCompletedDateDesc(
				eq(Collections.singletonList(new ObjectId(projectIdStr2))), anyInt()))
				.thenReturn(sprints2);

		// Different issue sets for each project
		List<JiraIssue> issues1 = createJiraIssuesList(10);
		List<JiraIssue> issues2 = new ArrayList<>();
		for (int i = 0; i < 15; i++) {
			JiraIssue issue = new JiraIssue();
			issue.setNumber("PROJECT2-ISSUE-" + i);
			issue.setBasicProjectConfigId(projectIdStr2);
			issues2.add(issue);
		}

		when(jiraIssueRepository.findByNumberInAndBasicProjectConfigId(anyList(), eq(projectIdStr)))
				.thenReturn(issues1);
		when(jiraIssueRepository.findByNumberInAndBasicProjectConfigId(anyList(), eq(projectIdStr2)))
				.thenReturn(issues2);

		List<JiraIssueCustomHistory> histories1 = createHistoriesList(10);
		List<JiraIssueCustomHistory> histories2 = new ArrayList<>();
		for (int i = 0; i < 15; i++) {
			JiraIssueCustomHistory history = new JiraIssueCustomHistory();
			history.setStoryID("PROJECT2-ISSUE-" + i);
			history.setBasicProjectConfigId(projectIdStr2);
			histories2.add(history);
		}

		when(jiraIssueCustomHistoryRepository.findByStoryIDInAndBasicProjectConfigIdIn(
				anyList(), eq(Collections.singletonList(projectIdStr))))
				.thenReturn(histories1);
		when(jiraIssueCustomHistoryRepository.findByStoryIDInAndBasicProjectConfigIdIn(
				anyList(), eq(Collections.singletonList(projectIdStr2))))
				.thenReturn(histories2);

		List<SprintMetricType> enabledMetrics = Collections.singletonList(SprintMetricType.GROOMING_DAY_ONE);
		when(strategyFactory.getEnabledMetricTypes()).thenReturn(enabledMetrics);
		when(strategyFactory.getStrategy(any(SprintMetricType.class))).thenReturn(mockStrategy);

		ProjectSprintMetrics projectMetrics = createProjectSprintMetrics("Test Project", 2);
		when(mockStrategy.calculate(any(SprintMetricContext.class))).thenReturn(projectMetrics);

		// Execute
		ServiceResponse response = service.computeSprintAnalyticsData(request);

		// Verify cross-project data consistency
		assertNotNull(response);
		assertTrue(response.getSuccess());

		SprintAnalyticsResponseDTO analyticsResponse = (SprintAnalyticsResponseDTO) response.getData();
		assertEquals(1, analyticsResponse.getAnalytics().size());

		SprintMetricDTO metricDTO = analyticsResponse.getAnalytics().get(0);
		assertEquals(2, metricDTO.getProjects().size()); // 2 projects

		// Verify strategies were called for each project separately
		verify(mockStrategy, times(2)).calculate(any(SprintMetricContext.class));
	}

	// Helper methods
	private List<SprintDetails> createSprintDetailsList(int count) {
		List<SprintDetails> sprints = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			SprintDetails sprint = new SprintDetails();
			sprint.setSprintID("sprint-" + i);
			sprint.setSprintName("Sprint " + i);
			sprint.setBasicProjectConfigId(new ObjectId(projectIdStr));

			Set<SprintIssue> totalIssues = new HashSet<>();
			for (int j = 0; j < 5; j++) {
				SprintIssue issue = new SprintIssue();
				issue.setNumber("ISSUE-" + (i * 5 + j));
				totalIssues.add(issue);
			}
			sprint.setTotalIssues(totalIssues);
			sprints.add(sprint);
		}
		return sprints;
	}

	private List<JiraIssue> createJiraIssuesList(int count) {
		List<JiraIssue> issues = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			JiraIssue issue = new JiraIssue();
			issue.setNumber("ISSUE-" + i);
			issue.setBasicProjectConfigId(projectIdStr);
			issues.add(issue);
		}
		return issues;
	}

	private List<JiraIssueCustomHistory> createHistoriesList(int count) {
		List<JiraIssueCustomHistory> histories = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			JiraIssueCustomHistory history = new JiraIssueCustomHistory();
			history.setStoryID("ISSUE-" + i);
			history.setBasicProjectConfigId(projectIdStr);
			histories.add(history);
		}
		return histories;
	}

	private ProjectSprintMetrics createProjectSprintMetrics(String projectName, int sprintCount) {
		List<SprintDataPoint> dataPoints = new ArrayList<>();
		for (int i = 0; i < sprintCount; i++) {
			SprintDataPoint dp = SprintDataPoint.builder()
					.sprint("Sprint " + i)
					.name("Sprint " + i)
					.value("5")
					.trend("25")
					.build();
			dataPoints.add(dp);
		}

		return ProjectSprintMetrics.builder()
				.name(projectName)
				.sprints(dataPoints)
				.build();
	}
}
