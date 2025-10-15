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

package com.publicissapient.kpidashboard.apis.analysis.analytics.aiusage.service;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.apache.commons.collections4.CollectionUtils;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.analysis.analytics.aiusage.dto.AiUsageAnalyticsResponseDTO;
import com.publicissapient.kpidashboard.apis.analysis.analytics.aiusage.dto.AiUsageAnalyticsSummaryDTO;
import com.publicissapient.kpidashboard.apis.analysis.analytics.shared.dto.BaseAnalyticsRequestDTO;
import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.jira.service.SprintDetailsServiceImpl;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;

@RunWith(MockitoJUnitRunner.class)
public class AiUsageAnalyticsServiceTest {

	private static final int PROJECT_LEVEL = 5;

	private static final String PROJECT_LABEL = "project";
	private static final String TEST_NODE_ID = "testNodeId";

	private static final List<String> TEST_BASIC_PROJECT_CONFIG_IDS = List.of("64f123abc9a4d2e1b7f02c11",
			"64f123abc9a4d2e1b7f02c12", "64f123abc9a4d2e1b7f02c13", "64f123abc9a4d2e1b7f02c14",
			"64f123abc9a4d2e1b7f02c15", "64f123abc9a4d2e1b7f02c16", "64f123abc9a4d2e1b7f02c17",
			"64f123abc9a4d2e1b7f02c18", "64f123abc9a4d2e1b7f02c19", "64f123abc9a4d2e1b7f02c1a");

	@Mock
	private JiraIssueRepository jiraIssueRepository;

	@Mock
	private ConfigHelperService configHelperService;

	@Mock
	private SprintDetailsServiceImpl sprintDetailsService;

	@Mock
	private AccountHierarchyServiceImpl accountHierarchyServiceImpl;

	@InjectMocks
	private AiUsageAnalyticsServiceImpl aiUsageAnalyticsService;

	@Test
	public void when_BaseAnalyticsRequestDTOIsNull_Expect_ExceptionIsThrown() {
		assertThrows(BadRequestException.class, () -> aiUsageAnalyticsService.computeAiUsageAnalyticsData(null));
	}

	@Test
	public void when_UserDoesNotHaveAccessToAnyProjects_ExpectExceptionIsThrown() {
		assertThrows(ForbiddenException.class,
				() -> aiUsageAnalyticsService.computeAiUsageAnalyticsData(new BaseAnalyticsRequestDTO()));
	}

	@Test
	public void when_UserDoesntHaveAccessToTheRequestedBasicProjectConfigIdsOrTheyDoNotExist_Expect_ExceptionIsThrown() {
		when(accountHierarchyServiceImpl.getHierarchyDataCurrentUserHasAccessTo(anyString())).thenReturn(generateProjectAccountFilteredData());

		BaseAnalyticsRequestDTO aiUsageAnalyticsRequestDTO = new BaseAnalyticsRequestDTO(10,
				Set.of("invalid-basic-project-config-id"));

		assertThrows(BadRequestException.class,
				() -> aiUsageAnalyticsService.computeAiUsageAnalyticsData(aiUsageAnalyticsRequestDTO));
	}

