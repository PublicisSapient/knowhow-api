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

package com.publicissapient.kpidashboard.apis.kpimaturity.strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.errors.ExecutiveDataException;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.KpiMaturityResponseDTO;
import com.publicissapient.kpidashboard.apis.kpimaturity.service.KanbanKpiMaturity;
import com.publicissapient.kpidashboard.apis.kpimaturity.service.ProjectEfficiencyService;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.userboardconfig.service.UserBoardConfigService;
import com.publicissapient.kpidashboard.common.model.application.KpiMaster;
import com.publicissapient.kpidashboard.common.model.application.OrganizationHierarchy;
import com.publicissapient.kpidashboard.common.model.userboardconfig.BoardDTO;
import com.publicissapient.kpidashboard.common.model.userboardconfig.ConfigLevel;
import com.publicissapient.kpidashboard.common.model.userboardconfig.ProjectListRequested;
import com.publicissapient.kpidashboard.common.model.userboardconfig.UserBoardConfigDTO;
import com.publicissapient.kpidashboard.common.repository.application.KpiCategoryRepository;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Base abstract class for executive dashboard strategies that provides common
 * functionality.
 */
@Slf4j
@Data
@RequiredArgsConstructor
public abstract class AbstractExecutiveDashboardStrategy {

	protected final String strategyType;
	protected final CacheService cacheService;
	protected final ProjectEfficiencyService projectEfficiencyService;
	protected final UserBoardConfigService userBoardConfigService;
	protected final KanbanKpiMaturity toolKpiMaturity;
	protected final KpiCategoryRepository kpiCategoryRepository;
	protected final ConfigHelperService configHelperService;
	protected final CustomApiConfig customApiConfig;

	protected abstract Executor getExecutor();

	public KpiMaturityResponseDTO getExecutiveDashboard(KpiRequest request) {
		ExecutorService overallExecutor = Executors.newSingleThreadExecutor();
		Future<KpiMaturityResponseDTO> future = overallExecutor.submit(() -> fetchDashboardData(request));
		try {
			log.info(">>> About to call future.get() with timeout: {} minutes",
					customApiConfig.getExecutiveTimeoutMinutes());
			KpiMaturityResponseDTO dto = future.get(customApiConfig.getExecutiveTimeoutMinutes(), TimeUnit.MINUTES);
			log.info(">>> Future completed, returning DTO = {}", dto);
			return dto;
		} catch (TimeoutException e) {
			future.cancel(true);
			throw new ExecutiveDataException("Service taking longer than expected (>"
					+ customApiConfig.getExecutiveTimeoutMinutes() + " min), try again later",
					HttpStatus.REQUEST_TIMEOUT, e);
		} catch (ExecutionException e) {
			throw new ExecutiveDataException("Strategy " + strategyType + " failed", e.getCause());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ExecutiveDataException("Strategy " + strategyType + " was interrupted", e);
		} finally {
			overallExecutor.shutdownNow();
		}
	}

	/**
	 * Template method to be implemented by concrete strategies. Contains the
	 * specific logic for fetching dashboard data.
	 *
	 * @param kpiRequest
	 *            the KPI request
	 * @return the executive dashboard response
	 */
	protected abstract KpiMaturityResponseDTO fetchDashboardData(KpiRequest kpiRequest);

	/**
	 * Gets project node IDs from KPI request.
	 *
	 * @param kpiRequest
	 *            the KPI request
	 * @return list of project node IDs
	 */
	public List<String> getRequiredNodeIds(KpiRequest kpiRequest) {
		return new ArrayList<>(
				kpiRequest.getSelectedMap().getOrDefault(kpiRequest.getLabel(), Collections.emptyList()));
	}

	/**
	 * Gets project configurations by node IDs.
	 *
	 * @param projectNodeIds
	 *            list of project node IDs
	 * @return map of project node ID to project configuration
	 */
	@NotNull
	public Map<String, OrganizationHierarchy> getNodeWiseHierarchy(List<String> projectNodeIds) {
		return configHelperService.loadAllOrganizationHierarchy().stream()
				.filter(organizationHierarchy -> projectNodeIds.contains(organizationHierarchy.getNodeId()))
				.collect(Collectors.toMap(OrganizationHierarchy::getNodeId, config -> config));
	}

