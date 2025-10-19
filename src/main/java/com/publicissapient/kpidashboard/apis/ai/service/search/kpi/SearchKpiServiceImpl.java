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

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.StringUtils;

import com.knowhow.retro.aigatewayclient.client.AiGatewayClient;
import com.knowhow.retro.aigatewayclient.client.request.chat.ChatGenerationRequest;
import com.knowhow.retro.aigatewayclient.client.response.chat.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.apis.ai.dto.response.search.kpi.SearchKpiResponseDTO;
import com.publicissapient.kpidashboard.apis.ai.parser.ParserStategy;
import com.publicissapient.kpidashboard.apis.ai.service.PromptGenerator;
import com.publicissapient.kpidashboard.apis.errors.AiGatewayServiceException;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;

import jakarta.ws.rs.InternalServerErrorException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Slf4j
@Service
public class SearchKpiServiceImpl implements SearchKPIService {

	private static final String SEARCH_KPI_ERROR_MESSAGE = "Could not process search kpi.";

	private final AiGatewayClient aiGatewayClient;

	@Qualifier("SearchParser")
	private final ParserStategy<SearchKpiResponseDTO> parserStategy;

	@Autowired PromptGenerator promptGenerator;

	@Override
	public SearchKpiResponseDTO searchRelatedKpi(String userMessage) throws EntityNotFoundException {
		if (StringUtils.isEmpty(userMessage)) {
			log.error(
					String.format(
							"%s No prompt was found", "Could not process the user message to search kpi."));
			throw new InternalServerErrorException("Could not process the user message to search kpi.");
		}
		String prompt = promptGenerator.getKpiSearchPrompt(userMessage);
		ChatGenerationResponseDTO chatGenerationResponseDTO =
				aiGatewayClient.generate(ChatGenerationRequest.builder().prompt(prompt).build());
		if (Objects.isNull(chatGenerationResponseDTO)
				|| StringUtils.isEmpty(chatGenerationResponseDTO.content())) {
			log.error(
					String.format(
							"%s. Ai Gateway returned a null or empty response", SEARCH_KPI_ERROR_MESSAGE));
			throw new AiGatewayServiceException(SEARCH_KPI_ERROR_MESSAGE);
		}

		return parserStategy.parse(chatGenerationResponseDTO.content());
	}
}
