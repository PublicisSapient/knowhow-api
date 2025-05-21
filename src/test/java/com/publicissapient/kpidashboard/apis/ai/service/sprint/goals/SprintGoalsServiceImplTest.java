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

import com.publicissapient.kpidashboard.apis.ai.config.sprint.SprintPromptConfig;
import com.publicissapient.kpidashboard.apis.ai.dto.request.sprint.goals.SummarizeSprintGoalsRequestDTO;
import com.publicissapient.kpidashboard.apis.ai.dto.response.sprint.goals.SummarizeSprintGoalsResponseDTO;
import com.publicissapient.kpidashboard.apis.aigateway.dto.response.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.apis.aigateway.service.AiGatewayService;
import jakarta.ws.rs.InternalServerErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SprintGoalsServiceImplTest {

    @Mock
    private SprintPromptConfig sprintPromptConfig;

    @Mock
    private AiGatewayService aiGatewayService;

    @InjectMocks
    private SprintGoalsServiceImpl sprintGoalsService;

    @BeforeEach
    public void setUp() {
        SprintPromptConfig.Goals goals = mock(SprintPromptConfig.Goals.class);
        when(sprintPromptConfig.getGoals()).thenReturn(goals);
        when(goals.getPrompt()).thenReturn("Summarize the following sprint goals:");
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
        when(sprintPromptConfig.getGoals().getPrompt()).thenReturn(null);

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
