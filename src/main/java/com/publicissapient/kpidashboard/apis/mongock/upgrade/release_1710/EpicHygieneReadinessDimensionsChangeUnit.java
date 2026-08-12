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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bson.Document;
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
 * Pins the Epic Hygiene KPI (kpi312) to FIVE FIXED readiness dimensions.
 *
 * <p>Until now the drill-down grew one dynamic column per configured rule (the "rule set" columns),
 * so two projects downloaded two different sheets and nothing was comparable. The report is now
 * fixed: <b>Business Clarity, Scope Definition, Solution Readiness, Dependency Readiness, Risk
 * Readiness</b> plus the derived <b>Readiness Score</b>. This change unit therefore:
 *
 * <ul>
 *   <li>replaces the {@code epic-hygiene} prompt document inserted by {@link
 *       EpicHygienePromptDetailsChangeUnit} - which is deliberately left untouched - so the LLM
 *       returns exactly those five dimensions, scored with the project's configured field mapping
 *       rules and, for a dimension no rule names, with the common Definition-of-Ready criteria;
 *   <li>replaces the kpi312 Excel column configuration with the fixed layout - which also fixes the
 *       column that was mislabelled "Hygiene Score" instead of "Readiness Score";
 *   <li>re-words the {@code jiraFieldsSelectionKPI312} field mapping: it configures the RULES that
 *       score the dimensions, not the dimensions themselves;
 *   <li>clears the cached Epic verdicts, which were produced by the previous prompt and would
 *       otherwise keep being served from {@code epic_hygiene_results} with the old dimension names.
 * </ul>
 *
 * <p>The prompt exists in the {@code prompt_details} collection only - this migration is the only
 * place that writes it. At runtime it is always read back from the DB through {@code
 * PromptService}, so a prompt tweak never needs a code change.
 */
@Slf4j
@RequiredArgsConstructor
@ChangeUnit(
		id = "epic_hygiene_fixed_readiness_dimensions",
		order = "17183",
		author = "kunkambl",
		systemVersion = "17.1.0")
public class EpicHygieneReadinessDimensionsChangeUnit {

	private static final String PROMPT_DETAILS_COLLECTION = "prompt_details";
	private static final String KPI_COLUMN_CONFIGS_COLLECTION = "kpi_column_configs";
	private static final String EPIC_HYGIENE_RESULTS_COLLECTION = "epic_hygiene_results";
	private static final String FIELD_MAPPING_STRUCTURE_COLLECTION = "field_mapping_structure";

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

	private static final String KPI_ID_FIELD = "kpiId";
	private static final String KPI_ID = "kpi312";
	private static final String KPI_COLUMN_DETAILS = "kpiColumnDetails";

	private static final String FIELD_NAME = "fieldName";
	private static final String JIRA_FIELDS_SELECTION_FIELD = "jiraFieldsSelectionKPI312";
	private static final String FIELD_LABEL = "fieldLabel";
	private static final String PLACEHOLDER_TEXT = "placeHolderText";
	private static final String TOOLTIP = "tooltip";
	private static final String DEFINITION = "definition";

	private static final String COLUMN_NAME = "columnName";
	private static final String ORDER = "order";
	private static final String IS_SHOWN = "isShown";
	private static final String IS_DEFAULT = "isDefault";

	/** The five fixed readiness dimensions, in the order they must be returned. */
	private static final String DIMENSION_LIST =
			"1. Business Clarity, 2. Scope Definition, 3. Solution Readiness, 4. Dependency Readiness, "
					+ "5. Risk Readiness";

	/** The drill-down layout: identity columns, the five dimensions, then the verdict columns. */
	private static final List<String> FIXED_COLUMNS =
			Arrays.asList(
					"Epic ID",
					"Epic Name",
					"Status",
					"Assignee",
					"Business Clarity",
					"Scope Definition",
					"Solution Readiness",
					"Dependency Readiness",
					"Risk Readiness",
					"Readiness Score",
					"Overall Status",
					"Recommendations");

	/** The layout seeded by {@link EpicHygieneSlingshotChangeUnit}, restored on rollback. */
	private static final List<String> PREVIOUS_COLUMNS =
			Arrays.asList(
					"Epic ID",
					"Epic Name",
					"Status",
					"Assignee",
					"Hygiene Score",
					"Overall Status",
					"Recommendations");

