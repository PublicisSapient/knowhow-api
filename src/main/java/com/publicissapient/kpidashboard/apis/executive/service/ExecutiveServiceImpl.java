/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/
package com.publicissapient.kpidashboard.apis.executive.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardDataDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveMatrixDTO;
import com.publicissapient.kpidashboard.apis.executive.mapper.ExecutiveDashboardMapper;
import com.publicissapient.kpidashboard.apis.kpiintegration.service.KpiIntegrationServiceImpl;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.userboardconfig.service.UserBoardConfigService;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.KpiMaster;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.userboardconfig.ConfigLevel;
import com.publicissapient.kpidashboard.common.model.userboardconfig.ProjectListRequested;
import com.publicissapient.kpidashboard.common.model.userboardconfig.UserBoardConfigDTO;
import com.publicissapient.kpidashboard.common.repository.application.KpiCategoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutiveServiceImpl implements ExecutiveService {

	@Value("${executive.dashboard.batch.size:5}")
	private int batchSize;

	@Value("${executive.dashboard.thread.pool.size:10}")
	private int threadPoolSize;

	private final UserBoardConfigService userBoardConfigService;
	private final ProjectEfficiencyService projectEfficiencyService;

	private final KpiCategoryRepository kpiCategoryRepository;
	private final KpiIntegrationServiceImpl kpiIntegrationService;
	private final CacheService cacheService;
	private final ConfigHelperService configHelperService;

	private final ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();

	@PostConstruct
	public void init() {
		taskExecutor.setCorePoolSize(threadPoolSize);
		taskExecutor.setMaxPoolSize(threadPoolSize * 2);
		taskExecutor.setQueueCapacity(100);
		taskExecutor.setThreadNamePrefix("executive-dashboard-");
		taskExecutor.initialize();
	}

	@PreDestroy
	public void destroy() {
		taskExecutor.shutdown();
		try {
			if (!taskExecutor.getThreadPoolExecutor().awaitTermination(60, TimeUnit.SECONDS)) {
				taskExecutor.shutdown();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			taskExecutor.shutdown();
		}
	}

	@Override
	public ExecutiveDashboardResponseDTO getExecutiveDashboardScrum(KpiRequest kpiRequest) {
		long startTime = System.currentTimeMillis();
		log.info("Starting executive dashboard processing for Scrum projects");

		// Pre-load all required data
		Set<String> boards = kpiCategoryRepository.findAll().stream()
				.map(category -> category.getCategoryName().trim().toLowerCase())
				.collect(Collectors.toCollection(HashSet::new));
		boards.add("dora");

		List<String> projectNodeIds = new ArrayList<>(kpiRequest.getSelectedMap()
				.getOrDefault(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, Collections.emptyList()));

		if (CollectionUtils.isEmpty(projectNodeIds) || CollectionUtils.isEmpty(boards)) {
			log.warn("Project IDs or boards are empty");
			return ExecutiveDashboardResponseDTO.builder()
					.data(ExecutiveDashboardDataDTO.builder()
							.matrix(ExecutiveMatrixDTO.builder().rows(Collections.emptyList()).build()).build())
					.build();
		}

		// Batch process project configurations
		Map<String, ProjectBasicConfig> projectConfigs = ((Map<String, ProjectBasicConfig>) cacheService
				.cacheProjectConfigMapData()).values().stream()
				.filter(config -> !config.isKanban() && projectNodeIds.contains(config.getProjectNodeId()))
				.collect(Collectors.toMap(ProjectBasicConfig::getProjectNodeId, config -> config));

		if (projectConfigs.isEmpty()) {
			log.warn("No valid project configurations found for the provided IDs");
			return ExecutiveDashboardResponseDTO.builder()
					.data(ExecutiveDashboardDataDTO.builder()
							.matrix(ExecutiveMatrixDTO.builder().rows(Collections.emptyList()).build()).build())
					.build();
		}

		// Process projects in parallel with controlled concurrency
		/*
		 * List<CompletableFuture<Void>> futures = new ArrayList<>();
		 * 
		 * for (List<String> batch : Lists.partition(projectNodeIds, Math.min(batchSize,
		 * 6))) { CompletableFuture<Void> batchFuture = CompletableFuture
		 * .supplyAsync(() -> processProjectBatch(batch, kpiRequest, projectConfigs,
		 * boards), taskExecutor.getThreadPoolExecutor())
		 * .thenAccept(finalResults::putAll);
		 * 
		 * futures.add(batchFuture); }
		 * 
		 * // Wait for all batches to complete
		 * CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		 */
		Map<String, Map<String, Integer>> finalResults = new ConcurrentHashMap<>(
				processProjectBatch(projectNodeIds, kpiRequest, projectConfigs, boards));
		log.info("Completed processing {} projects in {} ms", projectNodeIds.size(),
				System.currentTimeMillis() - startTime);

		// Calculate project efficiency for each project
		Map<String, Map<String, Object>> projectEfficiencies = new HashMap<>();
		finalResults.forEach((projectId, boardMaturities) -> {
			Map<String, Object> efficiency = projectEfficiencyService.calculateProjectEfficiency(boardMaturities);
			projectEfficiencies.put(projectId, efficiency);
			log.info("Project {} efficiency: {}", projectId, efficiency);
		});

		// Convert results to DTO with efficiency data
		return ExecutiveDashboardMapper.toExecutiveDashboardResponse(finalResults, projectConfigs, projectEfficiencies);
	}

	@Override
	public ExecutiveDashboardResponseDTO getExecutiveDashboardKanban(KpiRequest kpiRequest) {
		log.warn("Kanban dashboard not implemented yet");
		return ExecutiveDashboardResponseDTO.builder().data(ExecutiveDashboardDataDTO.builder()
				.matrix(ExecutiveMatrixDTO.builder().rows(Collections.emptyList()).build()).build()).build();
	}

	private Map<String, Map<String, Integer>> processProjectBatch(List<String> projectNodeIds, KpiRequest kpiRequest,
			Map<String, ProjectBasicConfig> projectConfigs, Set<String> boards) {
		Map<String, Map<String, Integer>> batchResults = new ConcurrentHashMap<>();

		// Process each project sequentially to avoid concurrency issues
		for (String projectNodeId : projectNodeIds) {
			ProjectBasicConfig projectConfig = projectConfigs.get(projectNodeId);
			if (projectConfig == null) {
				log.warn("No configuration found for project node ID: {}", projectNodeId);
				continue;
			}

			try {
				log.info("Processing project: {} - {}", projectNodeId, projectConfig.getProjectName());

				// Get user board configuration for the project
				ProjectListRequested projectListRequest = new ProjectListRequested();
				projectListRequest
						.setBasicProjectConfigIds(Collections.singletonList(projectConfig.getId().toString()));

				UserBoardConfigDTO config = userBoardConfigService.getBoardConfig(ConfigLevel.USER, projectListRequest);

				if (config == null || CollectionUtils.isEmpty(config.getScrum())) {
					log.warn("No board configuration found for project: {}", projectNodeId);
					continue;
				}

				// Process scrum boards for the project
				Map<String, List<String>> boardWiseKpi = config.getScrum().stream()
						.filter(board -> boards.contains(board.getBoardName().toLowerCase().trim()))
						.filter(board -> CollectionUtils.isNotEmpty(board.getKpis()))
						.collect(Collectors.toMap(board -> board.getBoardName().trim(),
								board -> board.getKpis().stream()
										.filter(kpi -> kpi.isShown() && kpi.getIsEnabled() && kpi.getKpiDetail() != null
												&& kpi.getKpiDetail().isCalculateMaturity())
										.map(kpi -> kpi.getKpiId().trim().toLowerCase()).collect(Collectors.toList()),
								(existing, replacement) -> existing, HashMap::new));

				if (!boardWiseKpi.isEmpty()) {
					log.debug("Processing {} boards for project {}", boardWiseKpi.size(), projectNodeId);
					Map<String, Integer> projectResults = processProjectKpis(projectNodeId, kpiRequest, boardWiseKpi);
					if (!projectResults.isEmpty()) {
						batchResults.put(projectNodeId, projectResults);
						log.info("Successfully processed project: {} - {}", projectNodeId,
								projectConfig.getProjectName());
					} else {
						log.warn("No results for project: {} - {}", projectNodeId, projectConfig.getProjectName());
					}
				} else {
					log.warn("No matching boards with KPIs found for project: {} - {}", projectNodeId,
							projectConfig.getProjectName());
				}
			} catch (Exception e) {
				log.error("Error processing project {}: {}", projectNodeId, e.getMessage(), e);
			}
		}

		return batchResults;
	}

	private Map<String, Integer> processProjectKpis(String projectNodeId, KpiRequest kpiRequest,
			Map<String, List<String>> boardWiseKpi) throws CloneNotSupportedException {
		Map<String, Integer> results = new HashMap<>();
		KpiRequest clonedKpiRequest = (KpiRequest) kpiRequest.clone();

		boardWiseKpi.forEach((boardName, kpiIds) -> {
			try {
				if (CollectionUtils.isNotEmpty(kpiIds)) {
					Map<String, List<KpiMaster>> sourceWiseKpiMaster = createKpiRequest(clonedKpiRequest, false,
							new HashSet<>(kpiIds), projectNodeId);

					if (!sourceWiseKpiMaster.isEmpty()) {
						List<KpiElement> kpiElements = kpiIntegrationService.getKpiElements(clonedKpiRequest,
								sourceWiseKpiMaster, true);

						if (CollectionUtils.isNotEmpty(kpiElements)) {
							int score = computeBoardSummary(boardName, kpiElements);
							results.put(boardName, score);
						}
					}
				}
			} catch (Exception e) {
				log.error("Error processing KPIs for project {} and board {}: {}", projectNodeId, boardName,
						e.getMessage(), e);
			}
		});

		return results;
	}

	private Map<String, List<KpiMaster>> createKpiRequest(KpiRequest kpiRequest, boolean kanban, Set<String> kpiIdList,
			String projectNodeId) {
		if (CollectionUtils.isEmpty(kpiIdList)) {
			return Collections.emptyMap();
		}

		// Cache KPI masters if not already cached
		List<KpiMaster> kpiMasters = (List<KpiMaster>) configHelperService.loadKpiMaster();

		// Filter and group KPIs in a single stream operation
		Map<String, List<KpiMaster>> kpiSourceMap = kpiMasters.parallelStream().filter(Objects::nonNull)
				.filter(kpi -> kpiIdList.contains(kpi.getKpiId()) && StringUtils.isNotBlank(kpi.getKpiSource()))
				.collect(Collectors.groupingByConcurrent(KpiMaster::getKpiSource, Collectors.toList()));

		String[] strings = new String[1];
		strings[0] = projectNodeId;
		// Set request parameters
		kpiRequest.setIds(strings);
		kpiRequest.getSelectedMap().put(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT,
				Collections.singletonList(projectNodeId));
		kpiRequest.setLabel("project");
		kpiRequest.setLevel(5);

		return kpiSourceMap;
	}

	private int computeBoardSummary(String dashboard, List<KpiElement> elements) {
		if (CollectionUtils.isEmpty(elements)) {
			return 0;
		}

		return (int) Math.ceil(elements.stream().filter(Objects::nonNull).map(KpiElement::getOverallMaturity)
				.filter(StringUtils::isNotBlank).mapToDouble(Double::parseDouble).filter(a -> a > 0).average()
				.orElse(0.0));
	}

}
