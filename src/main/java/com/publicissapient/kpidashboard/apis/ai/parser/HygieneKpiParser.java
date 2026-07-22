package com.publicissapient.kpidashboard.apis.ai.parser;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.kpidashboard.apis.ai.dto.response.HygieneKpiResponseDTO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component("hygieneParser")
public class HygieneKpiParser implements ParserStategy<List<HygieneKpiResponseDTO>> {
	private static final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public List<HygieneKpiResponseDTO> parse(String chatResponse) {
		try {
			// Strip any pre/post prose or markdown fences the LLM may add
			// around the JSON array before handing it to Jackson.
			int start = chatResponse.indexOf('[');
			int end = chatResponse.lastIndexOf(']');
			if (start < 0 || end <= start) {
				log.error("Chat response did not contain a JSON array: {}", chatResponse);
				return new ArrayList<>();
			}
			String jsonArrayString = chatResponse.substring(start, end + 1);
			return objectMapper.readValue(jsonArrayString, new TypeReference<>() {});
		} catch (JsonProcessingException e) {
			log.error("Error parsing JSON: {}", e.getMessage());
			return new ArrayList<>();
		}
	}
}
