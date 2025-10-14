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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Qualifier;

import com.knowhow.retro.aigatewayclient.client.AiGatewayClient;
import com.knowhow.retro.aigatewayclient.client.response.chat.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.apis.ai.dto.response.search.kpi.SearchKpiResponseDTO;
import com.publicissapient.kpidashboard.apis.ai.parser.ParserStategy;
import com.publicissapient.kpidashboard.apis.ai.service.PromptGenerator;
import com.publicissapient.kpidashboard.apis.errors.AiGatewayServiceException;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;

import jakarta.ws.rs.InternalServerErrorException;

@ExtendWith(MockitoExtension.class)
class SearchKpiServiceImplTest {

	@Mock private AiGatewayClient aiGatewayClient;

	@Mock
	@Qualifier("SearchParser")
	private ParserStategy<SearchKpiResponseDTO> parserStategy;

	@Mock private PromptGenerator promptGenerator;

	@InjectMocks private SearchKpiServiceImpl searchKpiService;

	@Test
	void testSearchRelatedKpi_validQuery_successfulResponse() throws EntityNotFoundException {
		String userMessage = "defect leakage";
		String generatedPrompt = "some prompt";
		String aiResponseContent = "Defect Leakage, Defect Density";
		SearchKpiResponseDTO searchKpiResponseDTO = new SearchKpiResponseDTO("kpi14", "found");
		List<String> parsedResult = List.of("kpi14");

		when(promptGenerator.getKpiSearchPrompt(userMessage)).thenReturn(generatedPrompt);
		when(aiGatewayClient.generate(any()))
				.thenReturn(new ChatGenerationResponseDTO(aiResponseContent));
		when(parserStategy.parse(aiResponseContent)).thenReturn(searchKpiResponseDTO);
		// When
		SearchKpiResponseDTO result = searchKpiService.searchRelatedKpi(userMessage);
		// Then
		assertNotNull(result);
		assertEquals(parsedResult, result.getKpis());
	}

	@Test
	void testSearchRelatedKpi_emptyQuery_throwsException() {
		InternalServerErrorException exception =
				assertThrows(
						InternalServerErrorException.class, () -> searchKpiService.searchRelatedKpi(""));
		assertEquals("Could not process the user message to search kpi.", exception.getMessage());
	}

	@Test
	void testSearchRelatedKpi_aiReturnsNull_throwsException() throws EntityNotFoundException {
		String userMessage = "defect leakage";
		when(promptGenerator.getKpiSearchPrompt(userMessage)).thenReturn("prompt");
		when(aiGatewayClient.generate(any())).thenReturn(null);

		AiGatewayServiceException exception =
				assertThrows(
						AiGatewayServiceException.class, () -> searchKpiService.searchRelatedKpi(userMessage));
		assertEquals("Could not process search kpi.", exception.getMessage());
	}

	@Test
	void testSearchRelatedKpi_aiReturnsEmptyContent_throwsException() throws EntityNotFoundException {
		String userMessage = "defect leakage";
		when(promptGenerator.getKpiSearchPrompt(userMessage)).thenReturn("prompt");
		when(aiGatewayClient.generate(any())).thenReturn(new ChatGenerationResponseDTO(""));

		AiGatewayServiceException exception =
				assertThrows(
						AiGatewayServiceException.class, () -> searchKpiService.searchRelatedKpi(userMessage));
		assertEquals("Could not process search kpi.", exception.getMessage());
	}

	@Test
	void testSearchRelatedKpi_parserThrowsException() throws EntityNotFoundException {
		String userMessage = "defect";
		String responseContent = "Defect Leakage, Defect Density";
		when(promptGenerator.getKpiSearchPrompt(userMessage)).thenReturn("prompt");
		when(aiGatewayClient.generate(any()))
				.thenReturn(new ChatGenerationResponseDTO(responseContent));
		when(parserStategy.parse(responseContent)).thenThrow(new RuntimeException("Parser failure"));

		RuntimeException exception =
				assertThrows(RuntimeException.class, () -> searchKpiService.searchRelatedKpi(userMessage));
		assertEquals("Parser failure", exception.getMessage());
	}
}
