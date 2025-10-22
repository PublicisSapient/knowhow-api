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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component("AiRecommendation")
@Slf4j
public class AiRecommendation implements ParserStategy<Object> {

	/**
	 * Parses a JSON string and converts it into a JSON object.
	 *
	 * @param jsonString The JSON string to be parsed. It is expected to contain a valid JSON object.
	 * @return A {@link JsonNode} representing the parsed JSON object, or an empty {@link Object} if
	 *     parsing fails.
	 */
	@Override
	public Object parse(String jsonString) {
		try {
			String formattedJsonString = jsonString.substring(jsonString.indexOf('{'));
			return new ObjectMapper().readTree(formattedJsonString);
		} catch (JsonProcessingException e) {
			log.error("Error parsing JSON: {}", e.getMessage());
			return new Object();
		}
	}
}
