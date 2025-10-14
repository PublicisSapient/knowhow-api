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

package com.publicissapient.kpidashboard.apis.ai.parser;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.kpidashboard.apis.ai.dto.response.search.kpi.SearchKpiResponseDTO;

import lombok.extern.slf4j.Slf4j;

@Component("SearchParser")
@Slf4j
public class SearchParser implements ParserStategy<SearchKpiResponseDTO> {
	private static final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public SearchKpiResponseDTO parse(String chatResponse) {
		try {
			String formattedJsonString = chatResponse.substring(chatResponse.indexOf('{'));
			return objectMapper.readValue(formattedJsonString, SearchKpiResponseDTO.class);
		} catch (JsonProcessingException e) {
			log.error("Error parsing JSON: {}", e.getMessage());
			return new SearchKpiResponseDTO();
		}
	}
}
