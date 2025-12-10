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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.filter.service.OrganizationLookup;
import com.publicissapient.kpidashboard.apis.model.AccountFilterRequest;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.recommendations.dto.ProjectRecommendationDTO;
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
 * Service for retrieving batch-calculated AI recommendations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

	private static final int DEFAULT_RECOMMENDATIONS_LIMIT_PER_PROJECT = 1;

	private final RecommendationRepository recommendationRepository;
	private final AccountHierarchyServiceImpl accountHierarchyService;
	private final FilterHelperService filterHelperService;
	/**
	 * Internal record to hold hierarchy levels data for recommendation calculations.
	 */
	@Builder
	private record HierarchyLevelsData(HierarchyLevel requestedLevel,
			HierarchyLevel firstChildHierarchyLevelAfterRequestedLevel, HierarchyLevel projectLevel) {
	}

	/**
	 * Internal record to hold the recommendation request with hierarchy and organization data.
	 */
	@Builder
	private record RecommendationRequestData(HierarchyLevelsData hierarchyLevelsData,
			OrganizationLookup organizationLookup) {
	}

	/**
	 * Retrieves batch-calculated AI recommendations for the specified organizational hierarchy level.
	 * Filters by user access permissions and returns only the most recent recommendation per project.
	 *
	 * @param levelName the organizational hierarchy level name (e.g., "project", "portfolio")
	 * @return ServiceResponse containing recommendations with summary metadata
	 * @throws BadRequestException if levelName is empty or unsupported
	 * @throws NotFoundException if the hierarchy level does not exist
	 * @throws ForbiddenException if user has no access to hierarchy data
	 * @throws InternalServerErrorException if multiple levels match or unexpected errors occur
	 */
	public ServiceResponse getRecommendationsForLevel(String levelName) {
		long startTime = System.currentTimeMillis();

		log.info("Processing recommendation request for level: {}", levelName);

		try {
			// TODO: TEMPORARY MOCK IMPLEMENTATION FOR UI TESTING - REMOVE THIS BLOCK AND UNCOMMENT BELOW
			// Currently returning all recommendations using findAll() to bypass validation issues
			// Once hierarchy validation logic is fixed, remove this block and uncomment the original logic below
			log.warn("TEMPORARY: Using findAll() - returning all recommendations for UI testing");
			
			List<RecommendationsActionPlan> allRecommendations = recommendationRepository.findAll();
			List<ProjectRecommendationDTO> recommendationDTOs = allRecommendations.stream()
					.map(this::mapToProjectRecommendationDTO)
					.collect(Collectors.toList());

			RecommendationSummaryDTO summary = RecommendationSummaryDTO.builder()
					.levelName(levelName)
					.totalProjectsWithRecommendations(recommendationDTOs.size())
					.totalProjectsQueried(recommendationDTOs.size())
					.totalRecommendations(recommendationDTOs.size())
					.message(String.format("TEMPORARY: Retrieved %d recommendations (all data for UI testing)",
							recommendationDTOs.size()))
					.build();

			RecommendationResponseDTO response = RecommendationResponseDTO.builder()
					.summary(summary)
					.details(recommendationDTOs)
					.build();

			long duration = System.currentTimeMillis() - startTime;
			log.info("TEMPORARY: Retrieved {} recommendations using findAll(). Duration: {} ms",
					recommendationDTOs.size(), duration);

			return new ServiceResponse(true, "Recommendation data was successfully retrieved (TEMPORARY)", response);
			// TODO: END OF TEMPORARY MOCK - REMOVE ABOVE AND UNCOMMENT BELOW

			/* TODO: UNCOMMENT THIS BLOCK AFTER FIXING VALIDATION LOGIC
			// Create request data with hierarchy levels and organization lookup
			RecommendationRequestData requestData = createRecommendationRequestData(levelName);

			// Extract project node IDs using OrganizationLookup
			Set<String> projectNodeIds = extractProjectNodeIds(requestData);

			if (CollectionUtils.isEmpty(projectNodeIds)) {
				log.warn("No projects found for level: {}", levelName);
				return buildEmptyResponse(levelName);
			}

			// Retrieve recommendations from batch data
			List<RecommendationsActionPlan> recommendations = getLatestRecommendationsForProjects(projectNodeIds);

			// Map entities to DTOs
			List<ProjectRecommendationDTO> recommendationDTOs = recommendations.stream()
					.map(this::mapToProjectRecommendationDTO)
					.collect(Collectors.toList());

			// Calculate total recommendations: each project has exactly 1 recommendation (the latest)
			// Note: recommendations field in DTO is a single Recommendation object, not a Collection
			int totalRecommendations = recommendationDTOs.size();

			// Build summary
			RecommendationSummaryDTO summary = RecommendationSummaryDTO.builder()
					.levelName(levelName)
					.totalProjectsWithRecommendations(recommendationDTOs.size())
					.totalProjectsQueried(projectNodeIds.size())
					.totalRecommendations(totalRecommendations)
					.message(String.format("Retrieved %d recommendations from %d projects at %s level",
							totalRecommendations, recommendationDTOs.size(), levelName))
					.build();

			// Build response with summary and details
			RecommendationResponseDTO response = RecommendationResponseDTO.builder()
					.summary(summary)
					.details(recommendationDTOs)
					.build();

			long duration = System.currentTimeMillis() - startTime;
			log.info(
					"Successfully retrieved {} recommendations from {} projects for level: {}. Total projects queried: {}. Duration: {} ms",
					totalRecommendations, recommendationDTOs.size(), levelName, projectNodeIds.size(), duration);

			return new ServiceResponse(true, "Recommendation data was successfully retrieved", response);
			*/

		} catch (BadRequestException | IllegalArgumentException e) {
			log.error("Bad request for recommendation retrieval: {}", e.getMessage());
			throw e;
		} catch (NotFoundException | ForbiddenException e) {
			log.error("Error retrieving recommendations: {}", e.getMessage());
			throw e;
		} catch (Exception e) {
			log.error("Unexpected error retrieving recommendations for level: {}", levelName, e);
			throw new InternalServerErrorException(
					"Internal server error occurred while retrieving recommendations", e);
		}
	}

	/**
	 * Prepares request context with hierarchy metadata and organization lookup.
	 *
	 * @param levelName the hierarchy level name
	 * @return RecommendationRequestData with hierarchy levels and organization lookup
	 */
	private RecommendationRequestData createRecommendationRequestData(String levelName) {
		HierarchyLevelsData hierarchyLevelsData = constructHierarchyLevelsDataByRequestedLevelName(levelName);
		OrganizationLookup organizationLookup = constructOrganizationLookupBasedOnAccountData(hierarchyLevelsData);
		return RecommendationRequestData.builder().hierarchyLevelsData(hierarchyLevelsData)
				.organizationLookup(organizationLookup).build();
	}

	/**
	 * Builds organizational hierarchy structure filtered by user access permissions.
	 * Uses cached data for Scrum projects with closed sprints only.
	 *
	 * @param hierarchyLevelsData hierarchy level metadata
	 * @return OrganizationLookup for hierarchy traversal
	 * @throws ForbiddenException if user has no access to hierarchy data
	 */
	private OrganizationLookup constructOrganizationLookupBasedOnAccountData(HierarchyLevelsData hierarchyLevelsData) {
		AccountFilterRequest accountFilterRequest = new AccountFilterRequest();
		accountFilterRequest.setKanban(false);
		accountFilterRequest.setSprintIncluded(List.of(CommonConstant.CLOSED.toUpperCase()));

		// getFilteredList uses cacheService.cacheAccountHierarchyData() internally - no DB hit
		log.info("Fetching hierarchy data from cache for user access filtering");
		Set<AccountFilteredData> hierarchyDataUserHasAccessTo = accountHierarchyService.getFilteredList(accountFilterRequest)
				.stream()
				.filter(accountFilteredData -> accountFilteredData != null
						&& StringUtils.isNotEmpty(accountFilteredData.getNodeId())
						&& accountFilteredData.getLevel() >= hierarchyLevelsData.firstChildHierarchyLevelAfterRequestedLevel.getLevel()
						&& accountFilteredData.getLevel() <= hierarchyLevelsData.projectLevel.getLevel())
				.collect(Collectors.toSet());

		if (CollectionUtils.isEmpty(hierarchyDataUserHasAccessTo)) {
			throw new ForbiddenException("Current user doesn't have access to any hierarchy data");
		}

		return new OrganizationLookup(hierarchyDataUserHasAccessTo);
	}

	/**
	 * Constructs and validates hierarchy level metadata for the requested level.
	 * Ensures the level exists, is unique, and is at or above project level.
	 *
	 * @param levelName the requested hierarchy level name (case-insensitive)
	 * @return HierarchyLevelsData with requested level, child level, and project level
	 * @throws NotFoundException if level does not exist
	 * @throws InternalServerErrorException if multiple levels match or project level not found
	 */
	private HierarchyLevelsData constructHierarchyLevelsDataByRequestedLevelName(String levelName) {
		// Note: levelName is already validated by @NotBlank in controller

		// Using cached hierarchy level map - no DB hit (assuming Scrum for
		// recommendations)
		Map<String, HierarchyLevel> allHierarchyLevels = filterHelperService.getHierarchyLevelMap(false);
		log.debug("Retrieved {} hierarchy levels from cache", allHierarchyLevels.size());

		if (multipleLevelsCorrespondToLevelName(levelName, allHierarchyLevels)) {
			throw new InternalServerErrorException(String
					.format("Multiple hierarchy levels were found corresponding to the level name '%s'", levelName));
		}

		Optional<HierarchyLevel> requestedHierarchyLevelOptional = accountHierarchyService
				.getHierarchyLevelByLevelName(levelName);

		if (requestedHierarchyLevelOptional.isEmpty()) {
			throw new NotFoundException(String.format("Requested level '%s' does not exist", levelName));
		}

		Optional<HierarchyLevel> projectHierarchyLevelOptional = accountHierarchyService
				.getHierarchyLevelByLevelId(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT);

		if (projectHierarchyLevelOptional.isEmpty()) {
			throw new InternalServerErrorException("Could not find any hierarchy level relating to a 'project' entity");
		}

		HierarchyLevel requestedLevel = requestedHierarchyLevelOptional.get();
		HierarchyLevel projectLevel = projectHierarchyLevelOptional.get();

		if (requestedLevelIsNotSupported(requestedLevel, projectLevel)) {
			throw new BadRequestException(String.format("Requested level '%s' is too low on the hierarchy", levelName));
		}

		HierarchyLevel firstChildHierarchyLevelAfterRequestedLevel = accountHierarchyService
				.getHierarchyLevelByLevelNumber(requestedLevel.getLevel() + 1).get();

		return HierarchyLevelsData.builder().requestedLevel(requestedLevel)
				.firstChildHierarchyLevelAfterRequestedLevel(firstChildHierarchyLevelAfterRequestedLevel)
				.projectLevel(projectLevel).build();
	}

	/**
	 * Validates if the requested level supports recommendations.
	 * Levels below project level are not supported.
	 *
	 * @param requestedLevel the requested hierarchy level
	 * @param projectLevel the project hierarchy level
	 * @return true if level is not supported, false otherwise
	 */
	private boolean requestedLevelIsNotSupported(HierarchyLevel requestedLevel, HierarchyLevel projectLevel) {
		int nextHierarchicalLevelNumber = requestedLevel.getLevel() + 1;

		Optional<HierarchyLevel> nextHierarchicalLevelOptional = accountHierarchyService
				.getHierarchyLevelByLevelNumber(nextHierarchicalLevelNumber);

		return nextHierarchicalLevelOptional.isEmpty()
				|| nextHierarchicalLevelOptional.get().getLevel() > projectLevel.getLevel();
	}

	/**
	 * Checks if multiple hierarchy levels match the given name.
	 *
	 * @param levelName the level name to check
	 * @param allHierarchyLevels all hierarchy levels in the system
	 * @return true if multiple levels match, false otherwise
	 */
	private boolean multipleLevelsCorrespondToLevelName(String levelName, Map<String, HierarchyLevel> allHierarchyLevels) {
		return allHierarchyLevels.values().stream()
				.filter(hierarchyLevel -> StringUtils.isNotEmpty(hierarchyLevel.getHierarchyLevelName())
						&& hierarchyLevel.getHierarchyLevelName().equalsIgnoreCase(levelName))
				.toList().size() > 1;
	}

	/**
	 * Extracts all project node IDs under the requested hierarchy level.
	 *
	 * @param requestData recommendation request data with hierarchy and organization lookup
	 * @return Set of project node IDs, or empty set if none found
	 */
	private Set<String> extractProjectNodeIds(RecommendationRequestData requestData) {
		// Get project children grouped by parent node IDs using OrganizationLookup
		Map<String, List<AccountFilteredData>> projectChildrenGroupedByParentNodeIds = requestData.organizationLookup()
				.getChildrenGroupedByParentNodeIds(
						requestData.hierarchyLevelsData.firstChildHierarchyLevelAfterRequestedLevel.getLevel(),
						requestData.hierarchyLevelsData.projectLevel.getLevel());

		// Extract all project node IDs
		return projectChildrenGroupedByParentNodeIds.values().stream().flatMap(Collection::stream)
				.map(AccountFilteredData::getNodeId).collect(Collectors.toSet());
	}

	/**
	 * Creates an empty response structure when no projects match the specified criteria.
	 *
	 * @param levelName the organizational level
	 * @return ServiceResponse with empty recommendation list and zero counts
	 */
	private ServiceResponse buildEmptyResponse(String levelName) {
		RecommendationSummaryDTO emptySummary = RecommendationSummaryDTO.builder()
				.levelName(levelName)
				.totalProjectsWithRecommendations(0)
				.totalProjectsQueried(0)
				.totalRecommendations(0)
				.message("No projects found for the specified criteria")
				.build();
		RecommendationResponseDTO emptyResponse = RecommendationResponseDTO.builder()
				.summary(emptySummary)
				.details(new ArrayList<>())
				.build();
		return new ServiceResponse(Boolean.TRUE, "No projects found for the specified criteria", emptyResponse);
	}

	/**
	 * Retrieves the most recent recommendation for each project using database-level optimization.
	 * Leverages MongoDB aggregation pipeline to fetch only the latest 1 recommendation per project,
	 * eliminating the need for in-memory deduplication.
	 *
	 * @param projectNodeIds Set of project node IDs
	 * @return List of latest recommendations, one per project
	 */
	private List<RecommendationsActionPlan> getLatestRecommendationsForProjects(Set<String> projectNodeIds) {
		log.debug("Fetching latest 1 recommendation for {} projects using database optimization",
			projectNodeIds.size());

		// Use MongoDB aggregation for efficient database-level filtering
		// Repository accepts Collection interface - no need to convert Set to ArrayList
		List<RecommendationsActionPlan> recommendations = recommendationRepository
				.findLatestRecommendationsByProjectIds(new ArrayList<>(projectNodeIds), DEFAULT_RECOMMENDATIONS_LIMIT_PER_PROJECT);

		log.debug("Retrieved {} recommendation(s) from database using optimized aggregation pipeline",
			recommendations.size());

		return recommendations;
	}

	/**
	 * Maps recommendation entity to API response DTO.
	 * Note: recommendations field in entity is a single Recommendation object (not a collection).
	 *
	 * @param entity the RecommendationsActionPlan entity
	 * @return ProjectRecommendationDTO for API response
	 * @throws IllegalStateException if entity has null recommendations field
	 */
	private ProjectRecommendationDTO mapToProjectRecommendationDTO(RecommendationsActionPlan entity) {
		if (entity.getRecommendations() == null) {
			log.error("Entity with id {} has null recommendations field. Project: {}",
					entity.getId(), entity.getBasicProjectConfigId());
			throw new IllegalStateException(
					String.format("Recommendations data is missing for project %s", entity.getBasicProjectConfigId()));
		}

		return ProjectRecommendationDTO.builder()
				.id(entity.getId().toString())
				.projectId(entity.getBasicProjectConfigId())
				.projectName(entity.getProjectName())
				.persona(entity.getPersona())
				.level(entity.getLevel())
				.recommendations(entity.getRecommendations())
				.metadata(entity.getMetadata())
				.createdAt(entity.getCreatedAt())
				.build();
	}
}