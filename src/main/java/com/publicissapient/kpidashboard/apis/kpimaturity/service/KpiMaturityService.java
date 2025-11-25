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

@Slf4j
@Service
@RequiredArgsConstructor
public class KpiMaturityService {

	private static final int EFFICIENCY_PERCENTAGE_HEALTHY = 80;
	private static final int EFFICIENCY_PERCENTAGE_MODERATE = 50;

	private static final String PROJECT_HAS_NO_KPI_MATURITY_DATA_L0G_MESSAGE = "No kpi maturity data was found for "
			+ "project with node id {} and name {}";

	private final KpiMaturityCustomRepository kpiMaturityCustomRepository;

	private final KpiMaturityDashboardService kpiMaturityDashboardService;
	private final FilterHelperService filterHelperService;
	private final AccountHierarchyServiceImpl accountHierarchyServiceImpl;

	@Builder
	private record KpiMaturityComputationData(HierarchyLevelsData hierarchyLevelsData,
			OrganizationLookup organizationLookup) {
	}

	@Builder
	private record HierarchyLevelsData(HierarchyLevel requestedLevel, HierarchyLevel projectLevel) {
	}

	public KpiMaturityResponseDTO getKpiMaturity(KpiMaturityRequest kpiMaturityRequest) {
		KpiMaturityComputationData kpiMaturityComputationData = createKpiMaturityComputationData(kpiMaturityRequest);
		if (kpiMaturityRequest.deliveryMethodology() == ProjectDeliveryMethodology.KANBAN) {
			return this.kpiMaturityDashboardService.getExecutiveDashboardKanban(KpiMaturityRequest.builder()
					.parentNodeId(kpiMaturityRequest.parentNodeId())
					.levelName(kpiMaturityComputationData.hierarchyLevelsData.requestedLevel.getHierarchyLevelId())
					.levelNumber(kpiMaturityComputationData.hierarchyLevelsData.requestedLevel.getLevel()).build());
		} else {
			return getKpiMaturityForScrum(kpiMaturityRequest, kpiMaturityComputationData);
		}
	}

	private KpiMaturityResponseDTO getKpiMaturityForScrum(KpiMaturityRequest kpiMaturityRequest,
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

	private KpiMaturityComputationData createKpiMaturityComputationData(KpiMaturityRequest kpiMaturityRequest) {
		if (kpiMaturityRequest == null) {
			throw new InternalServerErrorException("Received null KPI maturity request");
		}
		KpiMaturityComputationData.KpiMaturityComputationDataBuilder kpiMaturityComputationDataBuilder = KpiMaturityComputationData
				.builder();
		HierarchyLevelsData hierarchyLevelsData = constructHierarchyLevelsDataByRequestedLevelNameAndDeliveryMethodology(
				kpiMaturityRequest.levelName(), kpiMaturityRequest.deliveryMethodology(),
				kpiMaturityRequest.parentNodeId());
		OrganizationLookup organizationLookup = constructOrganizationLookupBasedOnAccountData(hierarchyLevelsData);

		if(StringUtils.isNotBlank(kpiMaturityRequest.parentNodeId())) {
			List<AccountFilteredData> requestedParentNodes =
					organizationLookup.getByNodeId().get(kpiMaturityRequest.parentNodeId());

			if(CollectionUtils.isEmpty(requestedParentNodes)) {
				throw new BadRequestException(String.format("Current user does not have access to the organization " +
						"entity with node id %s or it does not exist", kpiMaturityRequest.parentNodeId()));
			}

			if(requestedParentNodes.stream().anyMatch(parentNode -> hierarchyLevelsData.requestedLevel.getLevel() <= parentNode.getLevel())) {
				throw new BadRequestException("The requested level name should not correspond to an organization " +
						"level higher or equal than the one of the requested parent node");
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
	private OrganizationLookup constructOrganizationLookupBasedOnAccountData(HierarchyLevelsData hierarchyLevelsData) {
		AccountFilterRequest accountFilterRequest = new AccountFilterRequest();
		accountFilterRequest.setKanban(false);
		accountFilterRequest.setSprintIncluded(List.of(CommonConstant.CLOSED.toUpperCase()));

		Set<AccountFilteredData> hierarchyDataUserHasAccessTo = accountHierarchyServiceImpl
				.getFilteredList(accountFilterRequest).stream()
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
