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

package com.publicissapient.kpidashboard.apis.peb.productivity.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.model.AccountFilterRequest;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.peb.productivity.dto.CategoryScoresDTO;
import com.publicissapient.kpidashboard.apis.peb.productivity.dto.KPITrend;
import com.publicissapient.kpidashboard.apis.peb.productivity.dto.KPITrends;
import com.publicissapient.kpidashboard.apis.peb.productivity.dto.OrganizationEntityProductivity;
import com.publicissapient.kpidashboard.apis.peb.productivity.dto.ProductivityResponse;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.productivity.calculation.CategoryScores;
import com.publicissapient.kpidashboard.common.model.productivity.calculation.KPIData;
import com.publicissapient.kpidashboard.common.model.productivity.calculation.Productivity;
import com.publicissapient.kpidashboard.common.repository.productivity.ProductivityCustomRepository;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service class responsible for retrieving and calculating productivity metrics
 * for organizational hierarchy levels in the PEB (Potential Economical Benefit)
 * system.
 *
 * <p>
 * This service provides comprehensive productivity analytics by aggregating
 * data from project-level calculations and presenting them at various
 * organizational hierarchy levels such project.
 * </p>
 *
 * <p>
 * The service operates on a hierarchical model where productivity is calculated
 * at the project level and then aggregated upwards through the organizational
 * structure to provide insights at higher levels.
 * </p>
 *
 * @author vladinu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductivityService {

	private static final double TWO_DECIMAL_ROUNDING_COEFFICIENT = 100.0D;

	private final FilterHelperService filterHelperService;
	private final AccountHierarchyServiceImpl accountHierarchyServiceImpl;
	private final ProductivityCustomRepository productivityCustomRepository;

	/**
	 * Internal data structure representing a node in the organizational hierarchy
	 * tree based on the account filtered data the current user has access to. Used
	 * for building and traversing the hierarchical structure of organizational
	 * entities.
	 */
	@Data
	private static class TreeNode {
		private Node data;
		private List<TreeNode> children = new ArrayList<>();

		public TreeNode(Node data) {
			this.data = data;
		}
	}

	@Builder
	private record PEBProductivityRequest(HierarchyLevelsData hierarchyLevelsData, List<TreeNode> accountDataTreeNode) {
	}

	/**
	 * Data structure containing hierarchy level information required for
	 * productivity calculations.
	 *
	 * @param requestedLevel
	 *            The hierarchy level requested by the user
	 * @param firstChildHierarchyLevelAfterRequestedLevel
	 *            The immediate child level below the requested level
	 * @param projectLevel
	 *            The project hierarchy level (lowest level where productivity is
	 *            calculated)
	 */
	@Builder
	private record HierarchyLevelsData(HierarchyLevel requestedLevel,
			HierarchyLevel firstChildHierarchyLevelAfterRequestedLevel, HierarchyLevel projectLevel) {
	}

	/**
	 * Retrieves comprehensive productivity data for a specified organizational
	 * hierarchy level.
	 *
	 * <p>
	 * This method performs the following operations:
	 * </p>
	 * <ol>
	 * <li>Validates the requested hierarchy level</li>
	 * <li>Constructs the organizational hierarchy tree user has access to</li>
	 * <li>Retrieves project-level productivity data</li>
	 * <li>Aggregates productivity scores by organizational entities</li>
	 * <li>Calculates summary statistics and KPI trends</li>
	 * </ol>
	 *
	 * <p>
	 * The productivity data includes category scores for Quality, Speed,
	 * Efficiency, and overall Productivity, along with detailed breakdowns by
	 * organizational entities and trending information for key performance
	 * indicators.
	 * </p>
	 *
	 * @param levelName
	 *            The name of the organizational hierarchy level (e.g., "project"))
	 * @return ServiceResponse containing ProductivityResponse with summary and
	 *         detailed productivity metrics
	 */
	public ServiceResponse getProductivityForLevel(String levelName) {
		log.info("Started getting productivity data for level {}", levelName);
		long startTime = System.currentTimeMillis();

		PEBProductivityRequest pebProductivityRequest = createPEBProductivityRequestForRequestedLevel(levelName);

		// The "roots" will be the nodes corresponding to the first hierarchy child
		// level after the requested one
		Map<String, List<TreeNode>> rootNodeIdProjectChildren = getLevelMappingByRoot(
				pebProductivityRequest.accountDataTreeNode(),
				pebProductivityRequest.hierarchyLevelsData().firstChildHierarchyLevelAfterRequestedLevel().getLevel(),
				pebProductivityRequest.hierarchyLevelsData().projectLevel().getLevel());

		Set<String> projectNodeIds = rootNodeIdProjectChildren.values().stream().flatMap(Collection::stream)
				.map(treeNode -> treeNode.getData().getId()).collect(Collectors.toSet());

		Map<String, Productivity> productivityGroupedByNodeId = productivityCustomRepository
				.getLatestProductivityByCalculationDateForProjects(projectNodeIds).stream()
				.collect(Collectors.toMap(Productivity::getHierarchyEntityNodeId, productivity -> productivity));

		Map<String, Node> rootHierarchyEntityNodesGroupedById = pebProductivityRequest.accountDataTreeNode.stream()
				.map(TreeNode::getData).collect(Collectors.toMap(Node::getId, node -> node));

		CategoryScoresDTO summaryCategoryScoresDTO = new CategoryScoresDTO();
		List<OrganizationEntityProductivity> details = new ArrayList<>();

		Map<String, List<Double>> kpiTrendValuesGroupedById = new HashMap<>();
		Map<String, KPIData> kpiDataGroupedById = new HashMap<>();

		for (Map.Entry<String, List<TreeNode>> nextChildHierarchyLevelNodeIdProjectTreeNodes : rootNodeIdProjectChildren
				.entrySet()) {
			Node rootNode = rootHierarchyEntityNodesGroupedById
					.get(nextChildHierarchyLevelNodeIdProjectTreeNodes.getKey());
			CategoryScoresDTO rootNodeCategoryScore = new CategoryScoresDTO();
			int numberOfProjectsWithProductivityData = 0;
			for (TreeNode projectTreeNode : nextChildHierarchyLevelNodeIdProjectTreeNodes.getValue()) {
				Productivity projectProductivity = productivityGroupedByNodeId.get(projectTreeNode.data.getId());
				if (projectProductivity != null) {
					// For calculating the break-down details
					numberOfProjectsWithProductivityData++;
					addProductivityScores(rootNodeCategoryScore, projectProductivity.getCategoryScores());

					// For calculating the summary
					addProductivityScores(summaryCategoryScoresDTO, projectProductivity.getCategoryScores());

					projectProductivity.getKpis().forEach(kpiData -> {
						kpiTrendValuesGroupedById.computeIfAbsent(kpiData.getKpiId(), key -> new ArrayList<>());
						kpiTrendValuesGroupedById.get(kpiData.getKpiId()).add(kpiData.getVariationPercentage());
						kpiDataGroupedById.computeIfAbsent(kpiData.getKpiId(), key -> kpiData);
					});
				} else {
					log.info("No productivity data was found for project with node id {} and name {}",
							projectTreeNode.data.getId(), projectTreeNode.data.getName());
				}
			}

			if (numberOfProjectsWithProductivityData != 0) {
				setAveragedProductivityScores(rootNodeCategoryScore, numberOfProjectsWithProductivityData);

				details.add(OrganizationEntityProductivity.builder()
						.levelName(
								pebProductivityRequest.hierarchyLevelsData.firstChildHierarchyLevelAfterRequestedLevel
										.getHierarchyLevelName())
						.organizationEntityName(rootNode.getName()).categoryScores(rootNodeCategoryScore).build());
			} else {
				log.info("The next child hierarchy entity root with node id {} and name {} did not have any projects "
						+ "containing productivity data", rootNode.getId(), rootNode.getName());
			}
		}

		int totalProjectsNumber = productivityGroupedByNodeId.values().size();
		setAveragedProductivityScores(summaryCategoryScoresDTO, totalProjectsNumber);

		ProductivityResponse productivityResponse = new ProductivityResponse();
		productivityResponse.setDetails(details);
		productivityResponse.setSummary(OrganizationEntityProductivity.builder()
				.levelName(pebProductivityRequest.hierarchyLevelsData.requestedLevel.getHierarchyLevelName())
				.categoryScores(summaryCategoryScoresDTO)
				.trends(constructKPITrends(kpiTrendValuesGroupedById, kpiDataGroupedById)).build());

		ServiceResponse serviceResponse = new ServiceResponse();

		serviceResponse.setData(productivityResponse);
		serviceResponse.setSuccess(Boolean.TRUE);
		serviceResponse.setMessage("Productivity data was successfully retrieved");

		log.info("""
				Successfully retrieved the productivity data for level {}. Total projects found under requested level: {}
				Projects without productivity data: {}. Duration: {} ms
				""", levelName, projectNodeIds.size(), (projectNodeIds.size() - totalProjectsNumber),
				System.currentTimeMillis() - startTime);
		return serviceResponse;
	}

	/**
	 * Creates a PEB productivity request object containing all necessary data for
	 * productivity calculations.
	 *
	 * @param levelName
	 *            The name of the hierarchy level for which to create the request
	 * @return PEBProductivityRequest containing hierarchy data and account tree
	 *         structure
	 */
	private PEBProductivityRequest createPEBProductivityRequestForRequestedLevel(String levelName) {
		PEBProductivityRequest.PEBProductivityRequestBuilder pebProductivityRequestBuilder = PEBProductivityRequest
				.builder();
		HierarchyLevelsData hierarchyLevelsData = constructHierarchyLevelsDataByRequestedLevelName(levelName);
		List<TreeNode> accountDataTreeNode = constructAccountDataTreeNode(hierarchyLevelsData);
		return pebProductivityRequestBuilder.hierarchyLevelsData(hierarchyLevelsData)
				.accountDataTreeNode(accountDataTreeNode).build();
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
	private List<TreeNode> constructAccountDataTreeNode(HierarchyLevelsData hierarchyLevelsData) {
		AccountFilterRequest accountFilterRequest = new AccountFilterRequest();
		accountFilterRequest.setKanban(false);
		accountFilterRequest.setSprintIncluded(List.of(CommonConstant.CLOSED.toUpperCase()));

		Set<AccountFilteredData> hierarchyDataUserHasAccessTo = accountHierarchyServiceImpl
				.getFilteredList(accountFilterRequest).stream()
				.filter(accountFilteredData -> accountFilteredData != null
						&& StringUtils.isNotEmpty(accountFilteredData.getNodeId())
						&& accountFilteredData
								.getLevel() >= hierarchyLevelsData.firstChildHierarchyLevelAfterRequestedLevel
										.getLevel()
						&& accountFilteredData.getLevel() <= hierarchyLevelsData.projectLevel.getLevel())
				.collect(Collectors.toSet());

		if (CollectionUtils.isEmpty(hierarchyDataUserHasAccessTo)) {
			throw new ForbiddenException("Current user doesn't have access to any hierarchy data");
		}

		Set<String> rootNodeIds = hierarchyDataUserHasAccessTo.stream()
				.filter(accountFilteredData -> accountFilteredData
						.getLevel() == hierarchyLevelsData.firstChildHierarchyLevelAfterRequestedLevel.getLevel()
						&& accountFilteredData.getLabelName().equalsIgnoreCase(
								hierarchyLevelsData.firstChildHierarchyLevelAfterRequestedLevel.getHierarchyLevelId()))
				.map(AccountFilteredData::getNodeId).collect(Collectors.toSet());

		List<TreeNode> treeNodes = buildTreeNode(hierarchyDataUserHasAccessTo, rootNodeIds);

		if (CollectionUtils.isEmpty(treeNodes)) {
			throw new InternalServerErrorException("Could not construct the hierarchical account data tree node");
		}

		return treeNodes;
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
	private HierarchyLevelsData constructHierarchyLevelsDataByRequestedLevelName(String levelName) {
		if (StringUtils.isEmpty(levelName)) {
			throw new BadRequestException("Level name must not be empty");
		}

		Map<String, HierarchyLevel> allHierarchyLevels = this.filterHelperService.getHierarchyLevelMap(false);
		if (multipleLevelsAreCorrespondingToLevelName(levelName, allHierarchyLevels)) {
			throw new InternalServerErrorException(String
					.format("Multiple hierarchy levels were found corresponding to the level name '%s'", levelName));
		}

		Optional<HierarchyLevel> requestedHierarchyLevelOptional = getHierarchyLevelByLevelName(levelName);
		if (requestedHierarchyLevelOptional.isEmpty()) {
			throw new NotFoundException(String.format("Requested level '%s' does not exist", levelName));
		}

		Optional<HierarchyLevel> projectHierarchyLevelOptional = getHierarchyLevelByLevelId(
				CommonConstant.HIERARCHY_LEVEL_ID_PROJECT);
		if (projectHierarchyLevelOptional.isEmpty()) {
			throw new InternalServerErrorException("Could not find any hierarchy level relating to a 'project' entity");
		}

		HierarchyLevel requestedLevel = requestedHierarchyLevelOptional.get();
		HierarchyLevel projectLevel = projectHierarchyLevelOptional.get();

		if (requestedLevelIsNotSupported(requestedLevel, projectLevel)) {
			throw new BadRequestException(String.format("Requested level '%s' is too low on the hierarchy", levelName));
		}
		HierarchyLevel firstChildHierarchyLevelAfterRequestedLevel = getHierarchyLevelByLevelNumber(
				requestedLevel.getLevel() + 1).get();

		return HierarchyLevelsData.builder().requestedLevel(requestedLevel)
				.firstChildHierarchyLevelAfterRequestedLevel(firstChildHierarchyLevelAfterRequestedLevel)
				.projectLevel(projectLevel).build();
	}

	private boolean requestedLevelIsNotSupported(HierarchyLevel requestedLevel, HierarchyLevel projectLevel) {
		int nextHierarchicalLevelNumber = requestedLevel.getLevel() + 1;

		Optional<HierarchyLevel> nextHierarchicalLevelOptional = getHierarchyLevelByLevelNumber(
				nextHierarchicalLevelNumber);

		return nextHierarchicalLevelOptional.isEmpty()
				|| nextHierarchicalLevelOptional.get().getLevel() > projectLevel.getLevel();
	}

	private Optional<HierarchyLevel> getHierarchyLevelByLevelNumber(int levelNumber) {
		return this.filterHelperService.getHierarchyLevelMap(false).values().stream()
				.filter(hierarchyLevel -> hierarchyLevel.getLevel() == levelNumber).findAny();
	}

	private Optional<HierarchyLevel> getHierarchyLevelByLevelId(String levelId) {
		return this.filterHelperService.getHierarchyLevelMap(false).values().stream()
				.filter(hierarchyLevel -> StringUtils.isNotEmpty(hierarchyLevel.getHierarchyLevelId())
						&& hierarchyLevel.getHierarchyLevelId().equalsIgnoreCase(levelId))
				.findAny();
	}

	private Optional<HierarchyLevel> getHierarchyLevelByLevelName(String levelName) {
		return this.filterHelperService.getHierarchyLevelMap(false).values().stream()
				.filter(hierarchyLevel -> StringUtils.isNotEmpty(hierarchyLevel.getHierarchyLevelName())
						&& hierarchyLevel.getHierarchyLevelName().equalsIgnoreCase(levelName))
				.findAny();
	}

	/**
	 * Builds a tree structure from flat account filtered data.
	 *
	 * @param accountFilteredData
	 *            Set of account data to build the tree from
	 * @param rootNodeIds
	 *            Set of node IDs that should be treated as root nodes
	 * @return List of TreeNode representing the root nodes of the constructed tree
	 */
	private static List<TreeNode> buildTreeNode(Set<AccountFilteredData> accountFilteredData, Set<String> rootNodeIds) {
		Map<String, TreeNode> lookup = new HashMap<>();
		List<TreeNode> roots = new ArrayList<>();

		accountFilteredData.forEach(accountData -> {
			Node node = new Node();
			node.setName(accountData.getNodeName());
			node.setId(accountData.getNodeId());
			node.setLevel(accountData.getLevel());

			lookup.put(accountData.getNodeId(), new TreeNode(node));
		});

		for (AccountFilteredData accountData : accountFilteredData) {
			TreeNode treeNode = lookup.get(accountData.getNodeId());
			if (rootNodeIds.contains(accountData.getNodeId())) {
				roots.add(treeNode);
			} else {
				TreeNode parent = lookup.get(accountData.getParentId());
				if (parent != null) {
					parent.getChildren().add(treeNode);
				}
			}
		}

		return roots;
	}

	/**
	 * Creates a mapping of root nodes to their descendant nodes at the target
	 * level.
	 *
	 * @param roots
	 *            List of root tree nodes to start the mapping from
	 * @param currentLevel
	 *            The current level in the hierarchy traversal
	 * @param targetLevel
	 *            The target level to collect nodes from
	 * @return Map where keys are root node IDs and values are lists of nodes at the
	 *         target level
	 */
	private static Map<String, List<TreeNode>> getLevelMappingByRoot(List<TreeNode> roots, int currentLevel,
			int targetLevel) {
		Map<String, List<TreeNode>> mapping = new HashMap<>();
		for (TreeNode root : roots) {
			List<TreeNode> nodesAtLevel = new ArrayList<>();
			collectNodesAtLevel(root, currentLevel, targetLevel, nodesAtLevel);
			if (!nodesAtLevel.isEmpty()) {
				mapping.put(root.getData().getId(), nodesAtLevel);
			}
		}
		return mapping;
	}

	/**
	 * Recursively collects nodes at a specific target level in the hierarchy tree.
	 *
	 * @param current
	 *            The current node being processed
	 * @param currentLevel
	 *            The current level in the traversal
	 * @param targetLevel
	 *            The target level to collect nodes from
	 * @param result
	 *            List to collect the nodes found at the target level
	 */
	private static void collectNodesAtLevel(TreeNode current, int currentLevel, int targetLevel,
			List<TreeNode> result) {
		if (currentLevel == targetLevel) {
			result.add(current);
			return;
		}
		for (TreeNode child : current.getChildren()) {
			collectNodesAtLevel(child, currentLevel + 1, targetLevel, result);
		}
	}

	private static boolean multipleLevelsAreCorrespondingToLevelName(String levelName,
			Map<String, HierarchyLevel> allHierarchyLevels) {
		return allHierarchyLevels.values().stream()
				.filter(hierarchyLevel -> StringUtils.isNotEmpty(hierarchyLevel.getHierarchyLevelName())
						&& hierarchyLevel.getHierarchyLevelName().equalsIgnoreCase(levelName))
				.toList().size() > 1;
	}

	private static double roundToTwoDecimals(double number) {
		return Math.round(number * TWO_DECIMAL_ROUNDING_COEFFICIENT) / TWO_DECIMAL_ROUNDING_COEFFICIENT;
	}

	/**
	 * Adds productivity scores from a project to the aggregated category scores.
	 *
	 * @param categoryScoresDTO
	 *            The DTO to accumulate scores in
	 * @param projectCategoryScores
	 *            The project-level category scores to add
	 */
	private static void addProductivityScores(CategoryScoresDTO categoryScoresDTO,
			CategoryScores projectCategoryScores) {
		categoryScoresDTO.setOverall(categoryScoresDTO.getOverall() + projectCategoryScores.getOverall());
		categoryScoresDTO.setQuality(categoryScoresDTO.getQuality() + projectCategoryScores.getQuality());
		categoryScoresDTO.setSpeed(categoryScoresDTO.getSpeed() + projectCategoryScores.getSpeed());
		categoryScoresDTO.setEfficiency(categoryScoresDTO.getEfficiency() + projectCategoryScores.getEfficiency());
		categoryScoresDTO
				.setProductivity(categoryScoresDTO.getProductivity() + projectCategoryScores.getProductivity());
	}

	/**
	 * Calculates averaged productivity scores by dividing accumulated scores by the
	 * entity count.
	 *
	 * @param categoryScoresDTO
	 *            The DTO containing accumulated scores
	 * @param entityCount
	 *            The number of entities to average across
	 */
	private static void setAveragedProductivityScores(CategoryScoresDTO categoryScoresDTO, int entityCount) {
		categoryScoresDTO.setOverall(roundToTwoDecimals(categoryScoresDTO.getOverall() / entityCount));
		categoryScoresDTO.setQuality(roundToTwoDecimals(categoryScoresDTO.getQuality() / entityCount));
		categoryScoresDTO.setSpeed(roundToTwoDecimals(categoryScoresDTO.getSpeed() / entityCount));
		categoryScoresDTO.setEfficiency(roundToTwoDecimals(categoryScoresDTO.getEfficiency() / entityCount));
		categoryScoresDTO.setProductivity(roundToTwoDecimals(categoryScoresDTO.getProductivity() / entityCount));
	}

	/**
	 * Constructs KPI trends by categorizing them into positive and negative trends.
	 *
	 * <p>
	 * This method calculates the average trend value for each KPI and categorizes
	 * them as positive (>= 0) or negative (< 0) trends.
	 * </p>
	 *
	 * @param kpiTrendValuesGroupedById
	 *            Map of KPI IDs to their trend values
	 * @param kpiDataGroupedById
	 *            Map of KPI IDs to their metadata
	 * @return KPITrends object containing categorized positive and negative trends
	 */
	private static KPITrends constructKPITrends(Map<String, List<Double>> kpiTrendValuesGroupedById,
			Map<String, KPIData> kpiDataGroupedById) {
		List<KPITrend> positive = new ArrayList<>();
		List<KPITrend> negative = new ArrayList<>();

		double trendValue;
		for (Map.Entry<String, List<Double>> kpiIdTrendValuesEntry : kpiTrendValuesGroupedById.entrySet()) {
			KPIData kpiData = kpiDataGroupedById.get(kpiIdTrendValuesEntry.getKey());
			trendValue = roundToTwoDecimals(
					kpiIdTrendValuesEntry.getValue().stream().mapToDouble(Double::doubleValue).sum()
							/ kpiIdTrendValuesEntry.getValue().size());

			KPITrend kpiTrend = KPITrend.builder().trendValue(trendValue).kpiName(kpiData.getName())
					.kpiCategory(kpiData.getCategory()).build();
			if (trendValue >= 0.0) {
				positive.add(kpiTrend);
			} else {
				negative.add(kpiTrend);
			}
		}

		return KPITrends.builder().positive(positive).negative(negative).build();
	}
}
