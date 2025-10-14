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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.kpidashboard.apis.ai.dto.request.sprint.goals.SummarizeSprintGoalsRequestDTO;
import com.publicissapient.kpidashboard.apis.ai.dto.response.sprint.goals.SummarizeSprintGoalsResponseDTO;
import com.publicissapient.kpidashboard.apis.ai.service.search.kpi.SearchKPIService;
import com.publicissapient.kpidashboard.apis.ai.service.sprint.goals.SprintGoalsService;

@ExtendWith(MockitoExtension.class)
class AiControllerTest {

	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Mock private SprintGoalsService sprintGoalsService;

	@Mock private SearchKPIService searchKPIService;

	@InjectMocks private AiController aiController;

	@BeforeEach
	public void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(aiController).build();
	}

	@Test
	void testSummarizeSprintGoalsSuccess() throws Exception {
		when(sprintGoalsService.summarizeSprintGoals(any(SummarizeSprintGoalsRequestDTO.class)))
				.thenReturn(new SummarizeSprintGoalsResponseDTO("Summary of goals"));
		SummarizeSprintGoalsRequestDTO requestDTO =
				new SummarizeSprintGoalsRequestDTO(List.of("Goal 1", "Goal 2"));

		mockMvc
				.perform(
						post("/ai/sprint-goals/summary")
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(requestDTO)))
				.andExpect(status().isOk())
				.andExpect(
						content().json("""
						{
							"summary": "Summary of goals"
						}
						"""));
	}

	@Test
	void testSummarizeSprintGoalsValidationError() throws Exception {
		SummarizeSprintGoalsRequestDTO requestDTO =
				new SummarizeSprintGoalsRequestDTO(Collections.emptyList());

		mockMvc
				.perform(
						post("/ai/sprint-goals/summary")
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(requestDTO)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void testSearchKpisNegative() throws Exception {
		mockMvc
				.perform(get("/ai/kpisearch").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isBadRequest());
	}

	@Test
	void testSearchKpisPositive() throws Exception {
		mockMvc
				.perform(get("/ai/kpisearch").param("query", "kpiName").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk());
	}
}
