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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.executive.mapper.ExecutiveDashboardMapper;
import com.publicissapient.kpidashboard.apis.executive.service.KanbanKpiMaturity;
import com.publicissapient.kpidashboard.apis.executive.service.ProjectEfficiencyService;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.userboardconfig.service.UserBoardConfigService;
import com.publicissapient.kpidashboard.common.model.application.OrganizationHierarchy;
import com.publicissapient.kpidashboard.common.repository.application.KpiCategoryRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Strategy implementation for Kanban executive dashboard.
 */
@Component
@Slf4j
public class KanbanExecutiveDashboardStrategy extends BaseExecutiveDashboardStrategy {

	public static final String STRATEGY_TYPE = "kanban";

	public KanbanExecutiveDashboardStrategy(ProjectEfficiencyService projectEfficiencyService,
			CacheService cacheService, UserBoardConfigService userBoardConfigService,
			KanbanKpiMaturity kanbanKpiMaturity, KpiCategoryRepository kpiCategoryRepository,
			ConfigHelperService configHelperService) {
		super(STRATEGY_TYPE, cacheService, projectEfficiencyService, userBoardConfigService, kanbanKpiMaturity,
				kpiCategoryRepository, configHelperService);
	}

	@Override
	protected ExecutiveDashboardResponseDTO fetchDashboardData(KpiRequest kpiRequest) {
		// Implement Scrum-specific dashboard data fetching logic here
		// Add Scrum-specific implementation
		long startTime = System.currentTimeMillis();
		log.info("Starting executive dashboard processing for Scrum projects");
		// Pre-load all required data
		Set<String> boards = getBoards();
		List<String> requiredNodeIds = getRequiredNodeIds(kpiRequest);

		if (CollectionUtils.isEmpty(requiredNodeIds) || CollectionUtils.isEmpty(boards)) {
			log.warn("requiredNodeIds or boards are empty");
			return getDefaultResponse();
		}
		// Batch process project configurations
		Map<String, OrganizationHierarchy> nodeWiseHierachy = getNodeWiseHierarchy(requiredNodeIds);

		if (nodeWiseHierachy.isEmpty()) {
			log.warn("No valid projehierarchy configurations found for the provided IDs");
			return getDefaultResponse();
		}
		ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
		try {
			Map<String, Map<String, String>> finalResults = new ConcurrentHashMap<>(
					processProjectBatch(requiredNodeIds, kpiRequest, nodeWiseHierachy, boards, true, executor));
			log.info("Completed processing {} projects in {} ms", requiredNodeIds.size(),
					System.currentTimeMillis() - startTime);

			// Calculate project efficiency for each project
			Map<String, Map<String, Object>> projectEfficiencies = new HashMap<>();
			finalResults.forEach((projectId, boardMaturities) -> {
				Map<String, Object> efficiency = projectEfficiencyService.calculateProjectEfficiency(boardMaturities);
				projectEfficiencies.put(projectId, efficiency);
				log.info("Project {} efficiency: {}", projectId, efficiency);
			});

			// Convert results to DTO with efficiency data
			return ExecutiveDashboardMapper.toExecutiveDashboardResponse(finalResults, nodeWiseHierachy,
					projectEfficiencies, kpiRequest.getLevelName());
		} catch (Exception e) {

		} finally {
			executor.shutdown();
		}
		return getDefaultResponse();
	}
}