	private static final String RULES_LABEL = "Epic readiness rules";
	private static final String PREVIOUS_RULES_LABEL = "Epic readiness dimensions";

	private static final String RULES_TOOLTIP =
			"Rules used to score the five fixed readiness dimensions - Business Clarity, Scope Definition, Solution "
					+ "Readiness, Dependency Readiness and Risk Readiness. Name the entry after the dimension it scores, pick "
					+ "the Jira field carrying the evidence and describe the check in the prompt; several entries may target "
					+ "the same dimension. A dimension left unconfigured is scored with the standard Definition-of-Ready "
					+ "criteria. Prefix the prompt with [weight]: to make a dimension count more heavily than the others.";

	private static final String PREVIOUS_RULES_TOOLTIP =
			"One entry per readiness dimension: pick the Jira field carrying the evidence and describe the 0-100 scoring "
					+ "criteria in the prompt. Prefix the prompt with [weight]: to make a dimension count more heavily than "
					+ "the others.";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		updateEpicHygienePrompt();
		updateColumnConfig(FIXED_COLUMNS);
		updateFieldMappingStructure(RULES_LABEL, RULES_TOOLTIP);
		evictCachedVerdicts();
	}

	@RollbackExecution
	public void rollback() {
		updateColumnConfig(PREVIOUS_COLUMNS);
		updateFieldMappingStructure(PREVIOUS_RULES_LABEL, PREVIOUS_RULES_TOOLTIP);
		evictCachedVerdicts();
	}

	/** Rewrites the epic hygiene prompt document with the fixed five dimension version. */
	private void updateEpicHygienePrompt() {
		log.info("Updating {} prompt details with the fixed readiness dimensions", EPIC_HYGIENE_PROMPT);

		String context =
				"You are an Expert Epic Readiness Analyzer Agent. Your job is to assess how ready each Jira Epic is to "
						+ "enter construction by scoring it on FIVE FIXED readiness dimensions - "
						+ DIMENSION_LIST
						+ " - and to produce a strict, evidence-based verdict per Epic. The five dimensions are FIXED: never "
						+ "add, rename, merge, split, reorder or omit one, because each of them is a column of a report that is "
						+ "compared across projects.";

		String input =
				"=== Readiness Dimensions (FIXED - exactly five, always in this order) ===\n"
						+ "One block per dimension. Each block declares:\n"
						+ "  - dimension      : the dimension name - copy it VERBATIM into results[].dimension\n"
						+ "  - weight         : a positive number setting how much this dimension moves the readiness score\n"
						+ "  - evidenceFields : the Jira Epic field(s) to read the evidence from\n"
						+ "  - criteriaSource : PROJECT FIELD MAPPING (the project configured these checks) or DEFAULT (nothing "
						+ "was configured for this dimension, so apply the DEFAULT CRITERIA for it from your instructions)\n"
						+ "  - criteria       : one line per check, each naming the field it reads and the weight it carries\n"
						+ "The SAME field may feed several dimensions - that is expected. Score every dimension separately "
						+ "against its own criteria only.\n"
						+ HYGIENE_RULES_PLACEHOLDER
						+ "\n\n=== Jira Epics (JSON array) ===\n"
						+ "Each object is ONE Epic, keyed by its real Jira field names. A field that is absent was empty in "
						+ "Jira - treat it as missing evidence, never as a pass.\n"
						+ JIRA_ISSUES_PLACEHOLDER;

		String outputFormat =
				"Return a JSON ARRAY - one element per input Epic, in the same order as the input. No markdown, no prose, "
						+ "no CSV, no code fences, no trailing commentary. Schema per element:\n"
						+ "[\n"
						+ "  {\n"
						+ "    \"epicKey\": \"<epic.number>\",\n"
						+ "    \"epicName\": \"<epic.name, else epic.number>\",\n"
						+ "    \"status\": \"<epic.status>\",\n"
						+ "    \"assignee\": \"<epic.assigneeName or 'Unassigned'>\",\n"
						+ "    \"results\": [\n"
						+ "      { \"dimension\": \"Business Clarity\", \"field\": \"<field read>\", \"weight\": <weight of the "
						+ "block>, \"score\": <0-100 or null>, \"observed\": \"<evidence found, or 'null'>\", \"reason\": "
						+ "\"<one line citing the field and the evidence>\" },\n"
						+ "      { \"dimension\": \"Scope Definition\", \"field\": \"<field read>\", \"weight\": <weight of the "
						+ "block>, \"score\": <0-100 or null>, \"observed\": \"<evidence found, or 'null'>\", \"reason\": "
						+ "\"<one line citing the field and the evidence>\" },\n"
						+ "      { \"dimension\": \"Solution Readiness\", \"field\": \"<field read>\", \"weight\": <weight of "
						+ "the block>, \"score\": <0-100 or null>, \"observed\": \"<evidence found, or 'null'>\", \"reason\": "
						+ "\"<one line citing the field and the evidence>\" },\n"
						+ "      { \"dimension\": \"Dependency Readiness\", \"field\": \"<field read>\", \"weight\": <weight of "
						+ "the block>, \"score\": <0-100 or null>, \"observed\": \"<evidence found, or 'null'>\", \"reason\": "
						+ "\"<one line citing the field and the evidence>\" },\n"
						+ "      { \"dimension\": \"Risk Readiness\", \"field\": \"<field read>\", \"weight\": <weight of the "
						+ "block>, \"score\": <0-100 or null>, \"observed\": \"<evidence found, or 'null'>\", \"reason\": "
						+ "\"<one line citing the field and the evidence>\" }\n"
						+ "    ],\n"
						+ "    \"readinessScore\": <int 0-100, WEIGHT-BASED average of the five dimension scores>,\n"
						+ "    \"readinessGrade\": \"GOOD | AVERAGE | POOR\",\n"
						+ "    \"overallStatus\": \"READY | NOT READY\",\n"
						+ "    \"topGaps\": [\"<up to 3 dimension names scoring below 70, heaviest weight first>\"],\n"
						+ "    \"recommendations\": \"<3-5 fixes joined by ' | '>\"\n"
						+ "  }\n"
						+ "]\n\n"
						+ "Hard Constraints:\n"
						+ "- results MUST contain EXACTLY five elements, one per fixed dimension, in the order shown above.\n"
						+ "- results[].dimension MUST be one of: "
						+ DIMENSION_LIST
						+ " - spelled exactly, never renamed, merged or invented.\n"
						+ "- results[].weight MUST be the weight supplied for that dimension block, copied verbatim; never "
						+ "invent, rescale or renormalise weights.\n"
						+ "- results[].score MUST be an integer between 0 and 100, or null when the dimension genuinely does "
						+ "not apply. Never return a score without evidence: missing evidence scores low, it is not null.\n"
						+ "- Score EVERY dimension for EVERY Epic; never skip an Epic.\n"
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
								"Score each Jira Epic on all five readiness dimensions using ONLY the evidence present in that Epic's own "
										+ "fields and the criteria supplied for each dimension, then derive its overall readiness (the Readiness "
										+ "Score) from those five dimension scores and the weight each dimension declares.")
						.set(
								INSTRUCTIONS,
								Arrays.asList(
										"=== Fixed Dimensions === Grade EVERY Epic on exactly these five dimensions, in this order: "
												+ DIMENSION_LIST
												+ ". results MUST contain exactly five elements and results[].dimension MUST be spelled exactly as listed. "
												+ "A sixth dimension, a renamed dimension, a missing dimension or a re-ordered results array is an invalid "
												+ "answer.",
										"=== Where The Criteria Come From === Each dimension block supplies the criteria to apply. When criteriaSource "
												+ "is PROJECT FIELD MAPPING the block lists one or more configured checks, each with the Jira field to read and "
												+ "its own weight: satisfy ALL of them to score in the top band and cite the configured field in the reason. "
												+ "When criteriaSource is DEFAULT the project configured nothing for that dimension, so apply the DEFAULT "
												+ "CRITERIA below against the evidenceFields listed on the block. Never invent criteria of your own, and never "
												+ "carry the criteria of one dimension over to another.",
										"=== DEFAULT CRITERIA: Business Clarity === Judge whether the Epic explains WHY it exists. Look for: a clearly "
												+ "stated business problem or opportunity; the target users, personas or customer segment; the expected "
												+ "business outcome / value; and measurable success criteria or KPIs. Bands: 0-25 nothing beyond a title or a "
												+ "one-line mention; 26-50 an intent is stated but the users or the outcome are missing; 51-75 problem, users "
												+ "and expected outcome are all stated; 76-100 additionally quantified success metrics, business justification "
												+ "or the cost of not doing it.",
										"=== DEFAULT CRITERIA: Scope Definition === Judge whether the Epic states WHAT will be delivered. Look for: "
												+ "enumerated deliverables, features or capabilities; explicit in-scope AND out-of-scope boundaries; Epic level "
												+ "acceptance criteria or a Definition of Done; and a breakdown into child stories/features. Bands: 0-25 no "
												+ "scope beyond the title; 26-50 a high level list only, no boundaries or acceptance criteria; 51-75 "
												+ "deliverables plus acceptance criteria are documented; 76-100 additionally explicit exclusions, "
												+ "phasing/releases and a visible story breakdown.",
										"=== DEFAULT CRITERIA: Solution Readiness === Judge whether the HOW is understood. Look for: the proposed "
												+ "functional/technical approach; references to design artefacts (HLD, ADR, wireframes, API contracts, data "
												+ "model); data, integration and migration impacts; and non-functional requirements such as performance, "
												+ "security, availability or scalability. Bands: 0-25 no approach documented; 26-50 an idea or a direction "
												+ "without any design detail; 51-75 the approach and the main design decisions are described; 76-100 "
												+ "additionally NFRs, alternatives considered and links to the design artefacts.",
										"=== DEFAULT CRITERIA: Dependency Readiness === Judge whether everything the Epic waits on is identified and "
												+ "under control. Look for: named upstream/downstream teams, systems, vendors or third parties; linked blocking "
												+ "issues; a named owner per dependency; and agreed dates or confirmations. Bands: 0-25 dependencies are "
												+ "neither listed nor explicitly ruled out; 26-50 dependencies are mentioned without owners or dates; 51-75 "
												+ "dependencies are named with owners; 76-100 additionally confirmed dates/commitments and a mitigation for "
												+ "every unresolved dependency. An Epic that explicitly confirms it has NO dependencies scores in the 76-100 "
												+ "band - never null.",
										"=== DEFAULT CRITERIA: Risk Readiness === Judge whether risks, assumptions, constraints and blockers are surfaced "
												+ "WITH a plan. Look for: an explicit risk / assumption / constraint list; impact and likelihood; a named "
												+ "mitigation, contingency or owner; regulatory, security or compliance considerations; and - when the Epic is "
												+ "Blocked or On Hold - the blocker itself plus its remediation. Bands: 0-25 no risks or assumptions captured; "
												+ "26-50 risks listed with no mitigation; 51-75 risks captured with mitigations; 76-100 additionally "
												+ "impact/likelihood, owners and contingency plans. An Epic that explicitly confirms it carries NO material "
												+ "risk scores in the 76-100 band - never null.",
										"=== Non-Negotiable Principles === Rely STRICTLY on the fields provided in the Epic JSON. NEVER assume, infer, "
												+ "estimate or fabricate a value that is not present. Missing information scores low - it is never rewarded. "
												+ "Differentiate REQUESTS from CONFIRMATIONS - a request for sign-off is NOT approval, and a planned activity "
												+ "is NOT a completed one. Every score MUST cite the exact field name and the evidence observed in it.",
										"=== Independent Dimension Scoring === The five dimensions are INDEPENDENT, even though several of them usually "
												+ "read the SAME field (typically 'description'). Score each dimension strictly against its own criteria and "
												+ "ignore the criteria of the other four: a description may explain the business value brilliantly (high "
												+ "Business Clarity) while saying nothing about dependencies (low Dependency Readiness). Returning five "
												+ "identical scores is almost always a mistake - differentiate them.",
										"=== Scoring Bands === Grade every dimension on this evidence ladder, then place the score inside the band: "
												+ "0-25 the evidence is absent or a bare mention only; 26-50 partial evidence, high level or unstructured; "
												+ "51-75 solid evidence covering the main expectations of the criteria; 76-100 comprehensive evidence that "
												+ "also covers edge cases, exclusions or confirmations. Award points ONLY for evidence explicitly present in "
												+ "the Epic. A dimension with no supporting evidence at all scores in the 0-25 band - use null ONLY when the "
												+ "dimension genuinely cannot apply to this Epic, never as a substitute for 'nothing was documented'.",
										"=== Dimension Weighting === Every dimension block carries a numeric weight that determines ONLY how much it "
												+ "contributes to the readiness score - never how it is scored. Judge each dimension purely on its own "
												+ "criteria and the evidence available, then apply its weight when aggregating. Weights are relative to one "
												+ "another, not percentages, and need not sum to any particular total. Copy the weight of the block verbatim "
												+ "into results[].weight.",
										"=== Additional Configured Checks === When the input carries an 'Additional configured checks' section, fold "
												+ "each of those checks into the SINGLE dimension it informs most and mention it in that dimension's reason. "
												+ "They must NEVER become a sixth result element.",
										"=== Status-Aware Evaluation === Use the Epic's status as CONTEXT for the recommendations, never as a substitute "
												+ "for evidence. Intake / Discovery: expect business clarity and an initial scope. Functional Grooming: expect "
												+ "scope definition and an emerging solution. Technical Grooming: expect solution and dependency readiness. "
												+ "Construction Ready: every dimension is expected to be strong. Blocked or On Hold: score the dimensions "
												+ "normally, additionally identify the blocker or hold reason from the available evidence and lead the "
												+ "recommendations with its remediation.",
										"=== Readiness Score (weight-based) === Consider only APPLICABLE dimensions, i.e. those whose score is not "
												+ "null. totalWeight = SUM of weight across applicable dimensions. earnedWeight = SUM of (weight x score) "
												+ "across applicable dimensions. readinessScore = round(earnedWeight / totalWeight), or 0 when totalWeight "
												+ "is 0. readinessGrade = \"GOOD\" when readinessScore >= 76, \"AVERAGE\" when 51 <= readinessScore < 76, "
												+ "\"POOR\" when readinessScore < 51.",
										"=== Overall Status === \"READY\" -> EVERY applicable dimension scores 70 or above. \"NOT READY\" -> any "
												+ "applicable dimension scores below 70. A strong average must never mask one badly under-defined dimension, "
												+ "and weights NEVER affect this decision.",
										"=== Improvement Recommendations === Provide 3 to 5 short, action-oriented suggestions that would raise the "
												+ "readiness score for this Epic, ordered so the heaviest low-scoring dimensions are tackled first. Each "
												+ "suggestion must name the dimension it lifts and the specific missing evidence, and must never assume facts "
												+ "not present in the Epic. Return them as ONE string with items joined by \" | \"."))
						.set(INPUT, input)
						.set(OUTPUT_FORMAT, outputFormat)
						.set(PLACEHOLDERS, Arrays.asList(HYGIENE_RULES_PLACEHOLDER, JIRA_ISSUES_PLACEHOLDER));

		mongoTemplate.upsert(
				new Query(Criteria.where(KEY).is(EPIC_HYGIENE_PROMPT)), update, PROMPT_DETAILS_COLLECTION);

		log.info("Successfully updated {} prompt details", EPIC_HYGIENE_PROMPT);
	}

	/** Writes the given column layout onto every kpi312 column configuration. */
	private void updateColumnConfig(List<String> columnNames) {
		mongoTemplate.updateMulti(
				new Query(Criteria.where(KPI_ID_FIELD).is(KPI_ID)),
				new Update().set(KPI_COLUMN_DETAILS, columns(columnNames)),
				KPI_COLUMN_CONFIGS_COLLECTION);
	}

	/**
	 * Re-words the field mapping so it reads as "rules that score the fixed dimensions" rather than
	 * "the dimensions themselves" - the configured values keep working unchanged.
	 */
	private void updateFieldMappingStructure(String label, String tooltip) {
		mongoTemplate.updateFirst(
				new Query(Criteria.where(FIELD_NAME).is(JIRA_FIELDS_SELECTION_FIELD)),
				new Update()
						.set(FIELD_LABEL, label)
						.set(PLACEHOLDER_TEXT, label)
						.set(TOOLTIP, new Document().append(DEFINITION, tooltip)),
				FIELD_MAPPING_STRUCTURE_COLLECTION);
	}

	/**
	 * Drops the cached verdicts so the next request re-evaluates every Epic against the new prompt.
	 * The cache is keyed by (project, Epic) and rebuilt automatically, so nothing is lost.
	 */
	private void evictCachedVerdicts() {
		mongoTemplate.remove(new Query(), EPIC_HYGIENE_RESULTS_COLLECTION);
	}

	private static List<Document> columns(List<String> columnNames) {
		List<Document> columns = new ArrayList<>();
		for (int index = 0; index < columnNames.size(); index++) {
			columns.add(
					new Document()
							.append(COLUMN_NAME, columnNames.get(index))
							.append(ORDER, index + 1)
							.append(IS_SHOWN, true)
							.append(IS_DEFAULT, true));
		}
		return columns;
	}
}
