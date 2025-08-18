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

package com.publicissapient.kpidashboard.apis.executive.strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.common.model.application.OrganizationHierarchy;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardDataDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveMatrixDTO;
import com.publicissapient.kpidashboard.apis.executive.service.ProjectEfficiencyService;
import com.publicissapient.kpidashboard.apis.executive.service.ToolKpiMaturity;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.userboardconfig.service.UserBoardConfigService;
import com.publicissapient.kpidashboard.common.model.application.KpiMaster;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.userboardconfig.BoardDTO;
import com.publicissapient.kpidashboard.common.model.userboardconfig.ConfigLevel;
import com.publicissapient.kpidashboard.common.model.userboardconfig.ProjectListRequested;
import com.publicissapient.kpidashboard.common.model.userboardconfig.UserBoardConfigDTO;
import com.publicissapient.kpidashboard.common.repository.application.KpiCategoryRepository;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Base abstract class for executive dashboard strategies that provides common
 * functionality.
 */

@Slf4j
@RequiredArgsConstructor
public abstract class BaseExecutiveDashboardStrategy implements ExecutiveDashboardStrategy {

	protected final String strategyType;
	protected final CacheService cacheService;
	protected final ProjectEfficiencyService projectEfficiencyService;
	protected final UserBoardConfigService userBoardConfigService;
	protected final ToolKpiMaturity toolKpiMaturity;
	protected final KpiCategoryRepository kpiCategoryRepository;
	protected final ConfigHelperService configHelperService;

	@Override
	public String getStrategyType() {
		return strategyType;
	}

	@Override
	public ExecutiveDashboardResponseDTO getExecutiveDashboard(KpiRequest kpiRequest) {
		// Common preprocessing can be done here
		return fetchDashboardData(kpiRequest);
	}

	/**
	 * Template method to be implemented by concrete strategies. Contains the
	 * specific logic for fetching dashboard data.
	 *
	 * @param kpiRequest
	 *            the KPI request
	 * @return the executive dashboard response
	 */
	protected abstract ExecutiveDashboardResponseDTO fetchDashboardData(KpiRequest kpiRequest);

	/**
	 * Gets project node IDs from KPI request.
	 *
	 * @param kpiRequest
	 *            the KPI request
	 * @return list of project node IDs
	 */
	protected List<String> getRequiredNodeIds(KpiRequest kpiRequest) {
		return new ArrayList<>(kpiRequest.getSelectedMap().getOrDefault(kpiRequest.getLabel(),
				Collections.emptyList()));
	}

	/**
	 * Gets project configurations by node IDs.
	 *
	 * @param projectNodeIds list of project node IDs
	 * @param isKanban       whether to filter by Kanban projects
	 * @param kpiRequest
	 * @return map of project node ID to project configuration
	 */
	@NotNull
	protected Map<String, OrganizationHierarchy> getNodeidWiseProject(List<String> projectNodeIds, boolean isKanban, KpiRequest kpiRequest) {
		/*if(kpiRequest.getLevel()==5){
			Set<String> project = ((Map<String, ProjectBasicConfig>) cacheService.cacheProjectConfigMapData()).values().stream()
					.filter(config -> (config.isKanban() == isKanban) && projectNodeIds.contains(config.getProjectNodeId())).map(ProjectBasicConfig::getProjectNodeId).collect(Collectors.toSet());

		}
		else{*/
			return configHelperService.loadAllOrganizationHierarchy().stream().filter(organizationHierarchy -> projectNodeIds.contains(organizationHierarchy.getNodeId()))
					.collect(Collectors.toMap(OrganizationHierarchy::getNodeId, config -> config));


		//return Collections.emptyMap();
	}

	/**
	 * Creates a default response for error cases.
	 *
	 * @return default response DTO
	 */
	protected ExecutiveDashboardResponseDTO getDefaultResponse() {

		return ExecutiveDashboardResponseDTO.builder().data(ExecutiveDashboardDataDTO.builder()
				.matrix(ExecutiveMatrixDTO.builder().rows(Collections.emptyList()).build()).build()).build();
	}

