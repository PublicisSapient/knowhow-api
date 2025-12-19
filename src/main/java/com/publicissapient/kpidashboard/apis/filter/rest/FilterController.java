/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *    http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.filter.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.service.HierarchyLevelService;

@RestController
@RequiredArgsConstructor
@Tag(name = "Filter API", description = "APIs for Filter Management")
public class FilterController {

	private final HierarchyLevelService hierarchyLevelService;

	@GetMapping("/filters")
	public ResponseEntity<ServiceResponse> getFilters() {

		List<HierarchyLevel> scrumHierarchyLevels = hierarchyLevelService.getFullHierarchyLevels(false);
		List<HierarchyLevel> kanbanHierarchyLevels = hierarchyLevelService.getFullHierarchyLevels(true);
		Map<String, List<HierarchyLevel>> filtersMap = new HashMap<>();
		filtersMap.put("scrum", scrumHierarchyLevels);
		filtersMap.put("kanban", kanbanHierarchyLevels);

		ServiceResponse response = new ServiceResponse(true, "filters", filtersMap);

		return ResponseEntity.status(HttpStatus.OK).body(response);
	}
}
