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

package com.publicissapient.kpidashboard.apis.ai.service.search.kpi;

import java.util.List;
import java.util.Objects;

import com.knowhow.retro.aigatewayclient.client.AiGatewayClient;
import com.knowhow.retro.aigatewayclient.client.request.chat.ChatGenerationRequest;
import com.knowhow.retro.aigatewayclient.client.response.chat.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.apis.ai.parser.ParserStategy;
import com.publicissapient.kpidashboard.apis.ai.service.PromptGenerator;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.StringUtils;

import jakarta.ws.rs.InternalServerErrorException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Slf4j
@Service
public class SearchKpiServiceImpl implements SearchKPIService {

	private static final String COULD_NOT_PROCESS_SPRINT_GOALS_SUMMARIZATION_ERROR = "Could not process the sprint goals summarization.";
	private static final String PROMPT = "You are given a dictionary of KPI definitions, where each key is a kpiId and each value is the definition.\n"
			+ "KPI Dictionary: %s /nUser Query: %s /n Please return all relevant kpiIds and explain why each one matches.";

	private final AiGatewayClient aiGatewayClient;

	@Qualifier("SearchParser")
	private final ParserStategy<List<String>> parserStategy;

	@Autowired
	PromptGenerator promptGenerator;

	@Override
	public List<String> searchRelatedKpi(String userMessage) throws EntityNotFoundException {
		if (StringUtils.isEmpty(userMessage)) {
			log.error(String.format("%s No prompt configuration was found",
					"Could not process the user message to seach kpi."));
			throw new InternalServerErrorException("Could not process the user message to seach kpi.");
		}
		String prompt = promptGenerator.getKpiSearchPrompt(userMessage);
		ChatGenerationResponseDTO chatGenerationResponseDTO = aiGatewayClient
				.generate(ChatGenerationRequest.builder().prompt(prompt).build());
		if (Objects.isNull(chatGenerationResponseDTO) || StringUtils.isEmpty(chatGenerationResponseDTO.content())) {
			log.error(String.format("%s. Ai Gateway returned a null or empty response",
					COULD_NOT_PROCESS_SPRINT_GOALS_SUMMARIZATION_ERROR));
			throw new InternalServerErrorException(COULD_NOT_PROCESS_SPRINT_GOALS_SUMMARIZATION_ERROR);
		}

		return parserStategy.parse(chatGenerationResponseDTO.content());
		
	}
}