	protected Map<String, Map<String, Integer>> processProjectBatch(List<String> uniqueIds, KpiRequest kpiRequest,
			Map<String, OrganizationHierarchy> hierarchyMap, Set<String> boards, boolean isKanban) {
		Map<String, Map<String, Integer>> batchResults = new ConcurrentHashMap<>();

		// Process each project sequentially to avoid concurrency issues
		for (String uniqueId : uniqueIds) {
			OrganizationHierarchy organizationHierarchy = hierarchyMap.get(uniqueId);
			/*ProjectBasicConfig projectConfig = projectConfigs.get(uniqueId);
			if (projectConfig == null) {
				log.warn("No configuration found for project node ID: {}", uniqueId);
				continue;
			}
*/
			try {
				//log.info("Processing project: {} - {}", uniqueId, projectConfig.getProjectName());

				// Get user board configuration for the project
				ProjectListRequested projectListRequest = new ProjectListRequested();
				//if project then provide id otherwise not
				projectListRequest
						.setBasicProjectConfigIds(Collections.singletonList(""));

				UserBoardConfigDTO config = userBoardConfigService.getBoardConfig(ConfigLevel.USER, projectListRequest);

				Map<String, Map<String, List<KpiMaster>>> toolToBoardKpis = getBoardWiseUserBoardConfig(boards,
						uniqueId, config, isKanban);
				if (toolToBoardKpis == null)
					continue;
				if (!toolToBoardKpis.isEmpty()) {
					Map<String, Integer> projectResults = processToolKpis(uniqueId, kpiRequest, toolToBoardKpis);
					if (!projectResults.isEmpty()) {
						batchResults.put(uniqueId, projectResults);
						log.info("Successfully processed project: {} - {}", uniqueId,organizationHierarchy.getNodeDisplayName());
					} else {
						log.warn("No results for project: {} - {}", uniqueId, organizationHierarchy.getNodeDisplayName());
					}
				} else {
					log.warn("No matching boards with KPIs found for project: {} - {}", uniqueId,
							organizationHierarchy.getNodeDisplayName());
				}
			} catch (Exception e) {
				log.error("Error processing project {}: {}", uniqueId, e.getMessage(), e);
			}
		}

		return batchResults;
	}

	@Nullable
	private static Map<String, Map<String, List<KpiMaster>>> getBoardWiseUserBoardConfig(Set<String> boards,
			String projectNodeId, UserBoardConfigDTO config, boolean isKanban) {
		if (config == null)
			return null;
		List<BoardDTO> boardDTOList;
		if (isKanban) {
			boardDTOList = config.getKanban();
		} else {
			boardDTOList = config.getScrum();
		}

		if (CollectionUtils.isEmpty(boardDTOList)) {
			log.warn("No board configuration found for project: {}", projectNodeId);
			return null;
		}

		return boardDTOList.stream().filter(board -> boards.contains(board.getBoardName().toLowerCase().trim()))
				.filter(board -> CollectionUtils.isNotEmpty(board.getKpis()))
				.flatMap(board -> board.getKpis().stream()
						.filter(kpi -> kpi.isShown() && kpi.getIsEnabled() && kpi.getKpiDetail() != null
								&& kpi.getKpiDetail().isCalculateMaturity())
						.map(kpi -> Map.entry(kpi.getKpiDetail().getKpiSource(), // outer key: KPI source
								Map.entry(board.getBoardName().trim(), // inner key: board name
										kpi.getKpiDetail() // value: kpiId
								)))).collect(Collectors.groupingBy(Map.Entry::getKey, // group by kpiSource
										Collectors.flatMapping(entry -> Stream.of(entry.getValue()),
												Collectors.groupingBy(Map.Entry::getKey, // inner map key: boardName
														Collectors.mapping(Map.Entry::getValue,
																Collectors.toList())))));

	}