	@Test
	public void when_RequestIsValid_Expect_AiUsageAnalyticsAreComputedAccordingly() {
		int sprintsLimit = 2;

		List<String> basicProjectConfigIdsAsString = List.of("64f123abc9a4d2e1b7f02c11", "64f123abc9a4d2e1b7f02c12");
		when(accountHierarchyServiceImpl.getHierarchyDataCurrentUserHasAccessTo(anyString())).thenReturn(generateProjectAccountFilteredData());

		when(sprintDetailsService.findByBasicProjectConfigIdInByCompletedDateDesc(
				List.of(new ObjectId("64f123abc9a4d2e1b7f02c11"), new ObjectId("64f123abc9a4d2e1b7f02c12")),
				sprintsLimit))
				.thenReturn(generateSprintDetailsListBasedOnBasicProjectConfigIds(basicProjectConfigIdsAsString));
		when(configHelperService.getFieldMappingMap()).thenReturn(Map.of(new ObjectId("64f123abc9a4d2e1b7f02c11"),
				constructFieldMappingByKPI198Fields(null, null), new ObjectId("64f123abc9a4d2e1b7f02c12"),
				constructFieldMappingByKPI198Fields(List.of("Done"), List.of("Story"))));

		when(jiraIssueRepository.findByNumberInAndBasicProjectConfigIdIn(
				Set.of("issue-1", "issue-2", "issue-3", "issue-4", "issue-5"),
				new HashSet<>(basicProjectConfigIdsAsString)))
				.thenReturn(List.of(
						JiraIssue.builder().number("issue-1").aiUsageType("AI Usage type 1").aiEfficiencyGain(2.0D)
								.basicProjectConfigId("64f123abc9a4d2e1b7f02c11").build(),
						JiraIssue.builder().number("issue-2").aiUsageType("AI Usage type 2").aiEfficiencyGain(0.0D)
								.basicProjectConfigId("64f123abc9a4d2e1b7f02c11").build(),
						JiraIssue.builder().number("issue-3").aiUsageType("AI Usage type 3").aiEfficiencyGain(2.3D)
								.basicProjectConfigId("64f123abc9a4d2e1b7f02c11").build(),
						JiraIssue.builder().number("issue-1").aiUsageType("AI Usage type 4").aiEfficiencyGain(5.4D)
								.basicProjectConfigId("64f123abc9a4d2e1b7f02c12").build(),
						JiraIssue.builder().number("issue-2").aiUsageType("AI Usage type 1").aiEfficiencyGain(20.0D)
								.basicProjectConfigId("64f123abc9a4d2e1b7f02c12").build(),
						JiraIssue.builder().number("issue-3").aiUsageType("AI Usage type 1").aiEfficiencyGain(15.0D)
								.basicProjectConfigId("64f123abc9a4d2e1b7f02c12").build(),
						JiraIssue.builder().number("issue-4").aiUsageType("AI Usage type 4").aiEfficiencyGain(6.0D)
								.basicProjectConfigId("64f123abc9a4d2e1b7f02c12").build()));

		BaseAnalyticsRequestDTO aiUsageAnalyticsRequestDTO = new BaseAnalyticsRequestDTO();
		aiUsageAnalyticsRequestDTO.setProjectBasicConfigIds(new HashSet<>(basicProjectConfigIdsAsString));
		aiUsageAnalyticsRequestDTO.setNumberOfSprintsToInclude(sprintsLimit);

		ServiceResponse serviceResponse = aiUsageAnalyticsService
				.computeAiUsageAnalyticsData(aiUsageAnalyticsRequestDTO);

		assertNotNull(serviceResponse);
		assertNotNull(serviceResponse.getData());
		assertInstanceOf(AiUsageAnalyticsResponseDTO.class, serviceResponse.getData());
		AiUsageAnalyticsResponseDTO aiUsageAnalyticsResponseDTO = (AiUsageAnalyticsResponseDTO) serviceResponse.getData();

		assertNotNull(aiUsageAnalyticsResponseDTO.getSummary());
		AiUsageAnalyticsSummaryDTO aiUsageAnalyticsSummaryDTO = aiUsageAnalyticsResponseDTO.getSummary();
		assertEquals(2, aiUsageAnalyticsSummaryDTO.getProjectsNumber());
		assertEquals(4, aiUsageAnalyticsSummaryDTO.getUsageTypesNumber());
		assertEquals(0, aiUsageAnalyticsSummaryDTO.getAverageEfficiencyGainPerAiUsageType());
		assertEquals(0, aiUsageAnalyticsSummaryDTO.getAverageEfficiencyGainPerProject());

		assertTrue(CollectionUtils.isNotEmpty(aiUsageAnalyticsResponseDTO.getAnalytics()));
	}

	public List<SprintDetails> generateSprintDetailsListBasedOnBasicProjectConfigIds(
			List<String> basicProjectConfigIds) {
		List<SprintDetails> sprintDetailsList = new ArrayList<>();
		for (String basicProjectConfigId : basicProjectConfigIds) {
			sprintDetailsList.addAll(IntStream.range(0, 5).mapToObj(sprintDetailsIndex -> {
				SprintDetails sprintDetails = new SprintDetails();
				sprintDetails.setCompletedIssues(Set.of(generateSprintIssue("Done", "Task", "issue-1"),
						generateSprintIssue("Done", "Story", "issue-2"),
						generateSprintIssue("Completed", "Epic", "issue-3"),
						generateSprintIssue("Done", "Task", "issue-4"),
						generateSprintIssue("Completed", "Enabler story", "issue-5")));
				sprintDetails.setTotalIssues(Set.of(generateSprintIssue("Done", "Task", "issue-1"),
						generateSprintIssue("Done", "Story", "issue-2"),
						generateSprintIssue("Completed", "Epic", "issue-3"),
						generateSprintIssue("Done", "Task", "issue-4"),
						generateSprintIssue("Completed", "Enabler story", "issue-5"),
						generateSprintIssue("ToDo", "Epic", "issue-11"),
						generateSprintIssue("ToDo", "Epic", "issue-12"),
						generateSprintIssue("Blocked", "Epic", "issue-13")));
				sprintDetails.setBasicProjectConfigId(new ObjectId(basicProjectConfigId));
				return sprintDetails;
			}).toList());
		}
		return sprintDetailsList;
	}

	private SprintIssue generateSprintIssue(String jiraStatus, String issueType, String issueNumber) {
		SprintIssue sprintIssue = new SprintIssue();
		sprintIssue.setStatus(jiraStatus);
		sprintIssue.setTypeName(issueType);
		sprintIssue.setNumber(issueNumber);
		return sprintIssue;
	}

	private FieldMapping constructFieldMappingByKPI198Fields(
			List<String> includedStatusesToIdentifyCompletedIssuesKPI198,
			List<String> includedIssueTypesFromTheCompletedIssuesKPI198) {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping
				.setIncludedStatusesToIdentifyCompletedIssuesKPI198(includedStatusesToIdentifyCompletedIssuesKPI198);
		fieldMapping.setIncludedIssueTypesFromTheCompletedIssuesKPI198(includedIssueTypesFromTheCompletedIssuesKPI198);
		return fieldMapping;
	}

	private List<AccountFilteredData> generateProjectAccountFilteredData() {
		return TEST_BASIC_PROJECT_CONFIG_IDS.stream()
				.map(basicProjectConfigIdAsString -> AccountFilteredData.builder().level(PROJECT_LEVEL)
						.labelName(PROJECT_LABEL).nodeId(TEST_NODE_ID + basicProjectConfigIdAsString)
						.basicProjectConfigId(new ObjectId(basicProjectConfigIdAsString)).build())
				.toList();
	}
}
