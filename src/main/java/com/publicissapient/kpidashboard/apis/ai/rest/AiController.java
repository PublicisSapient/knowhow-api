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

package com.publicissapient.kpidashboard.apis.ai.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.ai.dto.request.sprint.goals.SummarizeSprintGoalsRequestDTO;
import com.publicissapient.kpidashboard.apis.ai.dto.response.sprint.goals.SummarizeSprintGoalsResponseDTO;
import com.publicissapient.kpidashboard.apis.ai.service.sprint.goals.SprintGoalsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@RestController
@RequestMapping("/ai")
@Tag(name = "AI", description = "Endpoints for managing the AI-generated content")
public class AiController {

	private final SprintGoalsService sprintGoalsService;

	@PostMapping("/sprint-goals/summary")
	@Operation(summary = "Summarize Sprint Goals", description = "Generates a summary of the provided sprint goals using AI")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successfully generated the sprint goals summary", content = {
					@Content(mediaType = "application/json", schema = @Schema(implementation = SummarizeSprintGoalsResponseDTO.class)) }),
			@ApiResponse(responseCode = "400", description = """
					Bad request. Can happen in one of the following cases:
					- No request body was provided
					- The request body does not contain the required fields
					"""), @ApiResponse(responseCode = "500", description = """
					Unexpected server error occurred. Can happen in one of the following cases:
					- AI gateway failed to process the request
					- Prompt configuration is invalid
					""") })
	public ResponseEntity<SummarizeSprintGoalsResponseDTO> summarizeSprintGoals(
			@Valid @RequestBody SummarizeSprintGoalsRequestDTO summarizeSprintGoalsRequestDTO) {
		return ResponseEntity.ok(sprintGoalsService.summarizeSprintGoals(summarizeSprintGoalsRequestDTO));
	}
}
