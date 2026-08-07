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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1710;

import java.util.Arrays;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.publicissapient.kpidashboard.common.constant.PromptKeys;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Inserts the {@code epic-hygiene} prompt into the {@code prompt_details} collection so the Epic
 * Hygiene KPI (kpi312) reads its prompt template from the DB instead of a hardcoded constant.
 *
 * <p>The prompt is the Epic Readiness Analyzer agent, generalised: the readiness dimensions are NOT
 * hardcoded in the template. They arrive through {@code HYGIENE_RULES_PLACEHOLDER}, rendered from
 * the project's {@code jiraFieldsSelectionKPI312} field mapping, so a project can add, remove or
 * re-word a dimension without a release. Each dimension entry declares the Jira field carrying the
 * evidence, a relative weight and the 0-100 scoring criteria.
 *
 * <p>The response is a JSON array rather than the CSV the original agent emitted, because the
 * result is consumed programmatically by {@code EpicHygieneKpiParser} which re-derives every
 * aggregate (readiness score, grade, overall status, top gaps) from the dimension scores.
 */
@Slf4j
@RequiredArgsConstructor
@ChangeUnit(
		id = "epic_hygiene_prompt_details",
		order = "17177",
		author = "kunkambl",
		systemVersion = "17.1.0")
public class EpicHygienePromptDetailsChangeUnit {

	private static final String PROMPT_DETAILS_COLLECTION = "prompt_details";
	private static final String EPIC_HYGIENE_PROMPT = PromptKeys.EPIC_HYGIENE_PROMPT;

	private static final String KEY = "key";
	private static final String CONTEXT = "context";
	private static final String TASK = "task";
	private static final String INSTRUCTIONS = "instructions";
	private static final String INPUT = "input";
	private static final String OUTPUT_FORMAT = "outputFormat";
	private static final String PLACEHOLDERS = "placeHolders";