	public Map<String, Map<String, String>> processProjectBatch(List<String> uniqueIds, KpiRequest kpiRequest,
			Map<String, OrganizationHierarchy> hierarchyMap, Set<String> boards, boolean isKanban) {

		Map<String, Map<String, String>> batchResults = new LinkedHashMap<>();

		// Preload all user board configs once per project
		Map<String, UserBoardConfigDTO> projectBoardConfigs = uniqueIds.stream()
				.collect(Collectors.toMap(projectId -> projectId, projectId -> {
					try {
						ProjectListRequested projectListRequest = new ProjectListRequested();
						projectListRequest.setBasicProjectConfigIds(Collections.singletonList(""));
						return userBoardConfigService.getBoardConfig(ConfigLevel.USER, projectListRequest);
					} catch (NullPointerException e) {
						log.error("Failed to load board config for project {}: {}", projectId, e.getMessage(), e);
						return null;
					}
				}));
		String uniqueHierarchyId = "";
		try {
			for (String uniqueId : uniqueIds) {

				uniqueHierarchyId = uniqueId;
				checkInterrupted();

				OrganizationHierarchy organizationHierarchy = hierarchyMap.get(uniqueId);
				UserBoardConfigDTO config = projectBoardConfigs.get(uniqueId);

				if (config != null) {
					Map<String, Map<String, List<KpiMaster>>> toolToBoardKpis = getBoardWiseUserBoardConfig(boards,
							uniqueId, config, isKanban);

					if (toolToBoardKpis != null && !toolToBoardKpis.isEmpty()) {
						// Process KPIs for this project
						processKpis(kpiRequest, boards, uniqueId, toolToBoardKpis, batchResults, organizationHierarchy);
					} else {
						log.warn("No KPIs to process for project {} - {}", uniqueId,
								organizationHierarchy.getNodeDisplayName());
					}
				} else {
					log.warn("Skipping project {}: no user board config found", uniqueId);
				}
			}
		} catch (InterruptedException ie) {
			log.warn("Processing interrupted for project {} due to timeout", uniqueHierarchyId);
			Thread.currentThread().interrupt();

		} catch (Exception e) {
			log.error("Unexpected error in processProjectBatch: {}", e.getMessage(), e);
		}

		return batchResults;
	}

	private void processKpis(KpiRequest kpiRequest, Set<String> boards, String uniqueId,
			Map<String, Map<String, List<KpiMaster>>> toolToBoardKpis, Map<String, Map<String, String>> batchResults,
			OrganizationHierarchy organizationHierarchy) throws InterruptedException {
		Map<String, String> projectResults = processToolKpis(uniqueId, kpiRequest, toolToBoardKpis, boards);
		checkInterrupted();

		if (!projectResults.isEmpty()) {
			batchResults.put(uniqueId, projectResults);
			log.info("Processed project {} successfully - {}", uniqueId, organizationHierarchy.getNodeDisplayName());
		} else {
			log.warn("No KPI results for project {} - {}", uniqueId, organizationHierarchy.getNodeDisplayName());
		}
	}

