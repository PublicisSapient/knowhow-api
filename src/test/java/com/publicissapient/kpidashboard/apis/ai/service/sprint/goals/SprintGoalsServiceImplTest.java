/*
 * ******************************************************************************
 *  * Copyright 2014 CapitalOne, LLC.
 *  * Further development Copyright 2022 Sapient Corporation.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *    http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  *
 *  *****************************************************************************
 *
 */

package com.publicissapient.kpidashboard.apis.ai.service.sprint.goals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.publicissapient.kpidashboard.apis.ai.constants.PromptKeys;
import com.publicissapient.kpidashboard.apis.ai.model.PromptDetails;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.apis.ai.dto.request.sprint.goals.SummarizeSprintGoalsRequestDTO;
import com.publicissapient.kpidashboard.apis.ai.dto.response.sprint.goals.SummarizeSprintGoalsResponseDTO;
import com.publicissapient.kpidashboard.apis.aigateway.dto.response.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.apis.aigateway.service.AiGatewayService;

import jakarta.ws.rs.InternalServerErrorException;

@ExtendWith(MockitoExtension.class)
class SprintGoalsServiceImplTest {

	@Mock
	private CacheService cacheService;

	@Mock
	private AiGatewayService aiGatewayService;

	@InjectMocks
	private SprintGoalsServiceImpl sprintGoalsService;

	@BeforeEach
	public void setUp() {
		PromptDetails promptDetails = new PromptDetails(PromptKeys.SPRINT_GOALS_PROMPT, "Prompt for sprint goals");
		when(cacheService.getPromptDetails()).thenReturn(Map.of(PromptKeys.SPRINT_GOALS_PROMPT, promptDetails));

	}

	@Test
	void testSummarizeSprintGoalsSuccess() {
		SummarizeSprintGoalsRequestDTO requestDTO = new SummarizeSprintGoalsRequestDTO(List.of("Goal 1", "Goal 2"));
		ChatGenerationResponseDTO chatResponse = new ChatGenerationResponseDTO("Summary of goals");
		when(aiGatewayService.generateChatResponse(anyString())).thenReturn(chatResponse);

		SummarizeSprintGoalsResponseDTO responseDTO = sprintGoalsService.summarizeSprintGoals(requestDTO);

		assertNotNull(responseDTO);
		assertEquals("Summary of goals", responseDTO.summary());
	}

	@Test
	void testSummarizeSprintGoalsNoPromptConfig() {
		when(cacheService.getPromptDetails()).thenReturn(Map.of());

		SummarizeSprintGoalsRequestDTO requestDTO = new SummarizeSprintGoalsRequestDTO(List.of("Goal 1", "Goal 2"));

		assertThrows(InternalServerErrorException.class, () -> sprintGoalsService.summarizeSprintGoals(requestDTO));
	}

	@Test
	void testSummarizeSprintGoalsEmptyAiResponse() {
		SummarizeSprintGoalsRequestDTO requestDTO = new SummarizeSprintGoalsRequestDTO(List.of("Goal 1", "Goal 2"));
		when(aiGatewayService.generateChatResponse(anyString())).thenReturn(new ChatGenerationResponseDTO(""));

		assertThrows(InternalServerErrorException.class, () -> sprintGoalsService.summarizeSprintGoals(requestDTO));
	}
}
