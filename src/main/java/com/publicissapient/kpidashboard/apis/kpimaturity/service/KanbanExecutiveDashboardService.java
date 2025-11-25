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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.errors.ExecutiveDataException;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.KpiMaturityResponseDTO;
import com.publicissapient.kpidashboard.apis.kpimaturity.mapper.KpiMaturityDashboardMapper;
import com.publicissapient.kpidashboard.apis.kpimaturity.strategy.AbstractExecutiveDashboardStrategy;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.userboardconfig.service.UserBoardConfigService;
import com.publicissapient.kpidashboard.common.model.application.OrganizationHierarchy;
import com.publicissapient.kpidashboard.common.repository.application.KpiCategoryRepository;

import lombok.extern.slf4j.Slf4j;

/** Strategy implementation for Kanban executive dashboard. */
@Slf4j
@Service
public class KanbanExecutiveDashboardService extends AbstractExecutiveDashboardStrategy {

	public static final String STRATEGY_TYPE = "kanban";

	@Autowired
	@Qualifier("kanbanExecutiveTaskExecutor")
	private Executor kanbanExecutiveTaskExecutor;

	public KanbanExecutiveDashboardService(
			ProjectEfficiencyService projectEfficiencyService,
			CacheService cacheService,
			UserBoardConfigService userBoardConfigService,
			KanbanKpiMaturity kanbanKpiMaturity,
			KpiCategoryRepository kpiCategoryRepository,
			ConfigHelperService configHelperService,
			CustomApiConfig customApiConfig) {
		super(
				STRATEGY_TYPE,
				cacheService,
				projectEfficiencyService,
				userBoardConfigService,
				kanbanKpiMaturity,
				kpiCategoryRepository,
				configHelperService,
				customApiConfig);
	}

	@Override
    public Executor getExecutor() {
		return kanbanExecutiveTaskExecutor;
	}

	@Override
    public KpiMaturityResponseDTO fetchDashboardData(KpiRequest kpiRequest) {
		// Implement Scrum-specific dashboard data fetching logic here
		// Add Scrum-specific implementation
		long startTime = System.currentTimeMillis();
		log.info("Starting executive dashboard processing for Scrum projects");
		// Pre-load all required data
		Set<String> boards = getBoards();
		List<String> requiredNodeIds = getRequiredNodeIds(kpiRequest);

		if (CollectionUtils.isEmpty(requiredNodeIds) || CollectionUtils.isEmpty(boards)) {
			log.warn("ids or boards are empty");
			throw new ExecutiveDataException(
					"Invalid request parameters, ids or boards are empty", HttpStatus.BAD_REQUEST);
		}
		// Batch process project configurations
		Map<String, OrganizationHierarchy> nodeWiseHierachy = getNodeWiseHierarchy(requiredNodeIds);

		if (nodeWiseHierachy.isEmpty()) {
			log.warn("No valid project configurations found for the provided IDs");
			throw new ExecutiveDataException(
					"No valid project configurations found for the provided IDs", HttpStatus.BAD_REQUEST);
		}
		Map<String, Map<String, String>> finalResults =
				new ConcurrentHashMap<>(
						processProjectBatch(requiredNodeIds, kpiRequest, nodeWiseHierachy, boards, true));
		log.info(
				"Completed processing {} projects in {} ms",
				requiredNodeIds.size(),
				System.currentTimeMillis() - startTime);

		// Calculate project efficiency for each project
		Map<String, Map<String, Object>> projectEfficiencies = new HashMap<>();
		finalResults.forEach(
				(projectId, boardMaturities) -> {
					Map<String, Object> efficiency =
							projectEfficiencyService.calculateProjectEfficiency(boardMaturities);
					projectEfficiencies.put(projectId, efficiency);
					log.info("Project {} efficiency: {}", projectId, efficiency);
				});

		// Convert results to DTO with efficiency data
		return KpiMaturityDashboardMapper.toExecutiveDashboardResponse(
				finalResults, nodeWiseHierachy, projectEfficiencies, kpiRequest.getLevelName());
	}
}
