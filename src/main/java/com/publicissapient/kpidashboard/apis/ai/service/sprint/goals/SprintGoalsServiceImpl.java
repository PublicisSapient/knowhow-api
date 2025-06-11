/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.ai.service.sprint.goals;

import java.util.Objects;

import com.publicissapient.kpidashboard.apis.ai.constants.PromptKeys;
import com.publicissapient.kpidashboard.apis.ai.model.PromptDetails;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.StringUtils;

import com.publicissapient.kpidashboard.apis.ai.dto.request.sprint.goals.SummarizeSprintGoalsRequestDTO;
import com.publicissapient.kpidashboard.apis.ai.dto.response.sprint.goals.SummarizeSprintGoalsResponseDTO;
import com.publicissapient.kpidashboard.apis.aigateway.dto.response.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.apis.aigateway.service.AiGatewayService;

import jakarta.ws.rs.InternalServerErrorException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Slf4j
@Service
public class SprintGoalsServiceImpl implements SprintGoalsService {

	private static final String COULD_NOT_PROCESS_SPRINT_GOALS_SUMMARIZATION_ERROR = "Could not process the sprint goals summarization.";

	private final CacheService cacheService;

	private final AiGatewayService aiGatewayService;

	@Override
	public SummarizeSprintGoalsResponseDTO summarizeSprintGoals(
			SummarizeSprintGoalsRequestDTO summarizeSprintGoalsRequestDTO) {
		PromptDetails sprintGoalPrompt = cacheService.getPromptDetails().get(PromptKeys.SPRINT_GOALS_PROMPT);
		if (Objects.isNull(sprintGoalPrompt) || StringUtils.isEmpty(sprintGoalPrompt.getPrompt())) {
			log.error("{} No sprint prompt was found", COULD_NOT_PROCESS_SPRINT_GOALS_SUMMARIZATION_ERROR);
			throw new InternalServerErrorException(COULD_NOT_PROCESS_SPRINT_GOALS_SUMMARIZATION_ERROR);
		}
		String prompt = String.format("%s%n%s", sprintGoalPrompt.getPrompt(),
				String.join("\n", summarizeSprintGoalsRequestDTO.sprintGoals()));
		ChatGenerationResponseDTO chatGenerationResponseDTO = aiGatewayService.generateChatResponse(prompt);
		if (Objects.isNull(chatGenerationResponseDTO) || StringUtils.isEmpty(chatGenerationResponseDTO.content())) {
			log.error(String.format("%s. Ai Gateway returned a null or empty response",
					COULD_NOT_PROCESS_SPRINT_GOALS_SUMMARIZATION_ERROR));
			throw new InternalServerErrorException(COULD_NOT_PROCESS_SPRINT_GOALS_SUMMARIZATION_ERROR);
		}
		return new SummarizeSprintGoalsResponseDTO(chatGenerationResponseDTO.content());
	}
}
