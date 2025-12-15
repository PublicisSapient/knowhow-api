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

package com.publicissapient.kpidashboard.apis.ai.service.sprint.goals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.knowhow.retro.aigatewayclient.client.AiGatewayClient;
import com.knowhow.retro.aigatewayclient.client.response.chat.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.common.constant.PromptKeys;
import com.publicissapient.kpidashboard.apis.ai.dto.request.sprint.goals.SummarizeSprintGoalsRequestDTO;
import com.publicissapient.kpidashboard.apis.ai.dto.response.sprint.goals.SummarizeSprintGoalsResponseDTO;
import com.publicissapient.kpidashboard.common.model.application.PromptDetails;
import com.publicissapient.kpidashboard.apis.ai.service.PromptGenerator;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;

import jakarta.ws.rs.InternalServerErrorException;

@ExtendWith(MockitoExtension.class)
class SprintGoalsServiceImplTest {

	@Mock private PromptGenerator promptGenerator;

	@Mock private AiGatewayClient aiGatewayClient;

	@InjectMocks private SprintGoalsServiceImpl sprintGoalsService;

	@BeforeEach
	public void setUp() throws EntityNotFoundException {
		PromptDetails promptDetails =
				new PromptDetails(
						PromptKeys.SPRINT_GOALS_SUMMARY,
						"Prompt for sprint goals",
						"Summarize the sprint goals",
						List.of("Please summarize the sprint goals provided."),
						"SPRINT_GOALS_PLACEHOLDER",
						"Summary format",
						List.of("SPRINT_GOALS_PLACEHOLDER"));

		when(promptGenerator.getPromptDetails(any())).thenReturn(promptDetails);
		sprintGoalsService = new SprintGoalsServiceImpl(promptGenerator, aiGatewayClient);
	}

	@Test
	void testSummarizeSprintGoalsSuccess() throws EntityNotFoundException, IOException {
		SummarizeSprintGoalsRequestDTO requestDTO =
				new SummarizeSprintGoalsRequestDTO(List.of("Goal 1", "Goal 2"));
		ChatGenerationResponseDTO chatResponse = new ChatGenerationResponseDTO("Summary of goals");
		when(aiGatewayClient.generate(any())).thenReturn(chatResponse);

		SummarizeSprintGoalsResponseDTO responseDTO =
				sprintGoalsService.summarizeSprintGoals(requestDTO);

		assertNotNull(responseDTO);
		assertEquals("Summary of goals", responseDTO.summary());
	}

	@Test
	void testSummarizeSprintGoalsNoPromptConfig() throws EntityNotFoundException {
		when(promptGenerator.getPromptDetails(any())).thenReturn(null);

		SummarizeSprintGoalsRequestDTO requestDTO =
				new SummarizeSprintGoalsRequestDTO(List.of("Goal 1", "Goal 2"));

		assertThrows(
				InternalServerErrorException.class,
				() -> sprintGoalsService.summarizeSprintGoals(requestDTO));
	}

	@Test
	void testSummarizeSprintGoalsEmptyAiResponse() throws IOException {
		SummarizeSprintGoalsRequestDTO requestDTO =
				new SummarizeSprintGoalsRequestDTO(List.of("Goal 1", "Goal 2"));
		when(aiGatewayClient.generate(any())).thenReturn(new ChatGenerationResponseDTO(""));

		InternalServerErrorException exception =
				assertThrows(
						InternalServerErrorException.class,
						() -> sprintGoalsService.summarizeSprintGoals(requestDTO));
		assertEquals("Could not process the sprint goals summarization.", exception.getMessage());
	}
}
