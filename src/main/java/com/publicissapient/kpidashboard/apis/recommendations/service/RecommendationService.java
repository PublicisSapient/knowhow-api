/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.recommendations.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.filter.service.OrganizationLookup;
import com.publicissapient.kpidashboard.apis.model.AccountFilterRequest;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.recommendations.dto.ProjectRecommendationDTO;
import com.publicissapient.kpidashboard.apis.recommendations.dto.RecommendationRequest;
import com.publicissapient.kpidashboard.apis.recommendations.dto.RecommendationResponseDTO;
import com.publicissapient.kpidashboard.apis.recommendations.dto.RecommendationSummaryDTO;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationsActionPlan;
import com.publicissapient.kpidashboard.common.repository.recommendation.RecommendationRepository;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for retrieving batch-calculated AI recommendations at organizational
 * hierarchy levels.
 * 
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

	private static final int LATEST_RECOMMENDATION_LIMIT = 1;

	private final RecommendationRepository recommendationRepository;
	private final AccountHierarchyServiceImpl accountHierarchyService;
	private final FilterHelperService filterHelperService;

	/**
	 * Holds hierarchy level metadata for recommendation queries.
	 *
	 * @param requestedLevel
	 *            the hierarchy level requested by user
	 * @param projectLevel
	 *            the project hierarchy level (lowest supported level)
	 */
	@Builder
	private record HierarchyLevelsData(HierarchyLevel requestedLevel, HierarchyLevel projectLevel) {
	}

	/**
	 * Encapsulates recommendation request context with hierarchy and organization
	 * data.
	 *
	 * @param hierarchyLevelsData
	 *            hierarchy level information
	 * @param organizationLookup
	 *            filtered organizational hierarchy for lookups
	 */
	@Builder
	private record RecommendationRequestData(HierarchyLevelsData hierarchyLevelsData,
			OrganizationLookup organizationLookup) {
	}

	/**
	 * Retrieves AI-generated recommendations for projects within a hierarchy level.
	 * 
	 *
	 * @param recommendationRequest
	 *            request containing levelName and optional parentNodeId
	 *
	 * @return ServiceResponse containing RecommendationResponseDTO
	 * @throws BadRequestException
	 *             if levelName is below project level
	 * @throws NotFoundException
	 *             if the hierarchy level does not exist
	 * @throws ForbiddenException
	 *             if user lacks access to hierarchy data
	 * @throws InternalServerErrorException
	 *             if multiple levels match or unexpected errors occur
	 */
	public ServiceResponse getRecommendationsForLevel(RecommendationRequest recommendationRequest) {
		validateRecommendationRequest(recommendationRequest);
		long startTime = System.currentTimeMillis();

		log.info("Started getting recommendations for level: {} with parentNodeId: {}",
				recommendationRequest.levelName(), recommendationRequest.parentNodeId());

		// Build request context with hierarchy metadata and organization lookup
		RecommendationRequestData requestData = createRecommendationRequestData(recommendationRequest.levelName(),
				recommendationRequest.parentNodeId());

		// Extract project config IDs from organizational hierarchy
		Set<String> projectConfigIds = extractProjectConfigIds(requestData, recommendationRequest.parentNodeId());

		if (CollectionUtils.isEmpty(projectConfigIds)) {
			log.warn("No projects found for level: {}", recommendationRequest.levelName());
			return buildEmptyResponse(recommendationRequest.levelName());
		}

		// Retrieve latest recommendation per project from batch data
		List<RecommendationsActionPlan> recommendations = getLatestRecommendationsForProjects(projectConfigIds);

		// Map entities to DTOs
		List<ProjectRecommendationDTO> recommendationDTOs = recommendations.stream()
				.map(this::mapToProjectRecommendationDTO).collect(Collectors.toList());

		// Build response with summary statistics
		RecommendationSummaryDTO summary = RecommendationSummaryDTO.builder()
				.levelName(recommendationRequest.levelName())
				.totalProjectsWithRecommendations(recommendationDTOs.size())
				.totalProjectsQueried(projectConfigIds.size()).totalRecommendations(recommendationDTOs.size())
				.message(String.format("Retrieved %d recommendations from %d projects at %s level",
						recommendationDTOs.size(), recommendationDTOs.size(), recommendationRequest.levelName()))
				.build();

		RecommendationResponseDTO response = RecommendationResponseDTO.builder().summary(summary)
				.details(recommendationDTOs).build();

		log.info("Successfully retrieved {} recommendations for {} projects at level: {}", recommendationDTOs.size(),
				projectConfigIds.size(), recommendationRequest.levelName());

		return new ServiceResponse(true, "Recommendations retrieved successfully", response);
	}

	/**
	 * Validates the recommendation request parameters.
	 *
	 * @param recommendationRequest
	 *            the request to validate
	 * @throws BadRequestException
	 *             if request is null or levelName is blank
	 */
	private void validateRecommendationRequest(RecommendationRequest recommendationRequest) {
		if (recommendationRequest == null) {
			throw new BadRequestException("Recommendation request cannot be null");
		}
		if (StringUtils.isBlank(recommendationRequest.levelName())) {
			throw new BadRequestException("The recommendation request 'levelName' is required");
		}
	}

	/**
	 * Creates recommendation request context with hierarchy validation and user
	 * access filtering.
	 *
	 * @param levelName
	 *            the hierarchy level name
	 * @param parentNodeId
	 *            optional parent node ID for filtering to specific subtree
	 * @return RecommendationRequestData with validated hierarchy and filtered
	 *         organization lookup
	 */
	private RecommendationRequestData createRecommendationRequestData(String levelName, String parentNodeId) {
		HierarchyLevelsData hierarchyLevelsData = validateAndConstructHierarchyLevels(levelName);
		OrganizationLookup organizationLookup = buildOrganizationLookupWithUserAccess(hierarchyLevelsData,
				parentNodeId);
		return RecommendationRequestData.builder().hierarchyLevelsData(hierarchyLevelsData)
				.organizationLookup(organizationLookup).build();
	}

	/**
	 * Constructs organizational hierarchy filtered by user access permissions.
	 * Retrieves cached Scrum project data with closed sprints only.
	 *
	 * @param hierarchyLevelsData
	 *            hierarchy level metadata
	 * @param parentNodeId
	 *            optional parent node ID for filtering to specific subtree
	 * @return OrganizationLookup for hierarchy traversal
	 * @throws ForbiddenException
	 *             if user has no access to hierarchy data
	 * @throws BadRequestException
	 *             if parentNodeId is invalid or user lacks access to it
	 */
	private OrganizationLookup buildOrganizationLookupWithUserAccess(HierarchyLevelsData hierarchyLevelsData,
			String parentNodeId) {
		AccountFilterRequest accountFilterRequest = new AccountFilterRequest();
		accountFilterRequest.setKanban(false);
		accountFilterRequest.setSprintIncluded(List.of(CommonConstant.CLOSED.toUpperCase()));

		log.debug("Fetching hierarchy data from cache with user access filtering");
		Set<AccountFilteredData> userAccessibleHierarchyData = accountHierarchyService
				.getFilteredList(accountFilterRequest).stream()
				.filter(data -> data != null && StringUtils.isNotEmpty(data.getNodeId())
						&& data.getLevel() <= hierarchyLevelsData.projectLevel.getLevel())
				.collect(Collectors.toSet());

		if (CollectionUtils.isEmpty(userAccessibleHierarchyData)) {
			throw new ForbiddenException("Current user doesn't have access to any hierarchy data");
		}

		OrganizationLookup organizationLookup = new OrganizationLookup(userAccessibleHierarchyData);

		// Validate parentNodeId if provided
		if (StringUtils.isNotBlank(parentNodeId)) {
			List<AccountFilteredData> parentNodeData = organizationLookup.getAccountDataByNodeId(parentNodeId);
			if (CollectionUtils.isEmpty(parentNodeData)) {
				throw new BadRequestException(
						String.format("Current user doesn't have access to the parent node id %s or it does not exist",
								parentNodeId));
			}
			if (parentNodeData.size() > 1) {
				throw new BadRequestException(String.format(
						"Multiple organization entities are corresponding with the parent node id '%s'", parentNodeId));
			}
			if (parentNodeData.stream()
					.anyMatch(data -> hierarchyLevelsData.requestedLevel.getLevel() <= data.getLevel())) {
				throw new BadRequestException(String.format(
						"The requested level name '%s' is not corresponding to a child level of the parent node",
						hierarchyLevelsData.requestedLevel.getHierarchyLevelName()));
			}
		}

		return organizationLookup;
	}

	/**
	 * Validates and constructs hierarchy level metadata for the requested level.
	 * Ensures level exists, is unique, and is at or above project level.
	 *
	 * @param levelName
	 *            the requested hierarchy level name
	 * @return HierarchyLevelsData with requested and project levels
	 * @throws BadRequestException
	 *             if level is below project level
	 * @throws NotFoundException
	 *             if level does not exist
	 * @throws InternalServerErrorException
	 *             if multiple levels match or project level not found
	 */
	private HierarchyLevelsData validateAndConstructHierarchyLevels(String levelName) {
		Map<String, HierarchyLevel> allHierarchyLevels = filterHelperService.getHierarchyLevelMap(false);
		log.debug("Retrieved {} hierarchy levels from cache", allHierarchyLevels.size());

		if (hasMultipleLevelsWithSameName(levelName, allHierarchyLevels)) {
			throw new InternalServerErrorException(
					String.format("Multiple hierarchy levels found for name '%s'", levelName));
		}

		HierarchyLevel requestedLevel = accountHierarchyService.getHierarchyLevelByLevelName(levelName)
				.orElseThrow(() -> new NotFoundException(String.format("Level '%s' does not exist", levelName)));

		HierarchyLevel projectLevel = accountHierarchyService
				.getHierarchyLevelByLevelId(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT)
				.orElseThrow(() -> new InternalServerErrorException("Could not find project hierarchy level"));

		if (isLevelBelowProject(requestedLevel, projectLevel)) {
			throw new BadRequestException(
					String.format("Level '%s' is below project level and not supported", levelName));
		}

		return HierarchyLevelsData.builder().requestedLevel(requestedLevel).projectLevel(projectLevel).build();
	}

	/**
	 * Checks if requested level is below project level in hierarchy. Lower numbers
	 * = higher in hierarchy (e.g., 1=org, 5=project, 6=sprint).
	 *
	 * @param requestedLevel
	 *            the requested hierarchy level
	 * @param projectLevel
	 *            the project hierarchy level
	 * @return true if requested level is below project (not supported)
	 */
	private boolean isLevelBelowProject(HierarchyLevel requestedLevel, HierarchyLevel projectLevel) {
		return requestedLevel.getLevel() > projectLevel.getLevel();
	}

	/**
	 * Checks if multiple hierarchy levels have the same name.
	 *
	 * @param levelName
	 *            the level name to check
	 * @param allHierarchyLevels
	 *            all hierarchy levels in the system
	 * @return true if multiple levels match the name
	 */
	private boolean hasMultipleLevelsWithSameName(String levelName, Map<String, HierarchyLevel> allHierarchyLevels) {
		return allHierarchyLevels.values().stream()
				.filter(level -> StringUtils.isNotEmpty(level.getHierarchyLevelName())
						&& level.getHierarchyLevelName().equalsIgnoreCase(levelName))
				.count() > 1;
	}

	/**
	 * Extracts project configuration IDs from organizational hierarchy. Converts
	 * AccountFilteredData.basicProjectConfigId to hex strings for repository
	 * queries. When parentNodeId is provided, only returns projects under that
	 * parent node.
	 *
	 * @param requestData
	 *            recommendation request with hierarchy and organization data
	 * @param parentNodeId
	 *            optional parent node ID for filtering to specific subtree
	 * @return Set of basicProjectConfigId strings (ObjectId hex format)
	 */
	private Set<String> extractProjectConfigIds(RecommendationRequestData requestData, String parentNodeId) {
		Map<String, List<AccountFilteredData>> projectsByParentNode;

		if (StringUtils.isNotBlank(parentNodeId)) {
			// Get children of specific parent node at requested level
			List<AccountFilteredData> childrenAtRequestedLevel = requestData.organizationLookup()
					.getChildrenByParentNodeId(parentNodeId, requestData.hierarchyLevelsData.requestedLevel.getLevel());

			// Get all projects under those children nodes
			Set<String> childrenNodeIds = childrenAtRequestedLevel.stream().map(AccountFilteredData::getNodeId)
					.collect(Collectors.toSet());
			projectsByParentNode = requestData.organizationLookup().getChildrenGroupedByParentNodeIds(childrenNodeIds,
					requestData.hierarchyLevelsData.projectLevel.getLevel());
		} else {
			// Get all projects at requested level
			projectsByParentNode = requestData.organizationLookup().getChildrenGroupedByParentNodeIds(
					requestData.hierarchyLevelsData.requestedLevel.getLevel(),
					requestData.hierarchyLevelsData.projectLevel.getLevel());
		}

		return projectsByParentNode.values().stream().flatMap(Collection::stream)
				.map(AccountFilteredData::getBasicProjectConfigId).filter(Objects::nonNull).map(ObjectId::toHexString)
				.collect(Collectors.toSet());
	}

	/**
	 * Creates empty response when no projects are found.
	 *
	 * @param levelName
	 *            the organizational level name
	 * @return ServiceResponse with zero counts and empty details list
	 */
	private ServiceResponse buildEmptyResponse(String levelName) {
		RecommendationSummaryDTO summary = RecommendationSummaryDTO.builder().levelName(levelName)
				.totalProjectsWithRecommendations(0).totalProjectsQueried(0).totalRecommendations(0)
				.message("No projects found for the specified criteria").build();

		RecommendationResponseDTO response = RecommendationResponseDTO.builder().summary(summary)
				.details(new ArrayList<>()).build();

		return new ServiceResponse(true, "No projects found", response);
	}

	/**
	 * Retrieves latest recommendation per project using MongoDB aggregation.
	 * Optimized to fetch only the most recent recommendation per project at
	 * database level.
	 *
	 * @param projectConfigIds
	 *            Set of project configuration IDs
	 * @return List of RecommendationsActionPlan (one per project)
	 */
	private List<RecommendationsActionPlan> getLatestRecommendationsForProjects(Set<String> projectConfigIds) {
		log.debug("Fetching latest recommendation for {} projects", projectConfigIds.size());

		List<RecommendationsActionPlan> recommendations = recommendationRepository
				.findLatestRecommendationsByProjectIds(new ArrayList<>(projectConfigIds), LATEST_RECOMMENDATION_LIMIT);

		log.debug("Retrieved {} recommendations from database", recommendations.size());
		return recommendations;
	}

	/**
	 * Maps recommendation entity to API response DTO.
	 *
	 * @param entity
	 *            the RecommendationsActionPlan entity from database
	 * @return ProjectRecommendationDTO for API response
	 * @throws IllegalStateException
	 *             if entity has null recommendations field
	 */
	private ProjectRecommendationDTO mapToProjectRecommendationDTO(RecommendationsActionPlan entity) {
		if (entity.getRecommendations() == null) {
			log.error("Entity {} has null recommendations field for project {}", entity.getId(),
					entity.getBasicProjectConfigId());
			throw new IllegalStateException(
					String.format("Missing recommendations for project %s", entity.getBasicProjectConfigId()));
		}

		return ProjectRecommendationDTO.builder().id(entity.getId().toString())
				.projectId(entity.getBasicProjectConfigId()).projectName(entity.getProjectName())
				.persona(entity.getPersona()).level(entity.getLevel()).recommendations(entity.getRecommendations())
				.metadata(entity.getMetadata()).createdAt(entity.getCreatedAt()).build();
	}
}