	private Map<String, Integer> processToolKpis(String projectNodeId, KpiRequest kpiRequest,
			Map<String, Map<String, List<KpiMaster>>> toolToBoardKpis) throws CloneNotSupportedException {

		ExecutorService executor = Executors
				.newFixedThreadPool(Math.min(toolToBoardKpis.size(), Runtime.getRuntime().availableProcessors() * 2));

		Set<String> kpiIds = toolToBoardKpis.values().stream().flatMap(a -> a.values().stream())
				.flatMap(a -> a.stream().map(KpiMaster::getKpiId)).collect(Collectors.toSet());

		Map<String, Integer> results = new HashMap<>();

		List<CompletableFuture<Map<String, List<KpiElement>>>> futures = toolToBoardKpis.entrySet().stream()
				.map(entry -> CompletableFuture.supplyAsync(() -> {
					String tool = entry.getKey();
					Map<String, List<KpiMaster>> boardKpisForTool = entry.getValue();
					// Merge all boards’ KPIs into one request
					List<KpiMaster> mergedKpis = boardKpisForTool.values().stream().flatMap(List::stream).toList();
					Map<String, List<KpiMaster>> sourceWiseKpiMaster = Map.of(tool, mergedKpis);
					KpiRequest cloneKpiRequest = null;
					try {
						cloneKpiRequest = kpiRequest.clone();
					} catch (CloneNotSupportedException e) {
						throw new RuntimeException(e);
					}
					createKpiRequest(cloneKpiRequest, false, new HashSet<>(kpiIds), projectNodeId);
					List<KpiElement> kpiResults = toolKpiMaturity.getKpiElements(cloneKpiRequest, sourceWiseKpiMaster);

					// Map results back to boards using pre-existing mapping
					Map<String, List<KpiElement>> resultsByBoard = new HashMap<>();
					boardKpisForTool.forEach((boardName, masterList) -> {

						List<String> kpiList = masterList.stream().map(KpiMaster::getKpiId)
								.collect(Collectors.toList());

						List<KpiElement> boardResults = kpiResults.stream().filter(r -> kpiList.contains(r.getKpiId()))
								.toList();
						resultsByBoard.put(boardName, boardResults);
					});

					return resultsByBoard;
					// Split results by board

				}, executor)).toList();

		Map<String, List<KpiElement>> boardWiseResults = new HashMap<>();
		futures.stream().map(CompletableFuture::join).forEach(toolResultMap -> {
			toolResultMap.forEach(
					(board, kpis) -> boardWiseResults.computeIfAbsent(board, b -> new ArrayList<>()).addAll(kpis));
		});

		// Shutdown executor
		executor.shutdown();

		boardWiseResults.forEach((board, kpiElements) -> {
			int score = computeBoardSummary(kpiElements);
			results.put(board, score);
			// store/process maturity as needed
		});

		return results;
	}

	private void createKpiRequest(KpiRequest kpiRequest, boolean kanban, Set<String> kpiIdList, String projectNodeId) {
		String[] strings = new String[1];
		strings[0] = projectNodeId;
		// Set request parameters
		kpiRequest.setIds(strings);
		kpiRequest.getSelectedMap().put(kpiRequest.getLabel(),
				Collections.singletonList(projectNodeId));
	}

	private int computeBoardSummary(List<KpiElement> elements) {
		if (CollectionUtils.isEmpty(elements)) {
			return 0;
		}

		return (int) Math.ceil(elements.stream().filter(Objects::nonNull).map(KpiElement::getOverallMaturity)
				.filter(StringUtils::isNotBlank).mapToDouble(Double::parseDouble).filter(a -> a > 0).average()
				.orElse(0.0));
	}

	@NotNull
	protected Set<String> getBoards() {
		Set<String> boards = kpiCategoryRepository.findAll().stream()
				.map(category -> category.getCategoryName().trim().toLowerCase())
				.collect(Collectors.toCollection(HashSet::new));
		boards.add("dora");
		return boards;
	}
}
