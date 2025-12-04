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

import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.filter.service.OrganizationLookup;
import com.publicissapient.kpidashboard.apis.model.AccountFilterRequest;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.recommendations.dto.ProjectRecommendationDTO;
import com.publicissapient.kpidashboard.apis.recommendations.dto.RecommendationRequest;
import com.publicissapient.kpidashboard.apis.recommendations.dto.RecommendationResponseDTO;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationsActionPlan;
import com.publicissapient.kpidashboard.common.repository.recommendation.RecommendationRepository;
import com.publicissapient.kpidashboard.common.shared.enums.ProjectDeliveryMethodology;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for retrieving batch-calculated project recommendations.
 * Follows the same pattern as ProductivityService and KpiMaturityService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

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
			OrganizationLookup organizationLookup, String parentNodeId) {
	}

	/**
	 * Retrieves recommendations for projects at the specified organizational level.
	 * Follows the same pattern as ProductivityService and KpiMaturityService.
	 *
	 * @param request containing levelName, deliveryMethodology, and optional parentNodeId
	 * @return ServiceResponse containing RecommendationResponseDTO with list of project recommendations
	 */
	public ServiceResponse getRecommendationsForLevel(RecommendationRequest request) {
		long startTime = System.currentTimeMillis();
		
		log.info("Processing recommendation request for level: {}, methodology: {}, parentNodeId: {}",
			request.levelName(), request.deliveryMethodology(), request.parentNodeId());

		try {
			// Create request data with hierarchy levels and organization lookup
			RecommendationRequestData requestData = createRecommendationRequestData(
					request.levelName(), request.deliveryMethodology(), request.parentNodeId());
			
			// Extract project node IDs using OrganizationLookup
			Set<String> projectNodeIds = extractProjectNodeIds(requestData);
			
			if (projectNodeIds.isEmpty()) {
				log.warn("No projects found for level: {} with parentNodeId: {}", request.levelName(), request.parentNodeId());
				return buildEmptyResponse();
			}
			
			// Retrieve recommendations from batch data
			List<RecommendationsActionPlan> recommendations = getLatestRecommendationsForProjects(projectNodeIds);
			
			// Map entities to DTOs
			List<ProjectRecommendationDTO> recommendationDTOs = recommendations.stream()
					.map(this::mapToProjectRecommendationDTO)
					.collect(Collectors.toList());
			
			// Build response with metadata
			RecommendationResponseDTO response = RecommendationResponseDTO.builder()
					.recommendations(recommendationDTOs)
					.totalProjects(recommendationDTOs.size())
					.totalProjectsQueried(projectNodeIds.size())
					.message(String.format("Retrieved %d recommendations from %d projects at %s level", 
							recommendationDTOs.size(), projectNodeIds.size(), request.levelName()))
					.build();
			
			long duration = System.currentTimeMillis() - startTime;
			log.info("Successfully retrieved {} recommendations for level: {}. Total projects queried: {}. Duration: {} ms", 
				recommendationDTOs.size(), request.levelName(), projectNodeIds.size(), duration);
			
			return new ServiceResponse(true, "Recommendations retrieved successfully", response);
			
		} catch (BadRequestException | IllegalArgumentException e) {
			log.error("Bad request for recommendation retrieval: {}", e.getMessage());
			throw new BadRequestException(e.getMessage());
		} catch (NotFoundException e) {
			log.error("Resource not found: {}", e.getMessage());
			throw new NotFoundException(e.getMessage());
		} catch (ForbiddenException e) {
			log.error("Access forbidden: {}", e.getMessage());
			throw new ForbiddenException(e.getMessage());
		} catch (Exception e) {
			log.error("Unexpected error retrieving recommendations for level: {}", request.levelName(), e);
			throw new InternalServerErrorException("Internal server error occurred while retrieving recommendations");
		}
	}

	/**
	 * Creates recommendation request data with hierarchy levels and organization lookup.
	 * Follows the pattern from ProductivityService.createPEBProductivityRequestForRequestedLevel
	 *
	 * @param levelName The name of the hierarchy level
	 * @param deliveryMethodology The project delivery methodology
	 * @param parentNodeId Optional parent node ID for filtering
	 * @return RecommendationRequestData containing all necessary data for recommendation retrieval
	 */
	private RecommendationRequestData createRecommendationRequestData(String levelName,
			ProjectDeliveryMethodology deliveryMethodology, String parentNodeId) {
		HierarchyLevelsData hierarchyLevelsData = constructHierarchyLevelsDataByRequestedLevelName(levelName,
				deliveryMethodology, parentNodeId);
		OrganizationLookup organizationLookup = constructOrganizationLookupBasedOnAccountData(hierarchyLevelsData);
		return RecommendationRequestData.builder().hierarchyLevelsData(hierarchyLevelsData)
				.organizationLookup(organizationLookup).parentNodeId(parentNodeId).build();
	}

	/**
	 * Constructs the organizational hierarchy tree structure based on user access permissions.
	 * Follows the pattern from ProductivityService.constructOrganizationLookupBasedOnAccountData
	 *
	 * @param hierarchyLevelsData Information about the hierarchy levels involved
	 * @return OrganizationLookup for efficient hierarchy data access
	 */
	private OrganizationLookup constructOrganizationLookupBasedOnAccountData(HierarchyLevelsData hierarchyLevelsData) {
		AccountFilterRequest accountFilterRequest = new AccountFilterRequest();
		accountFilterRequest.setKanban(false);
		accountFilterRequest.setSprintIncluded(List.of(CommonConstant.CLOSED.toUpperCase()));

		// getFilteredList uses cacheService.cacheAccountHierarchyData() internally - no DB hit
		log.debug("Fetching hierarchy data from cache for user access filtering");
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
	 * Constructs hierarchy levels data required for recommendation calculations.
	 * Follows the pattern from ProductivityService.constructHierarchyLevelsDataByRequestedLevelName
	 * and KpiMaturityService.constructHierarchyLevelsDataByRequestedLevelNameAndDeliveryMethodology
	 *
	 * @param levelName The name of the requested hierarchy level
	 * @param deliveryMethodology The project delivery methodology
	 * @param parentNodeId Optional parent node ID for validation
	 * @return HierarchyLevelsData containing the requested level, its child level, and project level
	 */
	private HierarchyLevelsData constructHierarchyLevelsDataByRequestedLevelName(String levelName,
			ProjectDeliveryMethodology deliveryMethodology, String parentNodeId) {
		if (StringUtils.isEmpty(levelName)) {
			throw new BadRequestException("Level name must not be empty");
		}

		if (deliveryMethodology == null) {
			throw new BadRequestException("Delivery methodology must not be null");
		}

		// Using cached hierarchy level map - no DB hit
		Map<String, HierarchyLevel> allHierarchyLevels = filterHelperService
				.getHierarchyLevelMap(deliveryMethodology == ProjectDeliveryMethodology.KANBAN);
		log.debug("Retrieved {} hierarchy levels from cache for methodology: {}", 
				allHierarchyLevels.size(), deliveryMethodology);

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

		if (requestedLevelIsNotSupported(requestedLevel, projectLevel, parentNodeId)) {
			throw new BadRequestException(String.format("Requested level '%s' is too low on the hierarchy", levelName));
		}

		HierarchyLevel firstChildHierarchyLevelAfterRequestedLevel = accountHierarchyService
				.getHierarchyLevelByLevelNumber(requestedLevel.getLevel() + 1).get();

		return HierarchyLevelsData.builder().requestedLevel(requestedLevel)
				.firstChildHierarchyLevelAfterRequestedLevel(firstChildHierarchyLevelAfterRequestedLevel)
				.projectLevel(projectLevel).build();
	}

	/**
	 * Checks if the requested level is supported for recommendation retrieval.
	 * Follows the pattern from KpiMaturityService.requestedLevelIsNotSupported
	 *
	 * @param requestedLevel The requested hierarchy level
	 * @param projectLevel The project hierarchy level
	 * @param parentNodeId Optional parent node ID
	 * @return true if the level is not supported, false otherwise
	 */
	private boolean requestedLevelIsNotSupported(HierarchyLevel requestedLevel, HierarchyLevel projectLevel,
			String parentNodeId) {
		if (StringUtils.isNotBlank(parentNodeId)) {
			return requestedLevel.getLevel() > projectLevel.getLevel();
		}
		int nextHierarchicalLevelNumber = requestedLevel.getLevel() + 1;

		Optional<HierarchyLevel> nextHierarchicalLevelOptional = accountHierarchyService
				.getHierarchyLevelByLevelNumber(nextHierarchicalLevelNumber);

		return nextHierarchicalLevelOptional.isEmpty()
				|| nextHierarchicalLevelOptional.get().getLevel() > projectLevel.getLevel();
	}

	/**
	 * Checks if multiple hierarchy levels correspond to the same level name.
	 * Follows the pattern from ProductivityService and KpiMaturityService.
	 *
	 * @param levelName The level name to check
	 * @param allHierarchyLevels All hierarchy levels in the system
	 * @return true if multiple levels correspond to the name, false otherwise
	 */
	private boolean multipleLevelsCorrespondToLevelName(String levelName, Map<String, HierarchyLevel> allHierarchyLevels) {
		return allHierarchyLevels.values().stream()
				.filter(hierarchyLevel -> StringUtils.isNotEmpty(hierarchyLevel.getHierarchyLevelName())
						&& hierarchyLevel.getHierarchyLevelName().equalsIgnoreCase(levelName))
				.toList().size() > 1;
	}

	/**
	 * Extracts project node IDs using OrganizationLookup.
	 * Follows the pattern from ProductivityService.getProductivityForLevel
	 *
	 * @param requestData The recommendation request data with hierarchy and organization lookup
	 * @return Set of project node IDs
	 */
	private Set<String> extractProjectNodeIds(RecommendationRequestData requestData) {
		// Get project children grouped by parent node IDs using OrganizationLookup
		Map<String, List<AccountFilteredData>> projectChildrenGroupedByParentNodeIds = requestData.organizationLookup()
				.getChildrenGroupedByParentNodeIds(
						requestData.hierarchyLevelsData.firstChildHierarchyLevelAfterRequestedLevel.getLevel(),
						requestData.hierarchyLevelsData.projectLevel.getLevel());

		// Filter by parentNodeId if provided
		if (StringUtils.isNotBlank(requestData.parentNodeId())) {
			List<AccountFilteredData> filteredProjects = projectChildrenGroupedByParentNodeIds.get(requestData.parentNodeId());
			if (CollectionUtils.isEmpty(filteredProjects)) {
				return Set.of();
			}
			return filteredProjects.stream().map(AccountFilteredData::getNodeId).collect(Collectors.toSet());
		}

		// Extract all project node IDs
		return projectChildrenGroupedByParentNodeIds.values().stream().flatMap(Collection::stream)
				.map(AccountFilteredData::getNodeId).collect(Collectors.toSet());
	}

	/**
	 * Builds an empty response when no projects are found.
	 *
	 * @return ServiceResponse with empty recommendation list
	 */
	private ServiceResponse buildEmptyResponse() {
		RecommendationResponseDTO emptyResponse = RecommendationResponseDTO.builder().recommendations(new ArrayList<>())
				.totalProjects(0).totalProjectsQueried(0).message("No projects found for the specified criteria")
				.build();
		return new ServiceResponse(true, "No projects found", emptyResponse);
	}

	/**
	 * Gets the latest recommendations for the specified projects.
	 * Filters to return only one (the latest) recommendation per project.
	 *
	 * @param projectNodeIds Set of project node IDs
	 * @return List of latest recommendations per project
	 */
	private List<RecommendationsActionPlan> getLatestRecommendationsForProjects(Set<String> projectNodeIds) {
		List<String> projectNodeIdList = new ArrayList<>(projectNodeIds);

		// Get the latest recommendations for all projects
		List<RecommendationsActionPlan> recommendations = recommendationRepository
				.findByProjectIdInOrderByCreatedAtDesc(projectNodeIdList);

		// Filter to get only the latest recommendation per project
		Map<String, RecommendationsActionPlan> latestRecommendationsByProject = recommendations.stream()
				.collect(Collectors.toMap(RecommendationsActionPlan::getProjectId, recommendation -> recommendation,
						(existing, replacement) -> existing.getCreatedAt().isAfter(replacement.getCreatedAt())
								? existing
								: replacement));

		return new ArrayList<>(latestRecommendationsByProject.values());
	}

	/**
	 * Maps RecommendationsActionPlan entity to ProjectRecommendationDTO.
	 * Excludes internal fields like expiresOn for API response.
	 *
	 * @param entity the RecommendationsActionPlan entity from batch processing
	 * @return ProjectRecommendationDTO for API response
	 */
	private ProjectRecommendationDTO mapToProjectRecommendationDTO(RecommendationsActionPlan entity) {
		return ProjectRecommendationDTO.builder()
				.id(entity.getId().toString())
				.projectId(entity.getProjectId())
				.projectName(entity.getProjectName())
				.persona(entity.getPersona())
				.level(entity.getLevel())
				.recommendations(entity.getRecommendations())
				.metadata(entity.getMetadata())
				.createdAt(entity.getCreatedAt())
				.build();
	}
}