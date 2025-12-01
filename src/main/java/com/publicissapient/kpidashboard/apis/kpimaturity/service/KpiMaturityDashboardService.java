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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceKanbanImpl;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.KpiMaturityRequest;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.KpiMaturityResponseDTO;
import com.publicissapient.kpidashboard.apis.model.AccountFilterRequest;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service implementation for executive dashboard functionality. Uses Strategy pattern to delegate
 * to appropriate dashboard strategy (Kanban/Scrum).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiMaturityDashboardService {

	private static final String CLOSED = "CLOSED";

	private final AccountHierarchyServiceKanbanImpl accountHierarchyServiceKanban;
	private final KanbanExecutiveDashboardService kanbanExecutiveDashboardService;
	private final CacheService cacheService;

	public KpiMaturityResponseDTO getExecutiveDashboardKanban(
			KpiMaturityRequest request) {
		log.info(
				"Processing Kanban executive dashboard request with parentId: {}", request.parentNodeId());

		KpiRequest kpiRequest = new KpiRequest();
		kpiRequest.setLevel(request.levelNumber());
		kpiRequest.setLabel(request.levelName());
		kpiRequest.setSelectedMap(createSelectedMap());
		kpiRequest.getSelectedMap().put("date", List.of("Weeks"));
		kpiRequest.setIds(new String[] {"5"});
		kpiRequest.setSprintIncluded(List.of(CLOSED));

		AccountFilterRequest accountFilterRequest = new AccountFilterRequest();
		accountFilterRequest.setKanban(true);
		accountFilterRequest.setSprintIncluded(List.of(CLOSED));

		Set<String> selectedIds =
				StringUtils.isBlank(request.parentNodeId())
						? getNodeIds(kpiRequest, accountFilterRequest, null)
						: getNodeIds(kpiRequest, accountFilterRequest, request.parentNodeId());

		if (CollectionUtils.isNotEmpty(selectedIds)) {
			kpiRequest.setIds(selectedIds.toArray(new String[0]));
		}

		return kanbanExecutiveDashboardService.getExecutiveDashboard(kpiRequest);
	}

	/** Unified method to fetch node IDs (all accounts or child accounts by parentId). */
	private Set<String> getNodeIds(
			KpiRequest kpiRequest,
			AccountFilterRequest accountFilterRequest,
			String parentId) {
		try {
			List<HierarchyLevel> hierarchyLevels = getHierarchyLevels();

			if (CollectionUtils.isEmpty(hierarchyLevels)) {
				log.warn("No hierarchy levels found for isKanban={}", true);
				return Collections.emptySet();
			}

			HierarchyLevel level =
					hierarchyLevels.stream()
							.filter(l -> l.getHierarchyLevelId() != null && l.getLevel() == kpiRequest.getLevel())
							.findFirst()
							.orElseThrow(
									() -> new IllegalStateException("No matching account level in hierarchy"));

			Set<AccountFilteredData> filteredList =
                    accountHierarchyServiceKanban.getFilteredList(accountFilterRequest);

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
					true,
					parentId,
					kpiRequest.getLevel(),
					e);
			return Collections.emptySet();
		}
	}

	private List<HierarchyLevel> getHierarchyLevels() {
		return cacheService.getFullKanbanHierarchyLevel();
	}

	@NotNull
	private Map<String, List<String>> createSelectedMap() {
		Map<String, List<String>> selectedMap = new HashMap<>();
		List<HierarchyLevel> hierarchyLevels = getHierarchyLevels();
		hierarchyLevels.forEach(
				hierarchyLevel -> selectedMap.put(hierarchyLevel.getHierarchyLevelId(), new ArrayList<>()));
		return selectedMap;
	}
}