	private static Map<String, Map<String, List<KpiMaster>>> getBoardWiseUserBoardConfig(Set<String> boards,
			String projectNodeId, UserBoardConfigDTO config, boolean isKanban) {
		if (config == null)
			return new HashMap<>();
		List<BoardDTO> boardDTOList;
		if (isKanban) {
			boardDTOList = config.getKanban();
		} else {
			boardDTOList = config.getScrum();
		}

		if (CollectionUtils.isEmpty(boardDTOList)) {
			log.warn("No board configuration found for project: {}", projectNodeId);
			return new HashMap<>();
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

	public Map<String, String> processToolKpis(String projectNodeId, KpiRequest kpiRequest,
			Map<String, Map<String, List<KpiMaster>>> toolToBoardKpis, Set<String> boards) {

		Map<String, String> results = new HashMap<>();
		Map<String, List<KpiElement>> boardWiseResults = new HashMap<>();
		for (String board : boards) {
			boardWiseResults.put(board, new ArrayList<>());
		}

		List<CompletableFuture<Map<String, List<KpiElement>>>> futures = toolToBoardKpis.entrySet().stream()
				.map(entry -> CompletableFuture.supplyAsync(() -> {
					try {
						String tool = entry.getKey();
						Map<String, List<KpiMaster>> boardKpisForTool = entry.getValue();

						// Merge all boards’ KPIs into one request
						List<KpiMaster> mergedKpis = boardKpisForTool.values().stream().flatMap(List::stream).toList();
						Map<String, List<KpiMaster>> sourceWiseKpiMaster = Map.of(tool, mergedKpis);

						// Clone request for thread-safety
						KpiRequest cloneKpiRequest = new KpiRequest(kpiRequest);
						createKpiRequest(cloneKpiRequest, projectNodeId);

						// Compute KPIs
						List<KpiElement> kpiResults = toolKpiMaturity.getKpiElements(cloneKpiRequest,
								sourceWiseKpiMaster);

						// Map results back to boards
						Map<String, List<KpiElement>> resultsByBoard = new HashMap<>();
						boardKpisForTool.forEach((boardName, masterList) -> {
							List<String> kpiList = masterList.stream().map(KpiMaster::getKpiId).toList();
							List<KpiElement> boardResults = kpiResults.stream()
									.filter(r -> kpiList.contains(r.getKpiId())).toList();
							resultsByBoard.put(boardName, boardResults);
						});
						return resultsByBoard;

					} catch (Exception e) {
						log.error("Error processing tool {} for project {}: {}", entry.getKey(), projectNodeId,
								e.getMessage(), e);
						return Map.<String, List<KpiElement>>of();
					}
				}, getExecutor())
						// Enforce timeout per tool
						.orTimeout(customApiConfig.getExecutiveTimeoutMinutes(), TimeUnit.MINUTES).exceptionally(ex -> {
							log.error("Tool computation timed out for project {} tool {}: {}", projectNodeId,
									entry.getKey(), ex.getMessage());
							return Map.of();
						}))
				.toList();

		// Wait for all tool computations
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		futures.stream().map(CompletableFuture::join).forEach(toolResultMap -> toolResultMap
				.forEach((board, kpis) -> boardWiseResults.computeIfPresent(board.toLowerCase(), (key, value) -> {
					value.addAll(kpis);
					return value;
				})));

		// Compute summary per board
		boardWiseResults.forEach((board, kpiElements) -> results.put(board, computeBoardSummary(kpiElements)));

		return results;
	}

	private void createKpiRequest(KpiRequest kpiRequest, String projectNodeId) {
		String[] strings = new String[1];
		strings[0] = projectNodeId;
		// Set request parameters
		kpiRequest.setIds(strings);
		kpiRequest.getSelectedMap().put(kpiRequest.getLabel(), Collections.singletonList(projectNodeId));
	}

	public String computeBoardSummary(List<KpiElement> elements) {
		if (CollectionUtils.isEmpty(elements)) {
			return Constant.NOT_AVAILABLE;
		}

		return String.valueOf(elements.stream().filter(Objects::nonNull).map(KpiElement::getOverallMaturity)
				.filter(StringUtils::isNotBlank).mapToDouble(Double::parseDouble).filter(a -> a > 0).average()
				.orElse(0.0));
	}

	@NotNull
	public Set<String> getBoards() {
		Set<String> boards = kpiCategoryRepository.findAll().stream()
				.map(category -> category.getCategoryName().trim().toLowerCase())
				.collect(Collectors.toCollection(HashSet::new));
		boards.add("dora");
		return boards;
	}

	public void checkInterrupted() throws InterruptedException {
		if (Thread.currentThread().isInterrupted()) {
			throw new InterruptedException("Task interrupted due to timeout");
		}
	}
}
