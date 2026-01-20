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

package com.publicissapient.kpidashboard.apis.hierarchy.rest;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.service.HierarchyLevelService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/hierarchylevels")
@RequiredArgsConstructor
@Tag(name = "Hierarchy Levels API", description = "APIs for Hierarchy Levels Management")
public class HierarchyLevelsController {

	private final HierarchyLevelService hierarchyLevelService;

	@Operation(
			summary = "Get Top Hierarchy Levels",
			description = "Retrieve all top-level hierarchy levels.")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully retrieved top hierarchy levels",
						content = {@Content(mediaType = "application/json")}),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@GetMapping
	public ResponseEntity<List<HierarchyLevel>> getHierarchyLevel() {

		List<HierarchyLevel> hierarchyLevels = hierarchyLevelService.getTopHierarchyLevels();

		return new ResponseEntity<>(hierarchyLevels, HttpStatus.OK);
	}
}
