/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 ******************************************************************************/
package com.publicissapient.kpidashboard.apis.ai.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.publicissapient.kpidashboard.common.model.jira.EpicHygieneResponseDTO;

/**
 * Tests for {@link EpicHygieneKpiParser}.
 *
 * <p>The parser deliberately distrusts every aggregate the LLM returns, so the assertions below
 * always feed deliberately wrong {@code readinessScore} / {@code overallStatus} values and verify
 * that the recomputed ones win.
 */
public class EpicHygieneKpiParserTest {

	private EpicHygieneKpiParser parser;

	@Before
	public void setUp() {
		parser = new EpicHygieneKpiParser();
	}

	// ---------------------------------------------------------------------
	// Happy path
	// ---------------------------------------------------------------------

	@Test
	public void parse_unweightedDimensions_averagesScoresAndMarksReady() {
		String json =
				"""
				[
					{
						"epicKey": "EPIC-1",
						"epicName": "Access control",
						"status": "Construction Ready",
						"assignee": "Ada",
						"results": [
							{ "dimension": "Business Clarity", "field": "description", "weight": 1, "score": 80 },
							{ "dimension": "Scope Definition", "field": "description", "weight": 1, "score": 90 }
						],
						"readinessScore": 5,
						"readinessGrade": "POOR",
						"overallStatus": "NOT READY",
						"recommendations": "a | b | c"
					}
				]
				""";

		List<EpicHygieneResponseDTO> verdicts = parser.parse(json);

		assertEquals(1, verdicts.size());
		EpicHygieneResponseDTO epic = verdicts.get(0);
		assertEquals("EPIC-1", epic.getEpicKey());
		assertEquals("Access control", epic.getEpicName());
		assertEquals("Ada", epic.getAssignee());
		// (80 + 90) / 2 — the bogus "5" the model returned is discarded
		assertEquals(Integer.valueOf(85), epic.getReadinessScore());
		assertEquals("GOOD", epic.getReadinessGrade());
		assertEquals("READY", epic.getOverallStatus());
		assertTrue(epic.getTopGaps().isEmpty());
		assertEquals("a | b | c", epic.getRecommendations());
	}

	@Test
	public void parse_weightedDimensions_usesWeightedAverage() {
		String json =
				"""
				[
					{
						"epicKey": "EPIC-2",
						"results": [
							{ "dimension": "Business Clarity", "weight": 3, "score": 40 },
							{ "dimension": "Scope Definition", "weight": 1, "score": 100 }
						]
					}
				]
				""";

		EpicHygieneResponseDTO epic = parser.parse(json).get(0);

		// (3*40 + 1*100) / 4 = 55
		assertEquals(Integer.valueOf(55), epic.getReadinessScore());
		assertEquals("AVERAGE", epic.getReadinessGrade());
		assertEquals("NOT READY", epic.getOverallStatus());
		assertEquals(List.of("Business Clarity"), epic.getTopGaps());
	}

	// ---------------------------------------------------------------------
	// Score normalisation
	// ---------------------------------------------------------------------

	@Test
	public void parse_outOfRangeScores_areClampedTo0And100() {
		String json =
				"""
				[
					{
						"epicKey": "EPIC-3",
						"results": [
							{ "dimension": "Over", "weight": 1, "score": 150 },
							{ "dimension": "Under", "weight": 1, "score": -20 }
						]
					}
				]
				""";

		EpicHygieneResponseDTO epic = parser.parse(json).get(0);

		assertEquals(Integer.valueOf(100), epic.getResults().get(0).getScore());
		assertEquals(Integer.valueOf(0), epic.getResults().get(1).getScore());
		assertEquals(Integer.valueOf(50), epic.getReadinessScore());
		assertEquals("POOR", epic.getReadinessGrade());
		assertEquals(List.of("Under"), epic.getTopGaps());
	}

	@Test
	public void parse_nullScore_isTreatedAsNotApplicableAndExcludedFromAverage() {
		String json =
				"""
				[
					{
						"epicKey": "EPIC-4",
						"results": [
							{ "dimension": "Not applicable here", "weight": 5, "score": null },
							{ "dimension": "Delivery Readiness", "weight": 1, "score": 80 }
						]
					}
				]
				""";

		EpicHygieneResponseDTO epic = parser.parse(json).get(0);

		assertEquals(Integer.valueOf(80), epic.getReadinessScore());
		assertEquals("READY", epic.getOverallStatus());
		assertTrue(epic.getTopGaps().isEmpty());
	}

	@Test
	public void parse_allScoresNotApplicable_yieldsZeroAndNotReady() {
		String json =
				"""
				[
					{ "epicKey": "EPIC-5", "results": [ { "dimension": "A", "score": null } ] }
				]
				""";

		EpicHygieneResponseDTO epic = parser.parse(json).get(0);

		assertEquals(Integer.valueOf(0), epic.getReadinessScore());
		assertEquals("POOR", epic.getReadinessGrade());
		assertEquals("NOT READY", epic.getOverallStatus());
	}

