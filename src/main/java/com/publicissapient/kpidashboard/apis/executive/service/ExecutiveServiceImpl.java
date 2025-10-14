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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardRequestDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.executive.strategy.ExecutiveDashboardStrategyFactory;
import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceKanbanImpl;
import com.publicissapient.kpidashboard.apis.model.AccountFilterRequest;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;

import lombok.extern.slf4j.Slf4j;

/**
 * Service implementation for executive dashboard functionality. Uses Strategy pattern to delegate
 * to appropriate dashboard strategy (Kanban/Scrum).
 */
@Slf4j
@Service
public class ExecutiveServiceImpl implements ExecutiveService {

	private static final String KANBAN = "kanban";
	private static final String SCRUM = "scrum";
	private static final String CLOSED = "CLOSED";

	private final ExecutiveDashboardStrategyFactory strategyFactory;
	private final AccountHierarchyServiceImpl accountHierarchyService;
	private final AccountHierarchyServiceKanbanImpl accountHierarchyServiceKanban;
	private final CacheService cacheService;

	@Autowired
	public ExecutiveServiceImpl(
			ExecutiveDashboardStrategyFactory strategyFactory,
			CacheService cacheService,
			AccountHierarchyServiceImpl accountHierarchyService,
			AccountHierarchyServiceKanbanImpl accountHierarchyServiceKanban) {
		this.strategyFactory = strategyFactory;
		this.cacheService = cacheService;
		this.accountHierarchyService = accountHierarchyService;
		this.accountHierarchyServiceKanban = accountHierarchyServiceKanban;
	}

	@Override
	public ExecutiveDashboardResponseDTO getExecutiveDashboardScrum(
			ExecutiveDashboardRequestDTO request) {
		log.info(
				"Processing Scrum executive dashboard request with parentId: {}", request.getParentId());

		KpiRequest kpiRequest = new KpiRequest();
		kpiRequest.setLevel(request.getLevel());
		kpiRequest.setLabel(request.getLabel());
		kpiRequest.setSelectedMap(createSelectedMap(false));
		kpiRequest.setSprintIncluded(List.of(CLOSED));

		AccountFilterRequest accountFilterRequest = new AccountFilterRequest();
		accountFilterRequest.setKanban(false);
		accountFilterRequest.setSprintIncluded(List.of(CLOSED, "ACTIVE"));

		Set<String> selectedIds =
				StringUtils.isBlank(request.getParentId())
						? getNodeIds(kpiRequest, false, accountFilterRequest, null)
						: getNodeIds(kpiRequest, false, accountFilterRequest, request.getParentId());

		if (CollectionUtils.isNotEmpty(selectedIds)) {
			kpiRequest.setIds(selectedIds.toArray(new String[0]));
		}

		return strategyFactory.getStrategy(SCRUM).getExecutiveDashboard(kpiRequest);
	}

	@Override
	public ExecutiveDashboardResponseDTO getExecutiveDashboardKanban(
			ExecutiveDashboardRequestDTO request) {
		log.info(
				"Processing Kanban executive dashboard request with parentId: {}", request.getParentId());

		KpiRequest kpiRequest = new KpiRequest();
		kpiRequest.setLevel(request.getLevel());
		kpiRequest.setLabel(request.getLabel());
		kpiRequest.setSelectedMap(createSelectedMap(true));
		kpiRequest.getSelectedMap().put("date", List.of("Weeks"));
		kpiRequest.setIds(new String[] {"5"});
		kpiRequest.setSprintIncluded(List.of(CLOSED));

		AccountFilterRequest accountFilterRequest = new AccountFilterRequest();
		accountFilterRequest.setKanban(true);
		accountFilterRequest.setSprintIncluded(List.of(CLOSED));

		Set<String> selectedIds =
				StringUtils.isBlank(request.getParentId())
						? getNodeIds(kpiRequest, true, accountFilterRequest, null)
						: getNodeIds(kpiRequest, true, accountFilterRequest, request.getParentId());

		if (CollectionUtils.isNotEmpty(selectedIds)) {
			kpiRequest.setIds(selectedIds.toArray(new String[0]));
		}

		return strategyFactory.getStrategy(KANBAN).getExecutiveDashboard(kpiRequest);
	}

	/** Unified method to fetch node IDs (all accounts or child accounts by parentId). */
	private Set<String> getNodeIds(
			KpiRequest kpiRequest,
			boolean isKanban,
			AccountFilterRequest accountFilterRequest,
			String parentId) {
		try {
			List<HierarchyLevel> hierarchyLevels = getHierarchyLevels(isKanban);

			if (CollectionUtils.isEmpty(hierarchyLevels)) {
				log.warn("No hierarchy levels found for isKanban={}", isKanban);
				return Collections.emptySet();
			}

			HierarchyLevel level =
					hierarchyLevels.stream()
							.filter(l -> l.getHierarchyLevelId() != null && l.getLevel() == kpiRequest.getLevel())
							.findFirst()
							.orElseThrow(
									() -> new IllegalStateException("No matching account level in hierarchy"));

			Set<AccountFilteredData> filteredList =
					isKanban
							? accountHierarchyServiceKanban.getFilteredList(accountFilterRequest)
							: accountHierarchyService.getFilteredList(accountFilterRequest);

			Set<String> filteredSet =
					filteredList.stream()
							.filter(
									a ->
											a.getLevel() == kpiRequest.getLevel()
													&& (parentId == null || parentId.equalsIgnoreCase(a.getParentId())))
							.map(AccountFilteredData::getNodeId)
							.collect(Collectors.toUnmodifiableSet());

			kpiRequest.getSelectedMap().get(level.getHierarchyLevelId()).addAll(filteredSet);
			kpiRequest.setLevelName(level.getHierarchyLevelName());
			return filteredSet;

		} catch (Exception e) {
			log.error(
					"Error retrieving node IDs (isKanban={}, parentId={}, level={})",
					isKanban,
					parentId,
					kpiRequest.getLevel(),
					e);
			return Collections.emptySet();
		}
	}

	private List<HierarchyLevel> getHierarchyLevels(boolean isKanban) {
		return isKanban
				? cacheService.getFullKanbanHierarchyLevel()
				: cacheService.getFullHierarchyLevel();
	}

	@NotNull
	private Map<String, List<String>> createSelectedMap(boolean isKanban) {
		Map<String, List<String>> selectedMap = new HashMap<>();
		List<HierarchyLevel> hierarchyLevels = getHierarchyLevels(isKanban);
		hierarchyLevels.forEach(
				hierarchyLevel -> selectedMap.put(hierarchyLevel.getHierarchyLevelId(), new ArrayList<>()));
		return selectedMap;
	}
}
