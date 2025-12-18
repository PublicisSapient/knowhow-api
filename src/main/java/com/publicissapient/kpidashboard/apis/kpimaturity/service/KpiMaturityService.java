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

package com.publicissapient.kpidashboard.apis.kpimaturity.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Precision;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.MapUtils;

import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceKanbanImpl;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.filter.service.OrganizationLookup;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.KpiMaturityRequest;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.KpiMaturityResponse;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.MaturityScore;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.OrganizationEntityKpiMaturity;
import com.publicissapient.kpidashboard.apis.model.AccountFilterRequest;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.kpimaturity.organization.KpiMaturity;
import com.publicissapient.kpidashboard.common.repository.kpimaturity.organization.KpiMaturityCustomRepository;
import com.publicissapient.kpidashboard.common.shared.enums.ProjectDeliveryMethodology;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing and calculating KPI maturity metrics across
 * organizational hierarchies.
 *
 * <p>
 * This service provides comprehensive KPI maturity analysis for both Kanban and
 * Scrum delivery methodologies. It processes organizational hierarchy data to
 * calculate maturity scores, efficiency percentages, and health indicators at
 * various organizational levels (Business Unit, Vertical, Account, Engagement,
 * Project).
 * </p>
 *
 * <p>
 * The service supports:
 * </p>
 * <ul>
 * <li>Multi-level organizational hierarchy analysis</li>
 * <li>Kanban and Scrum delivery methodology processing</li>
 * <li>User access permission validation</li>
 * <li>Maturity score aggregation and health classification</li>
 * <li>Dynamic dashboard matrix generation</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiMaturityService {

	/** Efficiency percentage threshold for healthy classification (80%) */
	private static final int EFFICIENCY_PERCENTAGE_HEALTHY = 80;
	/** Efficiency percentage threshold for moderate classification (50%) */
	private static final int EFFICIENCY_PERCENTAGE_MODERATE = 50;
	private static final int ROUNDING_SCALE_2 = 2;

	private static final String PROJECT_HAS_NO_KPI_MATURITY_DATA_L0G_MESSAGE = "No kpi maturity data was found for "
			+ "project with node id {} and name {}";
	private static final String ORGANIZATION_KPI_MATURITY_SUCCESSFULLY_RETRIEVED_MESSAGE = "Successfully retrieved the organization kpi maturity data";

	private final KpiMaturityCustomRepository kpiMaturityCustomRepository;

	private final FilterHelperService filterHelperService;
	private final AccountHierarchyServiceImpl accountHierarchyServiceImpl;
	private final AccountHierarchyServiceKanbanImpl accountHierarchyServiceKanbanImpl;

	/**
	 * Internal data structure for holding KPI maturity computation data.
	 *
	 * @param hierarchyLevelsData
	 *            Information about hierarchy levels involved in computation
	 * @param organizationLookup
	 *            Lookup structure for organizational hierarchy navigation
	 */
	@Builder
	private record KpiMaturityCalculationContext(HierarchyLevelsData hierarchyLevelsData,
			OrganizationLookup organizationLookup) {
	}

	/**
	 * Internal data structure for holding hierarchy level information.
	 *
	 * @param requestedLevel
	 *            The organizational level requested by the user
	 * @param projectLevel
	 *            The project level in the organizational hierarchy
	 */
	@Builder
	private record HierarchyLevelsData(HierarchyLevel requestedLevel, HierarchyLevel projectLevel) {
	}

	/**
	 * Retrieves KPI maturity data for the specified organizational level and
	 * delivery methodology.
	 *
	 * <p>
	 * This is the main entry point for KPI maturity analysis. The method handles
	 * both Kanban and Scrum delivery methodologies, delegating to appropriate
	 * processing logic based on the methodology specified in the request.
	 * </p>
	 *
	 * @param kpiMaturityRequest
	 *            The request containing level name, delivery methodology, and
	 *            optional parent node ID
	 * @return KpiMaturityResponseDTO containing maturity metrics, health
	 *         indicators, and dashboard data
	 */
	public ServiceResponse getKpiMaturity(KpiMaturityRequest kpiMaturityRequest) {
		KpiMaturityCalculationContext kpiMaturityCalculationContext = createKpiMaturityComputationData(
				kpiMaturityRequest);
		return processKpiMaturityRequest(kpiMaturityRequest, kpiMaturityCalculationContext);
	}

	/**
	 * This method performs the kpi maturity request processing including:
	 * <ul>
	 * <li>Organizational hierarchy traversal and project identification</li>
	 * <li>KPI maturity data retrieval for identified projects</li>
	 * <li>Maturity metrics computation and aggregation</li>
	 * <li>Dashboard matrix construction with dynamic columns</li>
	 * </ul>
	 *
	 * <p>
	 * If no KPI maturity data is found for the requested projects, an empty
	 * response is returned with appropriate logging for troubleshooting.
	 * </p>
	 *
	 * @param kpiMaturityRequest
	 *            The original request containing filtering criteria
	 * @param kpiMaturityCalculationContext
	 *            Pre-computed hierarchy and lookup data
	 * @return KpiMaturityResponseDTO with matrix data containing rows and columns
	 *         for dashboard display
	 */
	private ServiceResponse processKpiMaturityRequest(KpiMaturityRequest kpiMaturityRequest,
			KpiMaturityCalculationContext kpiMaturityCalculationContext) {
		Map<String, List<AccountFilteredData>> projectChildrenGroupedByRequestedRootNodeIds = createProjectChildrenGroupedByRequestedRootNodeIdsMap(
				kpiMaturityRequest, kpiMaturityCalculationContext);

		Set<String> projectNodeIds = projectChildrenGroupedByRequestedRootNodeIds.values().stream()
				.flatMap(Collection::stream).map(AccountFilteredData::getNodeId).collect(Collectors.toSet());

		Map<String, KpiMaturity> kpiMaturityGroupedByNodeId = this.kpiMaturityCustomRepository
				.getLatestKpiMaturityByCalculationDateForProjects(projectNodeIds).stream()
				.collect(Collectors.toMap(KpiMaturity::getHierarchyEntityNodeId, kpiMaturity -> kpiMaturity));

		if (MapUtils.isEmpty(kpiMaturityGroupedByNodeId)) {
			log.info("No kpi maturity data could be found for requested projects {}",
					projectChildrenGroupedByRequestedRootNodeIds.keySet());
			return new ServiceResponse(Boolean.TRUE, ORGANIZATION_KPI_MATURITY_SUCCESSFULLY_RETRIEVED_MESSAGE,
					new KpiMaturityResponse());
		}

		Map<String, AccountFilteredData> rootNodesGroupedById = new HashMap<>();

		projectChildrenGroupedByRequestedRootNodeIds.keySet().forEach(rootNodeId -> {
			if (kpiMaturityCalculationContext.organizationLookup.getAccountDataByNodeId(rootNodeId) != null) {
				rootNodesGroupedById.computeIfAbsent(rootNodeId,
						value -> kpiMaturityCalculationContext.organizationLookup.getAccountDataByNodeId(rootNodeId)
								.get(0));
			}
		});
		return new ServiceResponse(Boolean.TRUE, ORGANIZATION_KPI_MATURITY_SUCCESSFULLY_RETRIEVED_MESSAGE,
				computeKpiMaturityResponse(projectChildrenGroupedByRequestedRootNodeIds, kpiMaturityCalculationContext,
						kpiMaturityGroupedByNodeId));
	}

	@SuppressWarnings("java:S1774")
	private KpiMaturityResponse computeKpiMaturityResponse(
			Map<String, List<AccountFilteredData>> projectChildrenGroupedByRequestedRootNodeIds,
			KpiMaturityCalculationContext kpiMaturityCalculationContext,
			Map<String, KpiMaturity> kpiMaturityGroupedByNodeId) {
		List<OrganizationEntityKpiMaturity> details = new ArrayList<>();
		Map<String, List<Double>> maturityScoresGroupedByCategoryForSummaryComputation = new HashMap<>();
		double efficiencyPercentagesForSummaryComputation = 0.0D;
		int totalNumberOfProjectsWithKpiMaturityData = 0;
		for (Map.Entry<String, List<AccountFilteredData>> projectsGroupedByRootNodeId : projectChildrenGroupedByRequestedRootNodeIds
				.entrySet()) {
			AccountFilteredData rootNode = kpiMaturityCalculationContext.organizationLookup
					.getAccountDataByNodeId(projectsGroupedByRootNodeId.getKey()).get(0);

			int numberOfProjectsWithKpiMaturityData = 0;
			double efficiencyPercentages = 0.0D;

			Map<String, List<Double>> maturityScoresGroupedByCategory = new HashMap<>();
			for (AccountFilteredData projectAccountData : projectsGroupedByRootNodeId.getValue()) {
				KpiMaturity kpiMaturity = kpiMaturityGroupedByNodeId.get(projectAccountData.getNodeId());
				if (kpiMaturity != null) {
					numberOfProjectsWithKpiMaturityData++;
					totalNumberOfProjectsWithKpiMaturityData++;
					efficiencyPercentages += kpiMaturity.getEfficiency().getPercentage();
					efficiencyPercentagesForSummaryComputation += kpiMaturity.getEfficiency().getPercentage();
					kpiMaturity.getMaturityScores().forEach(maturityScore -> {
						maturityScoresGroupedByCategory.computeIfAbsent(maturityScore.getKpiCategory(),
								value -> new ArrayList<>());
						maturityScoresGroupedByCategoryForSummaryComputation
								.computeIfAbsent(maturityScore.getKpiCategory(), value -> new ArrayList<>());
						double score = maturityScore.getScore() == null ? 0.0D : maturityScore.getScore();
						maturityScoresGroupedByCategory.get(maturityScore.getKpiCategory()).add(score);
						maturityScoresGroupedByCategoryForSummaryComputation.get(maturityScore.getKpiCategory())
								.add(score);
					});
				} else {
					log.info(PROJECT_HAS_NO_KPI_MATURITY_DATA_L0G_MESSAGE, projectAccountData.getNodeId(),
							projectAccountData.getNodeName());
				}
			}
			if (numberOfProjectsWithKpiMaturityData != 0) {
				List<MaturityScore> maturityScores = computeMaturityScores(maturityScoresGroupedByCategory,
						numberOfProjectsWithKpiMaturityData);

				double overallEfficiencyPercentage = efficiencyPercentages / numberOfProjectsWithKpiMaturityData;

				details.add(OrganizationEntityKpiMaturity.builder().organizationEntityNodeId(rootNode.getNodeId())
						.levelName(kpiMaturityCalculationContext.hierarchyLevelsData.requestedLevel
								.getHierarchyLevelName())
						.organizationEntityName(rootNode.getNodeName()).maturityScores(maturityScores)
						.health(determineHealthByEfficiencyPercentage(overallEfficiencyPercentage))
						.completionPercentage((double) Math.round(overallEfficiencyPercentage)).build());
			} else {
				log.info("The organization entity root with node id {} and name {} did not have any projects "
						+ "containing productivity data", rootNode.getNodeId(), rootNode.getNodeName());
			}
		}
		double overallEfficiencyPercentage = efficiencyPercentagesForSummaryComputation
				/ totalNumberOfProjectsWithKpiMaturityData;
		KpiMaturityResponse kpiMaturityResponse = new KpiMaturityResponse();
		kpiMaturityResponse.setSummary(OrganizationEntityKpiMaturity.builder()
				.levelName(kpiMaturityCalculationContext.hierarchyLevelsData.requestedLevel.getHierarchyLevelName())
				.maturityScores(computeMaturityScores(maturityScoresGroupedByCategoryForSummaryComputation,
						totalNumberOfProjectsWithKpiMaturityData))
				.health(determineHealthByEfficiencyPercentage(overallEfficiencyPercentage))
				.completionPercentage((double) Math.round(overallEfficiencyPercentage)).build());
		kpiMaturityResponse.setDetails(details);
		return kpiMaturityResponse;
	}

	/**
	 * Creates and validates the computation data required for KPI maturity
	 * analysis.
	 *
	 * <p>
	 * This method performs comprehensive validation and setup including:
	 * </p>
	 * <ul>
	 * <li><strong>Request Validation:</strong> Ensures request is not null</li>
	 * <li><strong>Hierarchy Setup:</strong> Constructs hierarchy levels data with
	 * validation</li>
	 * <li><strong>Access Control:</strong> Creates organization lookup with user
	 * permission filtering</li>
	 * <li><strong>Parent Node Validation:</strong> Validates parent node access and
	 * hierarchy relationships</li>
	 * </ul>
	 *
	 * <p>
	 * The method enforces business rules such as ensuring the requested level is
	 * not higher than or equal to the parent node level in the organizational
	 * hierarchy.
	 * </p>
	 *
	 * @param kpiMaturityRequest
	 *            The request containing level name, methodology, and optional
	 *            parent node
	 * @return KpiMaturityComputationData containing validated hierarchy and lookup
	 *         structures
	 */
	private KpiMaturityCalculationContext createKpiMaturityComputationData(KpiMaturityRequest kpiMaturityRequest) {
		if (kpiMaturityRequest == null) {
			throw new BadRequestException("Received null KPI maturity request");
		}
		KpiMaturityCalculationContext.KpiMaturityCalculationContextBuilder kpiMaturityComputationDataBuilder = KpiMaturityCalculationContext
				.builder();
		HierarchyLevelsData hierarchyLevelsData = constructHierarchyLevelsDataByRequestedLevelNameAndDeliveryMethodology(
				kpiMaturityRequest.levelName(), kpiMaturityRequest.deliveryMethodology());
		OrganizationLookup organizationLookup = constructOrganizationLookupBasedOnAccountData(hierarchyLevelsData,
				kpiMaturityRequest.deliveryMethodology());

		if (StringUtils.isNotBlank(kpiMaturityRequest.parentNodeId())) {
			List<AccountFilteredData> requestedParentNodes = organizationLookup
					.getAccountDataByNodeId((kpiMaturityRequest.parentNodeId()));

			if (CollectionUtils.isEmpty(requestedParentNodes)) {
				throw new BadRequestException(String.format("Current user does not have access to the organization "
						+ "entity with node id %s or it does not exist", kpiMaturityRequest.parentNodeId()));
			}

			if (requestedParentNodes.stream()
					.anyMatch(parentNode -> hierarchyLevelsData.requestedLevel.getLevel() <= parentNode.getLevel())) {
				throw new BadRequestException("The requested level name should not correspond to an organization "
						+ "level higher or equal than the one of the requested parent node");
			}
		}

		return kpiMaturityComputationDataBuilder.hierarchyLevelsData(hierarchyLevelsData)
				.organizationLookup(organizationLookup).build();
	}

	/**
	 * Constructs the organizational hierarchy tree structure based on user access
	 * permissions.
	 *
	 * <p>
	 * This method filters the organizational hierarchy data to include only
	 * entities that the current user has access to, within the range from the first
	 * child level after the requested level down to the project level.
	 * </p>
	 *
	 * @param hierarchyLevelsData
	 *            Information about the hierarchy levels involved
	 * @return List of TreeNode representing the root nodes of the accessible
	 *         hierarchy tree
	 */
	private OrganizationLookup constructOrganizationLookupBasedOnAccountData(HierarchyLevelsData hierarchyLevelsData,
			ProjectDeliveryMethodology deliveryMethodology) {
		AccountFilterRequest accountFilterRequest = new AccountFilterRequest();

		Set<AccountFilteredData> hierarchyDataUserHasAccessTo = Set.of();
		if (deliveryMethodology == ProjectDeliveryMethodology.KANBAN) {
			hierarchyDataUserHasAccessTo = this.accountHierarchyServiceKanbanImpl.getFilteredList(accountFilterRequest);
		} else if (deliveryMethodology == ProjectDeliveryMethodology.SCRUM) {
			accountFilterRequest.setSprintIncluded(List.of(CommonConstant.CLOSED.toUpperCase()));
			hierarchyDataUserHasAccessTo = this.accountHierarchyServiceImpl.getFilteredList(accountFilterRequest);
		}

		hierarchyDataUserHasAccessTo = hierarchyDataUserHasAccessTo.stream()
				.filter(accountFilteredData -> accountFilteredData != null
						&& StringUtils.isNotEmpty(accountFilteredData.getNodeId())
						&& accountFilteredData.getLevel() <= hierarchyLevelsData.projectLevel.getLevel())
				.collect(Collectors.toSet());

		if (CollectionUtils.isEmpty(hierarchyDataUserHasAccessTo)) {
			throw new ForbiddenException("Current user doesn't have access to any organization hierarchy data");
		}

		return new OrganizationLookup(hierarchyDataUserHasAccessTo);
	}

	/**
	 * Constructs hierarchy levels data required for productivity calculations based
	 * on the requested level name.
	 *
	 * <p>
	 * This method validates the requested level, ensures it exists and is
	 * supported, and identifies the related hierarchy levels needed for the
	 * calculation process.
	 * </p>
	 *
	 * @param levelName
	 *            The name of the requested hierarchy level
	 * @return HierarchyLevelsData containing the requested level, its child level,
	 *         and project level
	 */
	private HierarchyLevelsData constructHierarchyLevelsDataByRequestedLevelNameAndDeliveryMethodology(String levelName,
			ProjectDeliveryMethodology deliveryMethodology) {
		if (StringUtils.isEmpty(levelName)) {
			throw new BadRequestException("Level name must not be empty");
		}

		if (deliveryMethodology == null) {
			throw new BadRequestException("Delivery methodology must not be null");
		}

		Map<String, HierarchyLevel> allHierarchyLevels = this.filterHelperService
				.getHierarchyLevelMap(deliveryMethodology == ProjectDeliveryMethodology.KANBAN);
		if (multipleLevelsAreCorrespondingToLevelName(levelName, allHierarchyLevels)) {
			throw new InternalServerErrorException(String
					.format("Multiple hierarchy levels were found corresponding to the level name '%s'", levelName));
		}

		Optional<HierarchyLevel> requestedHierarchyLevelOptional = Optional.empty();
		Optional<HierarchyLevel> projectHierarchyLevelOptional = Optional.empty();

		if (deliveryMethodology == ProjectDeliveryMethodology.KANBAN) {
			requestedHierarchyLevelOptional = this.accountHierarchyServiceKanbanImpl
					.getHierarchyLevelByLevelName(levelName);
			projectHierarchyLevelOptional = this.accountHierarchyServiceKanbanImpl
					.getHierarchyLevelByLevelId(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT);
		} else if (deliveryMethodology == ProjectDeliveryMethodology.SCRUM) {
			requestedHierarchyLevelOptional = this.accountHierarchyServiceImpl.getHierarchyLevelByLevelName(levelName);
			projectHierarchyLevelOptional = this.accountHierarchyServiceImpl
					.getHierarchyLevelByLevelId(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT);
		}

		if (requestedHierarchyLevelOptional.isEmpty()) {
			throw new NotFoundException(String.format("Requested level '%s' does not exist", levelName));
		}

		if (projectHierarchyLevelOptional.isEmpty()) {
			throw new InternalServerErrorException("Could not find any hierarchy level relating to a 'project' entity");
		}

		HierarchyLevel requestedLevel = requestedHierarchyLevelOptional.get();
		HierarchyLevel projectLevel = projectHierarchyLevelOptional.get();

		if (requestedLevel.getLevel() > projectLevel.getLevel()) {
			throw new BadRequestException(String.format("Requested level '%s' is too low on the hierarchy", levelName));
		}

		return HierarchyLevelsData.builder().requestedLevel(requestedLevel).projectLevel(projectLevel).build();
	}

	/**
	 * Creates a mapping of organizational entities to their child projects based on
	 * the request parameters.
	 *
	 * <p>
	 * This method handles two scenarios:
	 * </p>
	 * <ol>
	 * <li><strong>Filtered by Parent:</strong> When a parent node ID is provided,
	 * it finds children at the requested level under that parent, then maps each to
	 * their project children</li>
	 * <li><strong>Unfiltered:</strong> When no parent is specified, it finds all
	 * entities at the requested level and maps each to their project children</li>
	 * </ol>
	 *
	 * <p>
	 * The resulting map structure enables efficient processing of organizational
	 * hierarchies by grouping projects under their parent organizational entities.
	 * </p>
	 *
	 * @param kpiMaturityRequest
	 *            The request containing optional parent node filtering
	 * @param kpiMaturityCalculationContext
	 *            Computation context with hierarchy levels and organization lookup
	 * @return Map where keys are organizational entity node IDs and values are
	 *         lists of their child projects
	 */
	private static Map<String, List<AccountFilteredData>> createProjectChildrenGroupedByRequestedRootNodeIdsMap(
			KpiMaturityRequest kpiMaturityRequest, KpiMaturityCalculationContext kpiMaturityCalculationContext) {

		if (StringUtils.isNotBlank(kpiMaturityRequest.parentNodeId())) {
			List<AccountFilteredData> childrenByParentNodeId = kpiMaturityCalculationContext.organizationLookup()
					.getChildrenByParentNodeId(kpiMaturityRequest.parentNodeId(),
							kpiMaturityCalculationContext.hierarchyLevelsData.requestedLevel.getLevel());

			Set<String> childrenNodeIds = childrenByParentNodeId.stream().map(AccountFilteredData::getNodeId)
					.collect(Collectors.toSet());
			return kpiMaturityCalculationContext.organizationLookup().getChildrenGroupedByParentNodeIds(childrenNodeIds,
					kpiMaturityCalculationContext.hierarchyLevelsData.projectLevel.getLevel());
		}

		return kpiMaturityCalculationContext.organizationLookup().getChildrenGroupedByParentNodeIds(
				kpiMaturityCalculationContext.hierarchyLevelsData.requestedLevel.getLevel(),
				kpiMaturityCalculationContext.hierarchyLevelsData.projectLevel.getLevel());
	}

	private static List<MaturityScore> computeMaturityScores(Map<String, List<Double>> maturityScoresGroupedByCategory,
			int numberOfProjectsWithKpiMaturityData) {
		List<MaturityScore> maturityScores = new ArrayList<>();
		for (Map.Entry<String, List<Double>> categoryMaturityScoreEntry : maturityScoresGroupedByCategory.entrySet()) {
			if (CollectionUtils.isNotEmpty(categoryMaturityScoreEntry.getValue())) {
				double maturityScore = categoryMaturityScoreEntry.getValue().stream().mapToDouble(score -> score).sum()
						/ numberOfProjectsWithKpiMaturityData;

				double roundedMaturityScore = Precision.round(maturityScore, ROUNDING_SCALE_2);

				maturityScores.add(MaturityScore.builder().kpiCategory(categoryMaturityScoreEntry.getKey())
						.level(String.format("M%s (%s)", (int) Math.ceil(maturityScore), roundedMaturityScore))
						.score(roundedMaturityScore).build());
			}
		}
		return maturityScores;
	}

	private static String determineHealthByEfficiencyPercentage(double overallEfficiencyPercentage) {
		if (overallEfficiencyPercentage >= EFFICIENCY_PERCENTAGE_HEALTHY) {
			return "Healthy";
		}
		if (overallEfficiencyPercentage >= EFFICIENCY_PERCENTAGE_MODERATE) {
			return "Moderate";
		}
		return "Unhealthy";
	}

	private static boolean multipleLevelsAreCorrespondingToLevelName(String levelName,
			Map<String, HierarchyLevel> allHierarchyLevels) {
		return allHierarchyLevels.values().stream()
				.filter(hierarchyLevel -> StringUtils.isNotEmpty(hierarchyLevel.getHierarchyLevelName())
						&& hierarchyLevel.getHierarchyLevelName().equalsIgnoreCase(levelName))
				.toList().size() > 1;
	}
}
