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
import org.springframework.stereotype.Service;
import org.thymeleaf.util.MapUtils;

import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceKanbanImpl;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.filter.service.OrganizationLookup;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.BoardMaturityDTO;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.ColumnDefinitionDTO;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.KpiMaturityDashboardDataDTO;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.KpiMaturityMatrixDTO;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.KpiMaturityRequest;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.KpiMaturityResponseDTO;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.OrganizationEntityMaturityMetricsDTO;
import com.publicissapient.kpidashboard.apis.model.AccountFilterRequest;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
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

	private static final String PROJECT_HAS_NO_KPI_MATURITY_DATA_L0G_MESSAGE = "No kpi maturity data was found for "
			+ "project with node id {} and name {}";

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
	private record KpiMaturityComputationData(HierarchyLevelsData hierarchyLevelsData,
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
	public KpiMaturityResponseDTO getKpiMaturity(KpiMaturityRequest kpiMaturityRequest) {
		KpiMaturityComputationData kpiMaturityComputationData = createKpiMaturityComputationData(kpiMaturityRequest);
		return processKpiMaturityRequest(kpiMaturityRequest, kpiMaturityComputationData);
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
	 * @param kpiMaturityComputationData
	 *            Pre-computed hierarchy and lookup data
	 * @return KpiMaturityResponseDTO with matrix data containing rows and columns
	 *         for dashboard display
	 */
	private KpiMaturityResponseDTO processKpiMaturityRequest(KpiMaturityRequest kpiMaturityRequest,
			KpiMaturityComputationData kpiMaturityComputationData) {
		Map<String, List<AccountFilteredData>> projectChildrenGroupedByRequestedRootNodeIds = createProjectChildrenGroupedByRequestedRootNodeIdsMap(
				kpiMaturityRequest, kpiMaturityComputationData);

		Set<String> projectNodeIds = projectChildrenGroupedByRequestedRootNodeIds.values().stream()
				.flatMap(Collection::stream).map(AccountFilteredData::getNodeId).collect(Collectors.toSet());

		Map<String, KpiMaturity> kpiMaturityGroupedByNodeId = this.kpiMaturityCustomRepository
				.getLatestKpiMaturityByCalculationDateForProjects(projectNodeIds).stream()
				.collect(Collectors.toMap(KpiMaturity::getHierarchyEntityNodeId, kpiMaturity -> kpiMaturity));

		if (MapUtils.isEmpty(kpiMaturityGroupedByNodeId)) {
			log.info("No kpi maturity data could be found for requested projects {}",
					projectChildrenGroupedByRequestedRootNodeIds.keySet());
			return KpiMaturityResponseDTO.builder().build();
		}

		Map<String, AccountFilteredData> rootNodesGroupedById = new HashMap<>();

		projectChildrenGroupedByRequestedRootNodeIds.keySet().forEach(rootNodeId -> {
			if (kpiMaturityComputationData.organizationLookup.getAccountDataByNodeId(rootNodeId) != null) {
				rootNodesGroupedById.computeIfAbsent(rootNodeId, value -> kpiMaturityComputationData.organizationLookup
						.getAccountDataByNodeId(rootNodeId).get(0));
			}
		});

		List<OrganizationEntityMaturityMetricsDTO> rows = new ArrayList<>();

		List<ColumnDefinitionDTO> columns = constructColumnDefinitions(
				kpiMaturityGroupedByNodeId.values().stream().toList().get(0),
				kpiMaturityComputationData.hierarchyLevelsData);

		for (Map.Entry<String, List<AccountFilteredData>> projectsGroupedByRootNodeId : projectChildrenGroupedByRequestedRootNodeIds
				.entrySet()) {
			computeOrganizationEntityMaturityMetrics(kpiMaturityComputationData, projectsGroupedByRootNodeId.getKey(),
					projectsGroupedByRootNodeId.getValue(), kpiMaturityGroupedByNodeId).ifPresent(rows::add);
		}
		return KpiMaturityResponseDTO.builder().data(KpiMaturityDashboardDataDTO.builder()
				.matrix(KpiMaturityMatrixDTO.builder().rows(rows).columns(columns).build()).build()).build();
	}

	/**
	 * Computes maturity metrics for a specific organizational entity based on its
	 * child projects.
	 *
	 * <p>
	 * This method aggregates KPI maturity data from multiple projects under an
	 * organizational entity to calculate overall maturity scores, efficiency
	 * percentages, and health indicators. The computation includes:
	 * </p>
	 *
	 * <ul>
	 * <li>Efficiency percentage averaging across projects with data</li>
	 * <li>Maturity score aggregation by KPI category (speed, quality, efficiency,
	 * etc.)</li>
	 * <li>Health classification based on overall efficiency</li>
	 * <li>Board maturity metrics calculation using ceiling function</li>
	 * </ul>
	 *
	 * <p>
	 * Projects without KPI maturity data are logged but do not contribute to
	 * calculations. If no projects have maturity data, an empty Optional is
	 * returned.
	 * </p>
	 *
	 * @param kpiMaturityComputationData
	 *            Computation context with hierarchy and lookup data
	 * @param rootNodeId
	 *            The ID of the organizational entity being analyzed
	 * @param projectEntities
	 *            List of project entities under the organizational entity
	 * @param kpiMaturityGroupedByNodeId
	 *            Map of project node IDs to their KPI maturity data
	 * @return Optional containing computed maturity metrics, or empty if no data
	 *         available
	 */
	private Optional<OrganizationEntityMaturityMetricsDTO> computeOrganizationEntityMaturityMetrics(
			KpiMaturityComputationData kpiMaturityComputationData, String rootNodeId,
			List<AccountFilteredData> projectEntities, Map<String, KpiMaturity> kpiMaturityGroupedByNodeId) {

		AccountFilteredData rootNode = kpiMaturityComputationData.organizationLookup.getAccountDataByNodeId(rootNodeId)
				.get(0);

		int numberOfProjectsWithKpiMaturityData = 0;
		double efficiencyPercentages = 0.0D;

		Map<String, List<Double>> maturityScoresGroupedByCategory = new HashMap<>();
		for (AccountFilteredData projectAccountData : projectEntities) {
			KpiMaturity kpiMaturity = kpiMaturityGroupedByNodeId.get(projectAccountData.getNodeId());
			if (kpiMaturity != null) {
				numberOfProjectsWithKpiMaturityData++;
				efficiencyPercentages += kpiMaturity.getEfficiency().getPercentage();
				kpiMaturity.getMaturityScores().forEach(maturityScore -> {
					maturityScoresGroupedByCategory.computeIfAbsent(maturityScore.getKpiCategory(),
							value -> new ArrayList<>());
					double score;
					if (maturityScore.getScore() == null) {
						score = 0.0D;
					} else {
						score = maturityScore.getScore();
					}
					maturityScoresGroupedByCategory.get(maturityScore.getKpiCategory()).add(score);
				});
			} else {
				log.info(PROJECT_HAS_NO_KPI_MATURITY_DATA_L0G_MESSAGE, projectAccountData.getNodeId(),
						projectAccountData.getNodeName());
			}
		}
		if (numberOfProjectsWithKpiMaturityData != 0) {
			Map<String, String> metrics = computeBoardMaturityMetrics(maturityScoresGroupedByCategory,
					numberOfProjectsWithKpiMaturityData);

			double overallEfficiencyPercentage = efficiencyPercentages / numberOfProjectsWithKpiMaturityData;

			return Optional.of(OrganizationEntityMaturityMetricsDTO.builder().id(rootNode.getNodeId())
					.name(rootNode.getNodeName()).boardMaturity(BoardMaturityDTO.builder().metrics(metrics).build())
					.health(determineHealthByEfficiencyPercentage(overallEfficiencyPercentage))
					.completion((Math.round(overallEfficiencyPercentage) + "%")).build());
		} else {
			log.info("The organization entity root with node id {} and name {} did not have any projects "
					+ "containing productivity data", rootNode.getNodeId(), rootNode.getNodeName());
		}
		return Optional.empty();
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
	 * @param kpiMaturityComputationData
	 *            Computation context with hierarchy levels and organization lookup
	 * @return Map where keys are organizational entity node IDs and values are
	 *         lists of their child projects
	 */
	private static Map<String, List<AccountFilteredData>> createProjectChildrenGroupedByRequestedRootNodeIdsMap(
			KpiMaturityRequest kpiMaturityRequest, KpiMaturityComputationData kpiMaturityComputationData) {

		if (StringUtils.isNotBlank(kpiMaturityRequest.parentNodeId())) {
			List<AccountFilteredData> childrenByParentNodeId = kpiMaturityComputationData.organizationLookup()
					.getChildrenByParentNodeId(kpiMaturityRequest.parentNodeId(),
							kpiMaturityComputationData.hierarchyLevelsData.requestedLevel.getLevel());

			Set<String> childrenNodeIds = childrenByParentNodeId.stream().map(AccountFilteredData::getNodeId)
					.collect(Collectors.toSet());
			return kpiMaturityComputationData.organizationLookup().getChildrenGroupedByParentNodeIds(childrenNodeIds,
					kpiMaturityComputationData.hierarchyLevelsData.projectLevel.getLevel());
		}

		return kpiMaturityComputationData.organizationLookup().getChildrenGroupedByParentNodeIds(
				kpiMaturityComputationData.hierarchyLevelsData.requestedLevel.getLevel(),
				kpiMaturityComputationData.hierarchyLevelsData.projectLevel.getLevel());
	}

	/**
	 * Computes board maturity metrics by aggregating and averaging maturity scores
	 * across categories.
	 *
	 * <p>
	 * This method calculates maturity levels for each KPI category (e.g., speed,
	 * quality, efficiency) using the formula:
	 * <code>M[score] = ceil(sum_of_category_scores / number_of_projects)</code>
	 * </p>
	 *
	 * <p>
	 * The resulting metrics use the "M" prefix followed by the calculated maturity
	 * level, providing a standardized representation of organizational maturity
	 * across different KPI categories.
	 * </p>
	 *
	 * <p>
	 * <strong>Example:</strong> If speed scores are [3.0, 4.0, 5.0] across 2
	 * projects, the result would be "M6" (ceil((3+4+5)/2) = ceil(6) = 6)
	 * </p>
	 *
	 * @param maturityScoresGroupedByCategory
	 *            Map of KPI categories to their score lists
	 * @param numberOfProjectsWithKpiMaturityData
	 *            Number of projects contributing to the calculation
	 * @return Map of KPI categories to their computed maturity metrics (e.g.,
	 *         "speed" -> "M4")
	 */
	private static Map<String, String> computeBoardMaturityMetrics(
			Map<String, List<Double>> maturityScoresGroupedByCategory, int numberOfProjectsWithKpiMaturityData) {
		Map<String, String> metrics = new HashMap<>();
		for (Map.Entry<String, List<Double>> categoryMaturityScoreEntry : maturityScoresGroupedByCategory.entrySet()) {
			if (CollectionUtils.isNotEmpty(categoryMaturityScoreEntry.getValue())) {
				metrics.put(categoryMaturityScoreEntry.getKey(),
						"M" + (int) Math
								.ceil(categoryMaturityScoreEntry.getValue().stream().mapToDouble(score -> score).sum()
										/ numberOfProjectsWithKpiMaturityData));
			}
		}
		return metrics;
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

	/**
	 * Constructs column definitions for the KPI maturity dashboard matrix.
	 *
	 * <p>
	 * This method creates both static and dynamic columns for the dashboard
	 * display:
	 * </p>
	 *
	 * <h4>Static Columns (always present):</h4>
	 * <ul>
	 * <li><strong>Project ID:</strong> Unique identifier for the organizational
	 * entity</li>
	 * <li><strong>[Level] Name:</strong> Name of the entity at the requested
	 * hierarchy level</li>
	 * <li><strong>Efficiency(%):</strong> Overall efficiency percentage</li>
	 * <li><strong>Overall Health:</strong> Health classification
	 * (Healthy/Moderate/Unhealthy)</li>
	 * </ul>
	 *
	 * <h4>Dynamic Columns (based on available KPI categories):</h4>
	 * <ul>
	 * <li>Generated from maturity score categories in the KPI data</li>
	 * <li>Special handling for "DORA" category (displayed as uppercase)</li>
	 * <li>Other categories are capitalized (e.g., "speed" -> "Speed")</li>
	 * </ul>
	 *
	 * @param kpiMaturity
	 *            Sample KPI maturity data used to determine available categories
	 * @param hierarchyLevelsData
	 *            Hierarchy information for generating level-specific headers
	 * @return List of column definitions for dashboard matrix display
	 */
	private static List<ColumnDefinitionDTO> constructColumnDefinitions(KpiMaturity kpiMaturity,
			HierarchyLevelsData hierarchyLevelsData) {
		List<ColumnDefinitionDTO> columns = new ArrayList<>();

		// Add static columns
		columns.add(ColumnDefinitionDTO.builder().field("id").header("Project ID").build());
		columns.add(ColumnDefinitionDTO.builder().field("name")
				.header(hierarchyLevelsData.requestedLevel.getHierarchyLevelName() + " Name").build());
		columns.add(ColumnDefinitionDTO.builder().field("completion").header("Efficiency(%)").build());
		columns.add(ColumnDefinitionDTO.builder().field("health").header("Overall Health").build());

		// Add dynamic board columns
		kpiMaturity.getMaturityScores().forEach(maturityScore -> {
			String fieldName = maturityScore.getKpiCategory().toLowerCase();
			String header;
			if ("dora".equalsIgnoreCase(maturityScore.getKpiCategory())) {
				header = "DORA";
			} else {
				header = StringUtils.capitalize(maturityScore.getKpiCategory());
			}
			columns.add(ColumnDefinitionDTO.builder().field(fieldName).header(header).build());
		});
		return columns;
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
	private KpiMaturityComputationData createKpiMaturityComputationData(KpiMaturityRequest kpiMaturityRequest) {
		if (kpiMaturityRequest == null) {
			throw new BadRequestException("Received null KPI maturity request");
		}
		KpiMaturityComputationData.KpiMaturityComputationDataBuilder kpiMaturityComputationDataBuilder = KpiMaturityComputationData
				.builder();
		HierarchyLevelsData hierarchyLevelsData = constructHierarchyLevelsDataByRequestedLevelNameAndDeliveryMethodology(
				kpiMaturityRequest.levelName(), kpiMaturityRequest.deliveryMethodology(),
				kpiMaturityRequest.parentNodeId());
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
			ProjectDeliveryMethodology deliveryMethodology, String parentNodeId) {
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

		Optional<HierarchyLevel> requestedHierarchyLevelOptional = this.accountHierarchyServiceImpl
				.getHierarchyLevelByLevelName(levelName);
		if (requestedHierarchyLevelOptional.isEmpty()) {
			throw new NotFoundException(String.format("Requested level '%s' does not exist", levelName));
		}

		Optional<HierarchyLevel> projectHierarchyLevelOptional = this.accountHierarchyServiceImpl
				.getHierarchyLevelByLevelId(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT);
		if (projectHierarchyLevelOptional.isEmpty()) {
			throw new InternalServerErrorException("Could not find any hierarchy level relating to a 'project' entity");
		}

		HierarchyLevel requestedLevel = requestedHierarchyLevelOptional.get();
		HierarchyLevel projectLevel = projectHierarchyLevelOptional.get();

		if (requestedLevelIsNotSupported(requestedLevel, projectLevel, parentNodeId)) {
			throw new BadRequestException(String.format("Requested level '%s' is too low on the hierarchy", levelName));
		}

		return HierarchyLevelsData.builder().requestedLevel(requestedLevel).projectLevel(projectLevel).build();
	}

	private boolean requestedLevelIsNotSupported(HierarchyLevel requestedLevel, HierarchyLevel projectLevel,
			String parentNodeId) {
		if (StringUtils.isNotBlank(parentNodeId)) {
			return requestedLevel.getLevel() > projectLevel.getLevel();
		}
		int nextHierarchicalLevelNumber = requestedLevel.getLevel() + 1;

		Optional<HierarchyLevel> nextHierarchicalLevelOptional = this.accountHierarchyServiceImpl
				.getHierarchyLevelByLevelNumber(nextHierarchicalLevelNumber);

		return nextHierarchicalLevelOptional.isEmpty()
				|| nextHierarchicalLevelOptional.get().getLevel() > projectLevel.getLevel();
	}

	private static boolean multipleLevelsAreCorrespondingToLevelName(String levelName,
			Map<String, HierarchyLevel> allHierarchyLevels) {
		return allHierarchyLevels.values().stream()
				.filter(hierarchyLevel -> StringUtils.isNotEmpty(hierarchyLevel.getHierarchyLevelName())
						&& hierarchyLevel.getHierarchyLevelName().equalsIgnoreCase(levelName))
				.toList().size() > 1;
	}
}
