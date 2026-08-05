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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.kpidashboard.common.model.jira.EpicHygieneResponseDTO;
import com.publicissapient.kpidashboard.common.model.jira.EpicHygieneResponseDTO.DimensionResult;
import com.publicissapient.kpidashboard.common.util.HygienePromptBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * Parses the Epic readiness JSON array produced by the LLM for KPI312.
 *
 * <p>Everything numeric that the dashboard relies on is <b>recomputed here</b> rather than trusted
 * from the model: dimension scores are clamped to 0-100, the overall readiness score is derived as
 * a weight-based average of the applicable dimensions, and grade / status / top gaps follow
 * deterministically. The LLM therefore only has to do what it is good at — judging evidence — while
 * arithmetic stays reproducible.
 */
@Slf4j
@Component("epicHygieneParser")
public class EpicHygieneKpiParser implements ParserStategy<List<EpicHygieneResponseDTO>> {

	/** A dimension at or above this score is considered satisfied. */
	static final int READY_DIMENSION_THRESHOLD = 70;

	/** Readiness score at or above this value grades as GOOD. */
	static final int GOOD_GRADE_THRESHOLD = 76;

	/** Readiness score at or above this value grades as AVERAGE. */
	static final int AVERAGE_GRADE_THRESHOLD = 51;

	private static final int MIN_SCORE = 0;
	private static final int MAX_SCORE = 100;
	private static final int MAX_TOP_GAPS = 3;

	private static final String GRADE_GOOD = "GOOD";
	private static final String GRADE_AVERAGE = "AVERAGE";
	private static final String GRADE_POOR = "POOR";
	private static final String STATUS_READY = "READY";
	private static final String STATUS_NOT_READY = "NOT READY";

	private static final ObjectMapper OBJECT_MAPPER =
			new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	@Override
	public List<EpicHygieneResponseDTO> parse(String chatResponse) {
		if (chatResponse == null || chatResponse.isBlank()) {
			log.error("Epic hygiene parser: chat response was null or blank");
			return new ArrayList<>();
		}
		// Strip any pre/post prose or markdown fences the LLM may add around the JSON
		// array before handing it to Jackson.
		int start = chatResponse.indexOf('[');
		int end = chatResponse.lastIndexOf(']');
		if (start < 0 || end <= start) {
			log.error("Epic hygiene parser: response did not contain a JSON array: {}", chatResponse);
			return new ArrayList<>();
		}
		try {
			List<EpicHygieneResponseDTO> parsed =
					OBJECT_MAPPER.readValue(chatResponse.substring(start, end + 1), new TypeReference<>() {});
			List<EpicHygieneResponseDTO> verdicts =
					parsed.stream().filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));
			verdicts.forEach(this::normaliseVerdict);
			return verdicts;
		} catch (JsonProcessingException e) {
			log.error("Epic hygiene parser: error parsing JSON: {}", e.getMessage());
			return new ArrayList<>();
		}
	}

	/**
	 * Clamps the dimension scores and re-derives every aggregate field of a single Epic verdict so
	 * the values are authoritative regardless of what the LLM returned.
	 */
	private void normaliseVerdict(EpicHygieneResponseDTO verdict) {
		List<DimensionResult> dimensions =
				verdict.getResults() == null
						? new ArrayList<>()
						: verdict.getResults().stream()
								.filter(Objects::nonNull)
								.collect(Collectors.toCollection(ArrayList::new));
		verdict.setResults(dimensions);

		List<DimensionResult> applicable = new ArrayList<>();
		for (DimensionResult dimension : dimensions) {
			dimension.setWeight(normaliseWeight(dimension.getWeight()));
			// A null score means "not applicable" — it must not drag the average down.
			if (dimension.getScore() != null) {
				dimension.setScore(clamp(dimension.getScore()));
				applicable.add(dimension);
			}
		}

		verdict.setReadinessScore(weightedAverage(applicable));
		verdict.setReadinessGrade(grade(verdict.getReadinessScore()));
		verdict.setOverallStatus(overallStatus(applicable));
		verdict.setTopGaps(topGaps(applicable));
	}

	/** Weighted mean of the applicable dimension scores, rounded half-up to a whole number. */
	private int weightedAverage(List<DimensionResult> applicable) {
		double totalWeight = applicable.stream().mapToDouble(DimensionResult::getWeight).sum();
		if (totalWeight <= 0) {
			return MIN_SCORE;
		}
		double earned = applicable.stream().mapToDouble(d -> d.getWeight() * d.getScore()).sum();
		return clamp((int) Math.round(earned / totalWeight));
	}

	private String grade(int readinessScore) {
		if (readinessScore >= GOOD_GRADE_THRESHOLD) {
			return GRADE_GOOD;
		}
		return readinessScore >= AVERAGE_GRADE_THRESHOLD ? GRADE_AVERAGE : GRADE_POOR;
	}

	/**
	 * An Epic is READY only when every applicable dimension clears {@link #READY_DIMENSION_THRESHOLD}
	 * — a strong overall average must not mask one badly under-defined dimension.
	 */
	private String overallStatus(List<DimensionResult> applicable) {
		if (applicable.isEmpty()) {
			return STATUS_NOT_READY;
		}
		boolean allSatisfied =
				applicable.stream().allMatch(d -> d.getScore() >= READY_DIMENSION_THRESHOLD);
		return allSatisfied ? STATUS_READY : STATUS_NOT_READY;
	}

	/** Up to three under-performing dimensions, heaviest weight first then lowest score first. */
	private List<String> topGaps(List<DimensionResult> applicable) {
		return applicable.stream()
				.filter(d -> d.getScore() < READY_DIMENSION_THRESHOLD)
				.sorted(
						Comparator.comparingDouble(DimensionResult::getWeight)
								.reversed()
								.thenComparingInt(DimensionResult::getScore))
				.map(DimensionResult::getDimension)
				.filter(Objects::nonNull)
				.limit(MAX_TOP_GAPS)
				.toList();
	}

	private double normaliseWeight(Double weight) {
		return weight == null || weight.isNaN() || weight <= 0
				? HygienePromptBuilder.DEFAULT_RULE_WEIGHT
				: weight;
	}

	private int clamp(int score) {
		return Math.max(MIN_SCORE, Math.min(MAX_SCORE, score));
	}
}