	@Test
	public void parse_missingOrInvalidWeights_defaultToOne() {
		String json =
				"""
				[
					{
						"epicKey": "EPIC-6",
						"results": [
							{ "dimension": "NoWeight", "score": 60 },
							{ "dimension": "ZeroWeight", "weight": 0, "score": 100 },
							{ "dimension": "NegativeWeight", "weight": -4, "score": 80 }
						]
					}
				]
				""";

		EpicHygieneResponseDTO epic = parser.parse(json).get(0);

		epic.getResults().forEach(dimension -> assertEquals(Double.valueOf(1d), dimension.getWeight()));
		// (60 + 100 + 80) / 3 = 80
		assertEquals(Integer.valueOf(80), epic.getReadinessScore());
	}

	// ---------------------------------------------------------------------
	// Derived fields
	// ---------------------------------------------------------------------

	@Test
	public void parse_topGaps_areCappedAtThreeAndOrderedByWeightThenScore() {
		String json =
				"""
				[
					{
						"epicKey": "EPIC-7",
						"results": [
							{ "dimension": "Light",   "weight": 1,  "score": 10 },
							{ "dimension": "Heaviest","weight": 10, "score": 60 },
							{ "dimension": "Heavy",   "weight": 5,  "score": 20 },
							{ "dimension": "Medium",  "weight": 3,  "score": 30 },
							{ "dimension": "Fine",    "weight": 8,  "score": 95 }
						]
					}
				]
				""";

		EpicHygieneResponseDTO epic = parser.parse(json).get(0);

		assertEquals(List.of("Heaviest", "Heavy", "Medium"), epic.getTopGaps());
	}

	@Test
	public void parse_gradeBoundaries_followTheDocumentedBands() {
		assertEquals("GOOD", parser.parse(singleDimension(76)).get(0).getReadinessGrade());
		assertEquals("AVERAGE", parser.parse(singleDimension(75)).get(0).getReadinessGrade());
		assertEquals("AVERAGE", parser.parse(singleDimension(51)).get(0).getReadinessGrade());
		assertEquals("POOR", parser.parse(singleDimension(50)).get(0).getReadinessGrade());
	}

	@Test
	public void parse_readyRequiresEveryDimensionAtThreshold() {
		assertEquals("READY", parser.parse(singleDimension(70)).get(0).getOverallStatus());
		assertEquals("NOT READY", parser.parse(singleDimension(69)).get(0).getOverallStatus());
	}

	// ---------------------------------------------------------------------
	// Robustness
	// ---------------------------------------------------------------------

	@Test
	public void parse_stripsMarkdownFencesAndSurroundingProse() {
		String json =
				"Here is the analysis you asked for:\n```json\n"
						+ "[ { \"epicKey\": \"EPIC-8\", \"results\": [ { \"dimension\": \"A\", \"score\": 90 } ] } ]"
						+ "\n```\nHope that helps!";

		List<EpicHygieneResponseDTO> verdicts = parser.parse(json);

		assertEquals(1, verdicts.size());
		assertEquals("EPIC-8", verdicts.get(0).getEpicKey());
		assertEquals(Integer.valueOf(90), verdicts.get(0).getReadinessScore());
	}

	@Test
	public void parse_missingResultsArray_doesNotBlowUp() {
		EpicHygieneResponseDTO epic = parser.parse("[ { \"epicKey\": \"EPIC-9\" } ]").get(0);

		assertTrue(epic.getResults().isEmpty());
		assertEquals(Integer.valueOf(0), epic.getReadinessScore());
		assertEquals("NOT READY", epic.getOverallStatus());
	}

	@Test
	public void parse_unknownPropertiesAreIgnored() {
		String json =
				"[ { \"epicKey\": \"EPIC-10\", \"podName\": \"POD 5\", \"results\": "
						+ "[ { \"dimension\": \"A\", \"score\": 88, \"missingNfrs\": \"Yes\" } ] } ]";

		assertEquals(Integer.valueOf(88), parser.parse(json).get(0).getReadinessScore());
	}

	@Test
	public void parse_nullBlankOrNonArrayResponses_returnEmptyList() {
		assertTrue(parser.parse(null).isEmpty());
		assertTrue(parser.parse("   ").isEmpty());
		assertTrue(parser.parse("I could not analyse those epics.").isEmpty());
		assertTrue(parser.parse("]").isEmpty());
	}

	@Test
	public void parse_malformedJson_returnsEmptyList() {
		// Looks like an array (so it gets past the bracket sniffing) but Jackson cannot
		// read it.
		assertTrue(parser.parse("[ { \"epicKey\": \"EPIC-11\" ").isEmpty());
		assertTrue(parser.parse("[ { \"epicKey\": \"EPIC-11\" ]").isEmpty());
	}

	@Test
	public void parse_emptyArray_returnsEmptyList() {
		assertTrue(parser.parse("[]").isEmpty());
	}

	private String singleDimension(int score) {
		return "[ { \"epicKey\": \"EPIC-X\", \"results\": [ { \"dimension\": \"A\", \"weight\": 1, \"score\": "
				+ score
				+ " } ] } ]";
	}
}
