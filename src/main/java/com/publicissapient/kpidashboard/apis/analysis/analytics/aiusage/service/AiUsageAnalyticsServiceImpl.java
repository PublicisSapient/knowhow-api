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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.Valid;
import javax.ws.rs.InternalServerErrorException;

import org.apache.commons.collections4.CollectionUtils;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.MapUtils;

import com.publicissapient.kpidashboard.apis.analysis.analytics.aiusage.dto.AiUsageAnalyticsDTO;
import com.publicissapient.kpidashboard.apis.analysis.analytics.aiusage.dto.AiUsageAnalyticsRequestDTO;
import com.publicissapient.kpidashboard.apis.analysis.analytics.aiusage.dto.AiUsageAnalyticsResponseDTO;
import com.publicissapient.kpidashboard.apis.analysis.analytics.aiusage.dto.AiUsageAnalyticsSummaryDTO;
import com.publicissapient.kpidashboard.apis.analysis.analytics.aiusage.dto.ProjectAiUsageMetrics;
import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.jira.service.SprintDetailsServiceImpl;
import com.publicissapient.kpidashboard.apis.model.AccountFilterRequest;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;

import io.micrometer.common.util.StringUtils;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiUsageAnalyticsServiceImpl implements AiUsageAnalyticsService {

	private static final int HIERARCHY_LEVEL_PROJECT = 5;
	private static final int PERCENTAGE_MULTIPLIER = 100;

	private static final double TWO_DECIMAL_ROUNDING_COEFFICIENT = 100.0D;

	private static final String COMPUTING_AI_ANALYTICS_FAILED_ERROR_MESSAGE = "Computing the AI usage analytics failed";

	private final JiraIssueRepository jiraIssueRepository;

	private final ConfigHelperService configHelperService;

	private final SprintDetailsServiceImpl sprintDetailsService;

	private final AccountHierarchyServiceImpl accountHierarchyServiceImpl;

	@Data
	@AllArgsConstructor
	private static class ProjectDTO {
		private String name;

		private ObjectId projectBasicConfigId;
	}

	@Override
	public ServiceResponse computeAiUsageAnalyticsData(@Valid AiUsageAnalyticsRequestDTO aiUsageAnalyticsRequestDTO) {
		List<AccountFilteredData> projectsDataCurrentUserHasAccessTo = getHierarchyDataCurrentUserHasAccessTo(
				HIERARCHY_LEVEL_PROJECT, CommonConstant.HIERARCHY_LEVEL_ID_PROJECT);
		validateComputeAiUsageAnalyticsRequest(aiUsageAnalyticsRequestDTO, projectsDataCurrentUserHasAccessTo);

		ServiceResponse serviceResponse = new ServiceResponse();
		serviceResponse
				.setData(computeAiUsageAnalytics(aiUsageAnalyticsRequestDTO, projectsDataCurrentUserHasAccessTo));

		return serviceResponse;
	}

	private AiUsageAnalyticsResponseDTO computeAiUsageAnalytics(AiUsageAnalyticsRequestDTO aiUsageAnalyticsRequestDTO,
			List<AccountFilteredData> projectsDataCurrentUserHasAccessTo) {
		List<ProjectDTO> projectDTOList = createProjectDTOList(projectsDataCurrentUserHasAccessTo,
				aiUsageAnalyticsRequestDTO.getProjectBasicConfigIds());

		if (CollectionUtils.isEmpty(projectDTOList)) {
			log.error("Could not create the project AI usage analytics computation data list for request {}",
					aiUsageAnalyticsRequestDTO);
			throw new InternalServerErrorException(COMPUTING_AI_ANALYTICS_FAILED_ERROR_MESSAGE);
		}

		Map<String, List<JiraIssue>> basicProjectConfigIdAsStringJiraIssuesMap = createProjectBasicConfigIdAndAssociatedJiraIssuesMap(
				projectDTOList, aiUsageAnalyticsRequestDTO);

		if (MapUtils.isEmpty(basicProjectConfigIdAsStringJiraIssuesMap)) {
			log.warn("No Jira Issues could be found for project basic config ids present in the AI usage analytics "
					+ "request {}", aiUsageAnalyticsRequestDTO);
			return AiUsageAnalyticsResponseDTO.builder().build();
		}

		return constructAiUsageAnalyticsResponseDTO(projectDTOList, basicProjectConfigIdAsStringJiraIssuesMap);
	}

	private Map<String, List<JiraIssue>> createProjectBasicConfigIdAndAssociatedJiraIssuesMap(
			List<ProjectDTO> projectDTOList, AiUsageAnalyticsRequestDTO aiUsageAnalyticsRequestDTO) {
		List<SprintDetails> sprintsTakenIntoConsideration = sprintDetailsService
				.findByBasicProjectConfigIdInByCompletedDateDesc(
						projectDTOList.stream().map(ProjectDTO::getProjectBasicConfigId).toList(),
						aiUsageAnalyticsRequestDTO.getNumberOfSprintsToInclude());

		Map<ObjectId, List<SprintDetails>> basicProjectConfigIdSprintDetailsMap = sprintsTakenIntoConsideration.stream()
				.collect(Collectors.groupingBy(SprintDetails::getBasicProjectConfigId));

		Set<String> jiraIssueNumbers = new HashSet<>();

		for (ProjectDTO projectDTO : projectDTOList) {
			jiraIssueNumbers.addAll(getJiraIssueNumbersForProject(projectDTO.getProjectBasicConfigId(),
					basicProjectConfigIdSprintDetailsMap.get(projectDTO.getProjectBasicConfigId())));
		}

		List<JiraIssue> jiraIssues = jiraIssueRepository.findByNumberInAndBasicProjectConfigIdIn(jiraIssueNumbers,
				aiUsageAnalyticsRequestDTO.getProjectBasicConfigIds());

		return jiraIssues.stream().collect(Collectors.groupingBy(JiraIssue::getBasicProjectConfigId));
	}

	private Set<String> getJiraIssueNumbersForProject(ObjectId projectBasicConfigId,
			List<SprintDetails> projectSprintDetails) {
		if (CollectionUtils.isEmpty(projectSprintDetails)) {
			return Collections.emptySet();
		}
		Set<String> projectJiraIssueNumbers;
		FieldMapping projectFieldMapping = configHelperService.getFieldMappingMap().get(projectBasicConfigId);
		List<String> includedStatusesToIdentifyCompletedIssuesKPI198 = projectFieldMapping
				.getIncludedStatusesToIdentifyCompletedIssuesKPI198();

		List<String> includedIssueTypesFromTheCompletedIssuesKPI198 = projectFieldMapping
				.getIncludedStatusesToIdentifyCompletedIssuesKPI198();

		if (CollectionUtils.isNotEmpty(includedStatusesToIdentifyCompletedIssuesKPI198)) {
			projectJiraIssueNumbers = projectSprintDetails.stream()
					.flatMap(sprintDetails -> sprintDetails.getTotalIssues().stream()).filter(sprintIssue -> {
						if (CollectionUtils.isNotEmpty(includedIssueTypesFromTheCompletedIssuesKPI198)) {
							return includedStatusesToIdentifyCompletedIssuesKPI198.contains(sprintIssue.getStatus())
									&& includedIssueTypesFromTheCompletedIssuesKPI198
											.contains(sprintIssue.getTypeName());
						}
						return includedStatusesToIdentifyCompletedIssuesKPI198.contains(sprintIssue.getStatus());
					}).map(SprintIssue::getNumber).collect(Collectors.toSet());
		} else {
			projectJiraIssueNumbers = projectSprintDetails.stream()
					.flatMap(sprintDetails -> sprintDetails.getCompletedIssues().stream()).filter(sprintIssue -> {
						if (CollectionUtils.isNotEmpty(includedIssueTypesFromTheCompletedIssuesKPI198)) {
							return includedIssueTypesFromTheCompletedIssuesKPI198.contains(sprintIssue.getTypeName());
						}
						return true;
					}).map(SprintIssue::getNumber).collect(Collectors.toSet());
		}

		return projectJiraIssueNumbers;
	}

	private List<AccountFilteredData> getHierarchyDataCurrentUserHasAccessTo(int level, String label) {
		if (StringUtils.isEmpty(label)) {
			throw new IllegalArgumentException("The 'label' parameter is mandatory");
		}
		AccountFilterRequest accountFilterRequest = new AccountFilterRequest();
		accountFilterRequest.setKanban(false);
		accountFilterRequest.setSprintIncluded(List.of(CommonConstant.CLOSED.toUpperCase()));

		return accountHierarchyServiceImpl.getFilteredList(accountFilterRequest).stream()
				.filter(accountFilteredData -> Objects.nonNull(accountFilteredData)
						&& level == accountFilteredData.getLevel()
						&& label.equalsIgnoreCase(accountFilteredData.getLabelName()))
				.toList();
	}

	private static Map<String, Map<String, ProjectAiUsageMetrics>> createAiUsageTypeBasicProjectConfigIdAsStringProjectAiUsageAnalyticsDTOMap(
			List<ProjectDTO> projectDTOList, Map<String, List<JiraIssue>> basicProjectConfigIdAsStringJiraIssuesMap) {
		Map<String, Map<String, ProjectAiUsageMetrics>> aiUsageTypeBasicProjectConfigIdAsStringProjectAiUsageAnalyticsDTOMap = new HashMap<>();
		for (ProjectDTO projectDTO : projectDTOList) {
			if (basicProjectConfigIdAsStringJiraIssuesMap
					.containsKey(projectDTO.getProjectBasicConfigId().toString())) {
				basicProjectConfigIdAsStringJiraIssuesMap.get(projectDTO.getProjectBasicConfigId().toString())
						.forEach(jiraIssue -> {
							if (StringUtils.isNotEmpty(jiraIssue.getAiUsageType())) {
								aiUsageTypeBasicProjectConfigIdAsStringProjectAiUsageAnalyticsDTOMap
										.computeIfAbsent(jiraIssue.getAiUsageType(), value -> new HashMap<>());
								Map<String, ProjectAiUsageMetrics> basicProjectConfigIdAsStringProjectAiUsageAnalyticsDTOMap = aiUsageTypeBasicProjectConfigIdAsStringProjectAiUsageAnalyticsDTOMap
										.get(jiraIssue.getAiUsageType());
								String projectBasicConfigIdAsString = projectDTO.getProjectBasicConfigId().toString();
								if (basicProjectConfigIdAsStringProjectAiUsageAnalyticsDTOMap
										.containsKey(projectBasicConfigIdAsString)) {
									ProjectAiUsageMetrics projectAiUsageMetrics = basicProjectConfigIdAsStringProjectAiUsageAnalyticsDTOMap
											.get(projectBasicConfigIdAsString);
									projectAiUsageMetrics.setIssueCount(projectAiUsageMetrics.getIssueCount() + 1);
									projectAiUsageMetrics.setEfficiencyGain((double) Math.round((projectAiUsageMetrics
											.getEfficiencyGain()
											+ (jiraIssue.getAiEfficiencyGain() / TWO_DECIMAL_ROUNDING_COEFFICIENT))
											* PERCENTAGE_MULTIPLIER) / PERCENTAGE_MULTIPLIER);
								} else {
									basicProjectConfigIdAsStringProjectAiUsageAnalyticsDTOMap.put(
											projectBasicConfigIdAsString,
											new ProjectAiUsageMetrics(1,
													jiraIssue.getAiEfficiencyGain() / TWO_DECIMAL_ROUNDING_COEFFICIENT,
													projectDTO.getName()));
								}
							}
						});
			} else {
				log.warn("No AI usage analytics data could be found for project with basicProjectConfigID {}",
						projectDTO.getProjectBasicConfigId());
			}
		}
		return aiUsageTypeBasicProjectConfigIdAsStringProjectAiUsageAnalyticsDTOMap;
	}

	private static AiUsageAnalyticsResponseDTO constructAiUsageAnalyticsResponseDTO(List<ProjectDTO> projectDTOList,
			Map<String, List<JiraIssue>> basicProjectConfigIdAsStringJiraIssuesMap) {

		AiUsageAnalyticsSummaryDTO aiUsageAnalyticsSummaryDTO = new AiUsageAnalyticsSummaryDTO();

		Map<String, Map<String, ProjectAiUsageMetrics>> aiUsageTypeBasicProjectConfigIdAsStringProjectAiUsageAnalyticsDTOMap = createAiUsageTypeBasicProjectConfigIdAsStringProjectAiUsageAnalyticsDTOMap(
				projectDTOList, basicProjectConfigIdAsStringJiraIssuesMap);

		int aiUsageTypesNumber = aiUsageTypeBasicProjectConfigIdAsStringProjectAiUsageAnalyticsDTOMap.size();
		int projectsNumber = projectDTOList.size();

		aiUsageAnalyticsSummaryDTO.setUsageTypesNumber(aiUsageTypesNumber);
		aiUsageAnalyticsSummaryDTO.setProjectsNumber(projectsNumber);

		double totalEfficiencyGain = aiUsageTypeBasicProjectConfigIdAsStringProjectAiUsageAnalyticsDTOMap.values()
				.stream()
				.flatMap(
						basicProjectConfigIdAsStringProjectAiUsageAnalyticsDTOMap -> basicProjectConfigIdAsStringProjectAiUsageAnalyticsDTOMap
								.values().stream())
				.mapToDouble(ProjectAiUsageMetrics::getEfficiencyGain).sum();

		aiUsageAnalyticsSummaryDTO
				.setAverageEfficiencyGainPerAiUsageType((int) Math.round(totalEfficiencyGain / aiUsageTypesNumber));
		aiUsageAnalyticsSummaryDTO
				.setAverageEfficiencyGainPerProject((int) Math.round(totalEfficiencyGain / projectsNumber));

		List<AiUsageAnalyticsDTO> aiUsageAnalyticsDTOList = new ArrayList<>();

		for (Map.Entry<String, Map<String, ProjectAiUsageMetrics>> entry : aiUsageTypeBasicProjectConfigIdAsStringProjectAiUsageAnalyticsDTOMap
				.entrySet()) {
			AiUsageAnalyticsDTO aiUsageAnalyticsDTO = new AiUsageAnalyticsDTO();
			aiUsageAnalyticsDTO.setAiUsageType(entry.getKey());
			aiUsageAnalyticsDTO.setProjects(entry.getValue().values().stream().toList());
			aiUsageAnalyticsDTOList.add(aiUsageAnalyticsDTO);
		}

		return AiUsageAnalyticsResponseDTO.builder().summary(aiUsageAnalyticsSummaryDTO)
				.analytics(aiUsageAnalyticsDTOList).build();
	}

	private static List<ProjectDTO> createProjectDTOList(List<AccountFilteredData> projectsDataCurrentUserHasAccessTo,
			Set<String> requestedBasicProjectConfigIds) {
		List<ProjectDTO> projectDTOList;
		if (CollectionUtils.isEmpty(requestedBasicProjectConfigIds)) {
			projectDTOList = projectsDataCurrentUserHasAccessTo.stream()
					.map(accountFilteredData -> new ProjectDTO(accountFilteredData.getNodeDisplayName(),
							accountFilteredData.getBasicProjectConfigId()))
					.toList();
		} else {
			projectDTOList = projectsDataCurrentUserHasAccessTo.stream()
					.filter(accountFilteredData -> requestedBasicProjectConfigIds
							.contains(accountFilteredData.getBasicProjectConfigId().toString()))
					.map(accountFilteredData -> new ProjectDTO(accountFilteredData.getNodeDisplayName(),
							accountFilteredData.getBasicProjectConfigId()))
					.toList();
		}
		return projectDTOList;
	}

	private static void validateComputeAiUsageAnalyticsRequest(AiUsageAnalyticsRequestDTO aiUsageAnalyticsRequestDTO,
			List<AccountFilteredData> projectsDataCurrentUserHasAccessTo) {
		if (Objects.isNull(aiUsageAnalyticsRequestDTO)) {
			throw new BadRequestException("The AI usage request cannot be null");
		}

		if (CollectionUtils.isEmpty(projectsDataCurrentUserHasAccessTo)) {
			throw new ForbiddenException("Current user doesn't have access to any projects");
		}

		Set<String> projectBasicConfigIds = aiUsageAnalyticsRequestDTO.getProjectBasicConfigIds();
		if (CollectionUtils.isNotEmpty(projectBasicConfigIds)) {
			Set<String> basicProjectConfigIdsUserHasAccessTo = projectsDataCurrentUserHasAccessTo.stream()
					.filter(accountFilteredData -> Objects.nonNull(accountFilteredData.getBasicProjectConfigId())
							&& projectBasicConfigIds.contains(accountFilteredData.getBasicProjectConfigId().toString()))
					.map(accountFilteredData -> accountFilteredData.getBasicProjectConfigId().toString())
					.collect(Collectors.toSet());

			Set<String> invalidProjectBasicConfigIds = projectBasicConfigIds.stream().filter(
					projectBasicConfigId -> !basicProjectConfigIdsUserHasAccessTo.contains(projectBasicConfigId))
					.collect(Collectors.toSet());

			if (CollectionUtils.isNotEmpty(invalidProjectBasicConfigIds)) {
				throw new BadRequestException(String.format(
						"The current user doesn't have access to the project "
								+ "basic configs [%s] or they are not related to any project",
						String.join(",", invalidProjectBasicConfigIds)));
			}
		}
	}
}