	private static final String HYGIENE_RULES_PLACEHOLDER = "HYGIENE_RULES_PLACEHOLDER";
	private static final String JIRA_ISSUES_PLACEHOLDER = "JIRA_ISSUES_PLACEHOLDER";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		insertEpicHygienePromptDetails();
	}

	@RollbackExecution
	public void rollback() {
		deleteEpicHygienePromptDetails();
	}

	/** Inserts / upserts the complete epic hygiene prompt document. */
	private void insertEpicHygienePromptDetails() {
		log.info("Inserting {} prompt details", EPIC_HYGIENE_PROMPT);

		String context =
				"You are an Expert Epic Readiness Analyzer Agent. Your job is to assess how ready each Jira "
						+ "Epic is to enter construction, scoring it on a set of readiness dimensions supplied to you at runtime, "
						+ "and to produce a strict, evidence-based verdict per Epic.";

		String input =
				"=== Readiness Dimensions ===\n"
						+ "A numbered list of INDEPENDENT dimension entries. Each entry declares:\n"
						+ "  - ruleName: the dimension name - copy it VERBATIM into results[].dimension\n"
						+ "  - field   : the Jira Epic field this dimension inspects for evidence\n"
						+ "  - weight  : a positive number setting how much this dimension contributes to the readiness score\n"
						+ "  - criteria: what evidence to look for and how to grade it from 0 to 100\n"
						+ "Several entries MAY declare the SAME field (for example a 'Business Clarity' dimension and a "
						+ "'Scope Definition' dimension, both written against 'description'). Treat every entry as a completely "
						+ "separate dimension: score it using ONLY its own criteria and emit its own result element. Never merge, "
						+ "deduplicate or skip dimensions just because they share a field.\n"
						+ "Weights are RELATIVE, not percentages, and need not add up to any particular total: a dimension of "
						+ "weight 10 moves the readiness score ten times as much as a dimension of weight 1. Dimensions that were "
						+ "configured without a weight arrive with weight 1.\n"
						+ HYGIENE_RULES_PLACEHOLDER
						+ "\n\n=== Jira Epics (JSON array) ===\n"
						+ JIRA_ISSUES_PLACEHOLDER;

		String outputFormat =
				"Return a JSON ARRAY - one element per input Epic, in the same order as the input. No "
						+ "markdown, no prose, no CSV, no code fences, no trailing commentary. Schema per element:\n"
						+ "[\n"
						+ "  {\n"
						+ "    \"epicKey\": \"<epic.number>\",\n"
						+ "    \"epicName\": \"<epic.name or the Epic name field, else epic.number>\",\n"
						+ "    \"status\": \"<epic.status>\",\n"
						+ "    \"assignee\": \"<epic.assigneeName or 'Unassigned'>\",\n"
						+ "    \"results\": [\n"
						+ "      {\n"
						+ "        \"dimension\": \"<ruleName copied VERBATIM from the dimension entry>\",\n"
						+ "        \"field\": \"<the field declared on that dimension entry>\",\n"
						+ "        \"weight\": <the weight declared on that dimension entry, copied verbatim>,\n"
						+ "        \"score\": <int 0-100, or null when the dimension does not apply to this Epic>,\n"
						+ "        \"observed\": \"<actual field value or evidence found, or 'null'>\",\n"
						+ "        \"reason\": \"<one-line justification citing the observed value>\"\n"
						+ "      }\n"
						+ "    ],\n"
						+ "    \"readinessScore\": <int 0-100, WEIGHT-BASED average of the dimension scores>,\n"
						+ "    \"readinessGrade\": \"GOOD | AVERAGE | POOR\",\n"
						+ "    \"overallStatus\": \"READY | NOT READY\",\n"
						+ "    \"topGaps\": [\"<up to 3 dimension names scoring below 70, heaviest weight first>\"],\n"
						+ "    \"recommendations\": \"<3-5 fixes joined by ' | '>\"\n"
						+ "  }\n"
						+ "]\n\n"
						+ "Hard Constraints:\n"
						+ "- Score EVERY dimension entry for EVERY Epic; never skip a dimension and never skip an Epic.\n"
						+ "- results MUST contain EXACTLY one element per supplied dimension entry - same count, same order - "
						+ "including when several entries share the same field.\n"
						+ "- results[].dimension MUST be the ruleName copied verbatim; never rename, merge or invent dimensions.\n"
						+ "- results[].weight MUST be the weight supplied for that dimension entry, copied verbatim; never "
						+ "invent, rescale or renormalise weights.\n"
						+ "- results[].score MUST be an integer between 0 and 100, or null when the dimension genuinely does not "
						+ "apply. Never return a score without evidence: missing evidence scores low, it is not null.\n"
						+ "- epicKey MUST be copied verbatim from the input Epic. NEVER invent an Epic that was not supplied.\n"
						+ "- reason MUST cite the exact field name and the evidence observed.\n"
						+ "- Never invent field values that are not present in the input JSON.\n"
						+ "- Return the JSON array and nothing else.";

		Update update =
				new Update()
						.set(KEY, EPIC_HYGIENE_PROMPT)
						.set(CONTEXT, context)
						.set(
								TASK,
								"Score each Jira Epic on every supplied readiness dimension using only the evidence present in that Epic's "
										+ "fields, then derive its overall readiness from those dimension scores and the weight each dimension declares.")
						.set(
								INSTRUCTIONS,
								Arrays.asList(
										"=== Non-Negotiable Principles === Rely STRICTLY on the fields provided in the Epic JSON. NEVER "
												+ "assume, infer, estimate or fabricate a value that is not present. Missing information scores low - it is "
												+ "never rewarded. Differentiate REQUESTS from CONFIRMATIONS - a request for sign-off is NOT approval, and a "
												+ "planned activity is NOT a completed one. Every score MUST cite the exact field name and observed evidence.",
										"=== Independent Dimension Scoring === Every numbered dimension entry is INDEPENDENT, even when several "
												+ "entries declare the same field. Score each entry strictly against its own criteria and ignore the criteria "
												+ "of every other entry. A field carrying two dimensions (e.g. a business clarity check and a scope definition "
												+ "check on 'description') MUST produce TWO separate result elements with two separate scores - one may score "
												+ "high while the other scores low. The results array MUST contain exactly one element per dimension entry, in "
												+ "the order the dimensions were supplied: if 6 dimension entries are given, return 6 result elements.",
										"=== Scoring Bands === Grade every dimension on this evidence ladder, then place the score inside the band: "
												+ "0-25 the evidence is absent or a bare mention only; 26-50 partial evidence, high level or unstructured; "
												+ "51-75 solid evidence covering the main expectations of the criteria; 76-100 comprehensive evidence that also "
												+ "covers edge cases, exclusions or confirmations. Award points ONLY for evidence explicitly present in the "
												+ "declared field. A dimension with no supporting evidence at all scores in the 0-25 band.",
										"=== Dimension Weighting === Every dimension entry carries a numeric weight that determines ONLY how much it "
												+ "contributes to the readiness score - never how it is scored. Judge each dimension purely on its own criteria "
												+ "and the evidence available, then apply its weight when aggregating. Weights are relative to one another, not "
												+ "percentages, and need not sum to any particular total. Dimensions configured without a weight arrive with "
												+ "weight 1 and are scored normally alongside weighted ones.",
										"=== Status-Aware Evaluation === Use the Epic's status as CONTEXT for the recommendations, never as a "
												+ "substitute for evidence. Intake / Discovery: expect business clarity and an initial scope. Functional "
												+ "Grooming: expect scope definition and an emerging solution. Technical Grooming: expect solution and "
												+ "dependency readiness. Construction Ready: every dimension is expected to be strong. Blocked or On Hold: "
												+ "score the dimensions normally, additionally identify the blocker or hold reason from the available evidence "
												+ "and lead the recommendations with the remediation for it.",
										"=== Readiness Score (weight-based) === Consider only APPLICABLE dimensions, i.e. those whose score is not "
												+ "null. totalWeight = SUM of weight across applicable dimensions. earnedWeight = SUM of (weight x score) "
												+ "across applicable dimensions. readinessScore = round(earnedWeight / totalWeight), or 0 when totalWeight is "
												+ "0. readinessGrade = \"GOOD\" when readinessScore >= 76, \"AVERAGE\" when 51 <= readinessScore < 76, "
												+ "\"POOR\" when readinessScore < 51.",
										"=== Overall Status === \"READY\" -> EVERY applicable dimension scores 70 or above. \"NOT READY\" -> any "
												+ "applicable dimension scores below 70. A strong average must never mask one badly under-defined dimension, "
												+ "and weights NEVER affect this decision.",
										"=== Improvement Recommendations === Provide 3 to 5 short, action-oriented suggestions that would raise the "
												+ "readiness score for this Epic, ordered so the heaviest low-scoring dimensions are tackled first. Each "
												+ "suggestion must reference a specific dimension or a specific piece of missing evidence, and must never "
												+ "assume facts not present in the Epic. Return them as ONE string with items joined by \" | \"."))
						.set(INPUT, input)
						.set(OUTPUT_FORMAT, outputFormat)
						.set(PLACEHOLDERS, Arrays.asList(HYGIENE_RULES_PLACEHOLDER, JIRA_ISSUES_PLACEHOLDER));

		mongoTemplate.upsert(
				new Query(Criteria.where(KEY).is(EPIC_HYGIENE_PROMPT)), update, PROMPT_DETAILS_COLLECTION);

		log.info("Successfully inserted {} prompt details", EPIC_HYGIENE_PROMPT);
	}

	/** Deletes the epic hygiene prompt document. */
	private void deleteEpicHygienePromptDetails() {
		log.info("Deleting {} prompt details", EPIC_HYGIENE_PROMPT);

		mongoTemplate.remove(
				new Query(Criteria.where(KEY).is(EPIC_HYGIENE_PROMPT)), PROMPT_DETAILS_COLLECTION);

		log.info("Successfully deleted {} prompt details", EPIC_HYGIENE_PROMPT);
	}
}
