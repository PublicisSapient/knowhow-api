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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.analysis.analytics.shared.dto.BaseAnalyticsRequestDTO;
import com.publicissapient.kpidashboard.apis.analysis.analytics.shared.utils.AnalyticsValidationUtils;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.ProjectSprintMetrics;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.SprintAnalyticsResponseDTO;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.dto.SprintMetricDTO;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.enums.SprintMetricType;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.factory.SprintMetricStrategyFactory;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.model.SprintMetricContext;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.service.SprintAnalyticsService;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.strategy.SprintMetricStrategy;
import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of Sprint Analytics Service Orchestrates the calculation of
 * multiple sprint metrics across projects
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SprintAnalyticsServiceImpl implements SprintAnalyticsService {

	private final JiraIssueRepository jiraIssueRepository;
	private final ConfigHelperService configHelperService;
	private final SprintDetailsService sprintDetailsService;
	private final SprintMetricStrategyFactory strategyFactory;
	private final AccountHierarchyServiceImpl accountHierarchyServiceImpl;
	private final JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;

	@Override
	public ServiceResponse computeSprintAnalyticsData(BaseAnalyticsRequestDTO request) {

		// Validate project access for current user
		List<AccountFilteredData> projectsDataCurrentUserHasAccessTo = accountHierarchyServiceImpl
				.getHierarchyDataCurrentUserHasAccessTo(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT);
		AnalyticsValidationUtils.validateBaseAnalyticsComputationRequest(request, projectsDataCurrentUserHasAccessTo);

		log.info("Starting sprint analytics computation: {} projects, {} sprints requested",
				request.getProjectBasicConfigIds().size(), request.getNumberOfSprintsToInclude());

		// Initialize response with warnings list
		SprintAnalyticsResponseDTO response = SprintAnalyticsResponseDTO.builder().warnings(new ArrayList<>()).build();

		// Compute metrics
		response.setAnalytics(computeAllMetrics(request, response));

		// Determine response message based on warnings
		String message;
		if (response.getWarnings().isEmpty()) {
			message = "Sprint analytics computed successfully";
			log.info("Completed sprint analytics computation successfully for all {} projects",
					request.getProjectBasicConfigIds().size());
		} else {
			message = String.format("Sprint analytics completed with %d warning(s)", response.getWarnings().size());
			log.warn("Completed sprint analytics with {} warnings: {}", response.getWarnings().size(),
					response.getWarnings());
		}

		return new ServiceResponse(true, message, response);
	}

	/**
	 * Compute all enabled sprint metrics for all projects.
	 *
	 * @param request
	 *            Sprint Analytics request
	 * @param response
	 *            Response DTO
	 * @return List of computed metrics
	 */
	private List<SprintMetricDTO> computeAllMetrics(BaseAnalyticsRequestDTO request,
			SprintAnalyticsResponseDTO response) {
		List<SprintMetricDTO> allMetrics = new ArrayList<>();

		// Get all enabled metric types
		List<SprintMetricType> enabledSprintMetrics = strategyFactory.getEnabledMetricTypes();
		log.info("Computing {} enabled metrics: {}", enabledSprintMetrics.size(),
				enabledSprintMetrics.stream().map(SprintMetricType::getDisplayName).collect(Collectors.toList()));

		int metricSuccessCount = 0;
		int metricFailureCount = 0;

		for (SprintMetricType metricType : enabledSprintMetrics) {
			try {
				SprintMetricDTO metricDTO = computeMetricForAllProjects(metricType, request, response);
				allMetrics.add(metricDTO);
				metricSuccessCount++;
			} catch (Exception e) {
				metricFailureCount++;
				log.error("Failed to compute metric: {} - Error: {}", metricType.getDisplayName(), e.getMessage(), e);
				response.addWarning(String.format("Failed to compute metric '%s': %s", metricType.getDisplayName(),
						e.getMessage()));
			}
		}

		log.info("Metrics computation summary: {}/{} successful, {} failed", metricSuccessCount,
				enabledSprintMetrics.size(), metricFailureCount);

		return allMetrics;
	}

	/**
	 * Compute a specific metric for all requested projects with per-project error
	 * isolation
	 *
	 * @param metricType
	 *            Type of metric to compute
	 * @param request
	 *            Analytics request
	 * @param response
	 *            Response DTO to add warnings to
	 * @return Computed metric DTO
	 */
	private SprintMetricDTO computeMetricForAllProjects(SprintMetricType metricType, BaseAnalyticsRequestDTO request,
			SprintAnalyticsResponseDTO response) {

		SprintMetricStrategy sprintMetricStrategy = strategyFactory.getStrategy(metricType);
		List<ProjectSprintMetrics> projectSprintMetricsList = new ArrayList<>();
		int successCount = 0;
		int failureCount = 0;

		for (String projectIdStr : request.getProjectBasicConfigIds()) {
			try {
				ObjectId projectBasicConfigId = new ObjectId(projectIdStr);

				// Build sprintMetricContext for this project
				SprintMetricContext sprintMetricContext = buildContext(projectBasicConfigId,
						request.getNumberOfSprintsToInclude());

				// Calculate metric for this project
				ProjectSprintMetrics projectSprintMetrics = sprintMetricStrategy.calculate(sprintMetricContext);
				projectSprintMetricsList.add(projectSprintMetrics);
				successCount++;

				log.debug("Successfully computed {} for project: {} [{}]", metricType.getDisplayName(),
						sprintMetricContext.getProjectName(), projectIdStr);

			} catch (IllegalArgumentException e) {
				failureCount++;
				log.warn("Invalid project ID format: {} - Metric: {}", projectIdStr, metricType.getDisplayName());
				response.addWarning(String.format("Skipped project '%s': Invalid ID format", projectIdStr));
			} catch (EntityNotFoundException e) {
				failureCount++;
				log.warn("Configuration not found for project: {} - Metric: {} - {}", projectIdStr,
						metricType.getDisplayName(), e.getMessage());
				response.addWarning(String.format("Skipped project '%s': %s", projectIdStr, e.getMessage()));
			} catch (Exception e) {
				failureCount++;
				log.error("Failed to compute {} for project: {}", metricType.getDisplayName(), projectIdStr, e);
				response.addWarning(String.format("Failed to compute %s for project '%s': %s",
						metricType.getDisplayName(), projectIdStr, e.getMessage()));
			}
		}

		log.info("Computed {} for {}/{} projects (Success: {}, Failed: {})", metricType.getDisplayName(), successCount,
				request.getProjectBasicConfigIds().size(), successCount, failureCount);

		return SprintMetricDTO.builder().metric(metricType.getDisplayName()).metricType(metricType.name())
				.projects(projectSprintMetricsList).build();
	}

	/**
	 * Build context object with all required data for metric calculation
	 */
	private SprintMetricContext buildContext(ObjectId projectId, int numberOfSprints) throws EntityNotFoundException {
		log.debug("Building context for project {} with {} sprints", projectId, numberOfSprints);

		// 1. Fetch project basic config
		ProjectBasicConfig projectBasicConfig = fetchProjectBasicConfig(projectId);
		String projectName = projectBasicConfig.getProjectDisplayName();

		// 2. Fetch sprint details (sorted by completed date desc)
		List<SprintDetails> sprints = fetchSprintDetails(projectId, numberOfSprints, projectName);
		if (CollectionUtils.isEmpty(sprints)) {
			log.warn("No completed sprints found for project: {} [{}]", projectName, projectId);
			return createEmptyContext(projectId, numberOfSprints, projectName);
		}

		// 3. Extract all unique issue numbers from all sprints
		Set<String> issueNumbers = extractIssueNumbersFromSprints(sprints, projectName);
		if (CollectionUtils.isEmpty(issueNumbers)) {
			log.warn("No issues found in {} sprints for project: {} [{}]", sprints.size(), projectName, projectId);
			return SprintMetricContext.builder().basicProjectConfigId(projectId).projectName(projectName)
					.sprintDetailsList(sprints).jiraIssueMap(new HashMap<>()).historyMap(new HashMap<>())
					.fieldMapping(fetchFieldMapping(projectId)).numberOfSprints(numberOfSprints).build();
		}

		// 4. Fetch ONLY the issues that belong to these sprints
		List<JiraIssue> issues = fetchJiraIssuesByNumbers(projectId, issueNumbers, projectName);

		// 5. Fetch ONLY the custom histories for these issues
		List<JiraIssueCustomHistory> histories = fetchJiraIssueCustomHistoriesByStoryIds(projectId, issueNumbers,
				projectName);

		// 6. Build Maps for O(1) lookup
		Map<String, JiraIssue> jiraIssueMap = buildJiraIssueMap(issues);
		Map<String, JiraIssueCustomHistory> historyMap = buildHistoryMap(histories);

		// 7. Fetch field mapping
		FieldMapping fieldMapping = fetchFieldMapping(projectId);

		log.debug("Context built for project: {} - Sprints: {}, Issues: {}, Histories: {}", projectName, sprints.size(),
				jiraIssueMap.size(), historyMap.size());

		return SprintMetricContext.builder().basicProjectConfigId(projectId).projectName(projectName)
				.sprintDetailsList(sprints).jiraIssueMap(jiraIssueMap).historyMap(historyMap).fieldMapping(fieldMapping)
				.numberOfSprints(numberOfSprints).build();
	}

	/**
	 * Extract all unique issue numbers from sprint details Issues are present in:
	 * totalIssues, completedIssues, notCompletedIssues, etc.
	 *
	 * @param sprintDetailsList
	 *            List of sprint details
	 * @param projectName
	 *            Project name for logging
	 * @return Set of unique issue numbers (e.g., "DTS-50132")
	 */
	private Set<String> extractIssueNumbersFromSprints(List<SprintDetails> sprintDetailsList, String projectName) {
		Set<String> issueNumbers = new HashSet<>();

		for (SprintDetails sprint : sprintDetailsList) {

			// Extract from totalIssues
			if (CollectionUtils.isNotEmpty(sprint.getTotalIssues())) {
				sprint.getTotalIssues().stream().filter(issue -> issue != null && issue.getNumber() != null)
						.map(SprintIssue::getNumber).forEach(issueNumbers::add);
			}

			if (CollectionUtils.isNotEmpty(sprint.getPuntedIssues())) {
				sprint.getPuntedIssues().stream().filter(issue -> issue != null && issue.getNumber() != null)
						.map(SprintIssue::getNumber).forEach(issueNumbers::add);
			}
			if (CollectionUtils.isNotEmpty(sprint.getAddedIssues())) {
				sprint.getAddedIssues().stream().filter(Objects::nonNull).forEach(issueNumbers::add);
			}
		}

		log.debug("Extracted {} unique issue numbers from {} sprints for project: {}", issueNumbers.size(),
				sprintDetailsList.size(), projectName);
		return issueNumbers;
	}

	/** Create empty context with project name */
	private SprintMetricContext createEmptyContext(ObjectId projectId, int numberOfSprints, String projectName) {
		return SprintMetricContext.builder().basicProjectConfigId(projectId).projectName(projectName)
				.sprintDetailsList(new ArrayList<>()).jiraIssueMap(new HashMap<>()).historyMap(new HashMap<>())
				.fieldMapping(null).numberOfSprints(numberOfSprints).build();
	}

	/**
	 * Build Map of JiraIssues by issue number for O(1) lookup
	 * 
	 * @param issues
	 *            List of Jira issues
	 * @return Map of issue number to JiraIssue
	 */
	private Map<String, JiraIssue> buildJiraIssueMap(List<JiraIssue> issues) {
		if (CollectionUtils.isEmpty(issues)) {
			return new HashMap<>();
		}

		return issues.stream().filter(issue -> issue != null && issue.getNumber() != null)
				.collect(Collectors.toMap(JiraIssue::getNumber, issue -> issue, (existing, replacement) -> existing));
	}

	/**
	 * Build Map of JiraIssueCustomHistory by story ID for O(1) lookup
	 * 
	 * @param histories
	 *            List of Jira issue custom histories
	 * @return Map of story ID to JiraIssueCustomHistory
	 */
	private Map<String, JiraIssueCustomHistory> buildHistoryMap(List<JiraIssueCustomHistory> histories) {
		if (CollectionUtils.isEmpty(histories)) {
			return new HashMap<>();
		}

		return histories.stream().filter(history -> history != null && history.getStoryID() != null).collect(Collectors
				.toMap(JiraIssueCustomHistory::getStoryID, history -> history, (existing, replacement) -> existing));
	}

	/**
	 * Fetch sprint details for a project (sorted by completed date descending)
	 *
	 * @param projectId
	 *            Project basic config ID
	 * @param numberOfSprints
	 *            Number of sprints to fetch
	 * @param projectName
	 *            Project name for logging
	 * @return List of sprint details
	 */
	private List<SprintDetails> fetchSprintDetails(ObjectId projectId, int numberOfSprints, String projectName) {
		try {
			List<SprintDetails> sprints = sprintDetailsService.findByBasicProjectConfigIdInByCompletedDateDesc(
					Collections.singletonList(projectId), numberOfSprints);

			if (CollectionUtils.isEmpty(sprints)) {
				log.info("No completed sprints found for project: {} [{}]", projectName, projectId);
				return new ArrayList<>();
			}

			if (sprints.size() < numberOfSprints) {
				log.info("Retrieved {}/{} requested sprints for project: {} [{}]", sprints.size(), numberOfSprints,
						projectName, projectId);
			} else {
				log.debug("Retrieved {} sprints for project: {} [{}]", sprints.size(), projectName, projectId);
			}
			return sprints;

		} catch (Exception e) {
			log.error("Failed to fetch sprint details for project: {} [{}]", projectName, projectId, e);
			return new ArrayList<>();
		}
	}

	/**
	 * Fetch project basic config
	 *
	 * @param projectId
	 *            Project basic config ID
	 * @return ProjectBasicConfig
	 */
	private ProjectBasicConfig fetchProjectBasicConfig(ObjectId projectId) throws EntityNotFoundException {
		return Optional.ofNullable(configHelperService.getProjectConfigMap().get(projectId.toString()))
				.orElseThrow(() -> {
					log.error("Project basic config not found for : {}", projectId);
					return new EntityNotFoundException(ProjectBasicConfig.class, "projectId", projectId.toString());
				});
	}

	/**
	 * Fetch jira issues by issue numbers
	 *
	 * @param projectId
	 *            Project basic config ID
	 * @param issueNumbers
	 *            Set of issue numbers to fetch
	 * @param projectName
	 *            Project name for logging
	 * @return List of jira issues
	 */
	private List<JiraIssue> fetchJiraIssuesByNumbers(ObjectId projectId, Set<String> issueNumbers, String projectName) {
		try {
			if (CollectionUtils.isEmpty(issueNumbers)) {
				return new ArrayList<>();
			}

			List<JiraIssue> issues = jiraIssueRepository
					.findByNumberInAndBasicProjectConfigId(new ArrayList<>(issueNumbers), projectId.toString());

			if (CollectionUtils.isEmpty(issues)) {
				log.warn("No jira issue details found for {} issue numbers in project: {} [{}]", issueNumbers.size(),
						projectName, projectId);
				return new ArrayList<>();
			}

			int missingCount = issueNumbers.size() - issues.size();
			if (missingCount > 0) {
				log.info("Retrieved {}/{} jira issues ({} missing) for project: {} [{}]", issues.size(),
						issueNumbers.size(), missingCount, projectName, projectId);
			} else {
				log.debug("Retrieved {} jira issues for project: {} [{}]", issues.size(), projectName, projectId);
			}
			return issues;

		} catch (Exception e) {
			log.error("Failed to fetch jira issues for project: {} [{}]", projectName, projectId, e);
			return new ArrayList<>();
		}
	}

	/**
	 * Fetch jira issue custom histories by story IDs
	 *
	 * @param projectId
	 *            Project basic config ID
	 * @param storyIds
	 *            Set of story IDs (issue numbers) to fetch
	 * @param projectName
	 *            Project name for logging
	 * @return List of jira issue custom histories
	 */
	private List<JiraIssueCustomHistory> fetchJiraIssueCustomHistoriesByStoryIds(ObjectId projectId,
			Set<String> storyIds, String projectName) {
		try {
			if (CollectionUtils.isEmpty(storyIds)) {
				return new ArrayList<>();
			}

			List<JiraIssueCustomHistory> histories = jiraIssueCustomHistoryRepository
					.findByStoryIDInAndBasicProjectConfigIdIn(new ArrayList<>(storyIds),
							Collections.singletonList(projectId.toString()));

			if (CollectionUtils.isEmpty(histories)) {
				log.info("No custom history found for {} story IDs in project: {} [{}]", storyIds.size(), projectName,
						projectId);
				return new ArrayList<>();
			}

			int missingCount = storyIds.size() - histories.size();
			if (missingCount > 0) {
				log.debug("Retrieved {}/{} custom histories ({} missing) for project: {} [{}]", histories.size(),
						storyIds.size(), missingCount, projectName, projectId);
			} else {
				log.debug("Retrieved {} custom histories for project: {} [{}]", histories.size(), projectName,
						projectId);
			}
			return histories;

		} catch (Exception e) {
			log.error("Failed to fetch custom histories for project: {} [{}]", projectName, projectId, e);
			return new ArrayList<>();
		}
	}

	/**
	 * Fetch field mapping for a project
	 *
	 * @param basicProjectConfigId
	 *            Project Basic Config ID
	 * @return FieldMapping
	 */
	private FieldMapping fetchFieldMapping(ObjectId basicProjectConfigId) throws EntityNotFoundException {
		return Optional.ofNullable(configHelperService.getFieldMapping(basicProjectConfigId)).orElseThrow(() -> {
			log.error("Field mapping not found for project: {}", basicProjectConfigId);
			return new EntityNotFoundException(FieldMapping.class, "basicProjectConfigId",
					basicProjectConfigId.toString());
		});
	}

}
