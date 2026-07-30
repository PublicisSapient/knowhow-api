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
 * Inserts the {@code project-hygiene} prompt into the {@code prompt_details} collection so that the
 * Story Hygiene KPI (kpi311) reads its prompt template from the DB instead of a hardcoded constant.
 *
 * <p>The document follows the {@code PromptDetails} schema - {@code key}, {@code context}, {@code
 * task}, {@code instructions}, {@code input}, {@code outputFormat} and {@code placeHolders}. The
 * two placeholders are resolved at runtime by {@code PromptService#getProjectHygienePrompt}.
 *
 * <p>The prompt treats every supplied rule entry as an INDEPENDENT rule, so a single Jira field
 * (e.g. {@code description}) may carry several rule sets - an acceptance-criteria check and a
 * BDD-definition check - and each one produces its own verdict in the response.
 *
 * <p>Each rule entry also carries a numeric {@code weight} driving how much it contributes to the
 * hygiene score. Weights come from the {@code [weight]:} prefix on the configured prompt ({@code
 * [10]: ...}, or {@code [null]: ...} for the default weight of 1) and are stripped by {@code
 * HygienePromptBuilder} before the rules reach the LLM.
 */
@Slf4j
@RequiredArgsConstructor
@ChangeUnit(
		id = "project_hygiene_prompt_details",
		order = "17174",
		author = "knowhow",
		systemVersion = "17.1.0")
public class ProjectHygienePromptDetailsChangeUnit {

	private static final String PROMPT_DETAILS_COLLECTION = "prompt_details";
	private static final String PROJECT_HYGIENE_PROMPT = PromptKeys.PROJECT_HYGIENE_PROMPT;

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
		insertProjectHygienePromptDetails();
	}

	@RollbackExecution
	public void rollback() {
		deleteProjectHygienePromptDetails();
	}

	/** Inserts / upserts the complete project hygiene prompt document. */
	private void insertProjectHygienePromptDetails() {
		log.info("Inserting {} prompt details", PROJECT_HYGIENE_PROMPT);

		String context =
				"You are an Expert Project Hygiene Analyzer Agent. Your job is to evaluate Jira issues against a "
						+ "Definition-of-Ready (DoR) style hygiene checklist and produce a strict, evidence-based verdict "
						+ "for each issue.";

		String input =
				"=== Hygiene Rules ===\n"
						+ "A numbered list of INDEPENDENT rule entries. Each entry declares:\n"
						+ "  - ruleName: the unique identifier of the rule - copy it VERBATIM into results[].rule\n"
						+ "  - field   : the Jira issue field this rule inspects\n"
						+ "  - weight  : a positive number setting how much this rule contributes to the hygiene score\n"
						+ "  - criteria: the condition this rule evaluates\n"
						+ "Several entries MAY declare the SAME field (for example one 'acceptance criteria' rule and one "
						+ "'BDD definition' rule, both written against 'description'). Treat every entry as a completely "
						+ "separate rule: evaluate it using ONLY its own criteria and emit its own result element. Never "
						+ "merge, deduplicate or skip rules just because they share a field.\n"
						+ "Weights are RELATIVE, not percentages, and need not add up to any particular total: a rule of "
						+ "weight 10 moves the score ten times as much as a rule of weight 1. Rules that were configured "
						+ "without a weight arrive with weight 1.\n"
						+ HYGIENE_RULES_PLACEHOLDER
						+ "\n\n=== Jira Issues (JSON array) ===\n"
						+ JIRA_ISSUES_PLACEHOLDER;

		String outputFormat =
				"Return a JSON ARRAY - one element per input Jira issue, in the same order as the input. No markdown, "
						+ "no prose, no code fences, no trailing commentary. Schema per element:\n"
						+ "[\n"
						+ "  {\n"
						+ "    \"issueKey\": \"<jiraIssue.number>\",\n"
						+ "    \"issueType\": \"<jiraIssue.typeName>\",\n"
						+ "    \"sprintId\": \"<jiraIssue.sprintID>\",\n"
						+ "    \"assignee\": \"<jiraIssue.assigneeName or 'Unassigned'>\",\n"
						+ "    \"results\": [\n"
						+ "      {\n"
						+ "        \"rule\": \"<ruleName copied VERBATIM from the rule entry>\",\n"
						+ "        \"field\": \"<the field declared on that rule entry>\",\n"
						+ "        \"weight\": <the weight declared on that rule entry, copied verbatim>,\n"
						+ "        \"observed\": \"<actual field value or 'null'>\",\n"
						+ "        \"status\": \"Passed | Failed | Partial | N/A\",\n"
						+ "        \"reason\": \"<one-line justification citing the observed value>\"\n"
						+ "      }\n"
						+ "    ],\n"
						+ "    \"totalApplicableRules\": <int>,\n"
						+ "    \"passedRules\": <int>,\n"
						+ "    \"failedRules\": <int>,\n"
						+ "    \"partialRules\": <int>,\n"
						+ "    \"hygieneScore\": <int 0-100, WEIGHT-BASED>,\n"
						+ "    \"hygieneGrade\": \"GOOD | AVERAGE | POOR\",\n"
						+ "    \"overallStatus\": \"READY | NOT READY\",\n"
						+ "    \"topFailures\": [\"<up to 3 ruleNames of non-Passed rules, heaviest weight first>\"],\n"
						+ "    \"recommendations\": \"<3-5 fixes joined by ' | '>\"\n"
						+ "  }\n"
						+ "]\n\n"
						+ "Hard Constraints:\n"
						+ "- Evaluate EVERY rule entry for EVERY issue; never skip a rule and never skip an issue.\n"
						+ "- results MUST contain EXACTLY one element per supplied rule entry - same count, same order - "
						+ "including when several rule entries share the same field.\n"
						+ "- results[].rule MUST be the ruleName copied verbatim; never rename, merge or invent rules.\n"
						+ "- results[].weight MUST be the weight supplied for that rule entry, copied verbatim; never "
						+ "invent, rescale or renormalise weights.\n"
						+ "- hygieneScore MUST be derived from the weights as described; never fall back to a plain "
						+ "count of passed rules.\n"
						+ "- status MUST be exactly one of \"Passed\", \"Failed\", \"Partial\", \"N/A\" (case sensitive, "
						+ "spelled exactly).\n"
						+ "- overallStatus MUST be exactly \"READY\" or \"NOT READY\".\n"
						+ "- reason MUST cite the exact field name and value observed.\n"
						+ "- Never invent field values that are not present in the input JSON.\n"
						+ "- Return the JSON array and nothing else.";

		Update update =
				new Update()
						.set(KEY, PROJECT_HYGIENE_PROMPT)
						.set(CONTEXT, context)
						.set(
								TASK,
								"Evaluate each Jira issue against every hygiene rule and produce a strict, evidence-based verdict per issue, "
										+ "scoring it according to the weight each rule declares.")
						.set(
								INSTRUCTIONS,
								Arrays.asList(
										"=== Non-Negotiable Principles === Rely STRICTLY on the fields provided in the Jira Issue "
												+ "JSON. NEVER assume, infer, or fabricate a value that is not present. If evidence is "
												+ "missing, mark the rule as \"Failed\" (never \"Passed\"). Differentiate REQUESTS from "
												+ "CONFIRMATIONS - a request for sign-off is NOT approval. Every verdict MUST cite the exact "
												+ "field name and observed value.",
										"=== Independent Rule Evaluation === Every numbered rule entry is INDEPENDENT, even when several "
												+ "entries declare the same field. Evaluate each entry strictly against its own criteria and "
												+ "ignore the criteria of every other entry. A field carrying two rule sets (e.g. an acceptance "
												+ "criteria check and a BDD definition check on 'description') MUST produce TWO separate result "
												+ "elements with two separate verdicts - one may pass while the other fails. The results array "
												+ "MUST contain exactly one element per rule entry, in the order the rules were supplied: if 7 "
												+ "rule entries are given, return 7 result elements.",
										"=== Rule Weighting === Every rule entry carries a numeric weight that determines ONLY how much it "
												+ "contributes to the hygiene score - never whether it passes. Judge each rule purely on its own "
												+ "criteria and the evidence available, then apply its weight when scoring. Weights are relative "
												+ "to one another, not percentages, and need not sum to any particular total. Rules configured "
												+ "without a weight arrive with weight 1 and are scored normally alongside weighted ones.",
										"=== Per-Rule Verdict Vocabulary === \"Passed\" -> rule is fully satisfied by explicit evidence "
												+ "in the listed field. \"Failed\" -> rule is not met OR required evidence is missing. "
												+ "\"Partial\" -> rule is partially met - present but incomplete / unclear / unconfirmed. "
												+ "\"N/A\" -> rule does not apply to this issue type/status per its own criteria.",
										"=== Overall Status Rules === \"READY\" -> every applicable rule entry (i.e. excluding \"N/A\") "
												+ "has status \"Passed\". \"NOT READY\" -> any applicable rule entry is \"Failed\" or \"Partial\". "
												+ "Weights NEVER affect this: a failing low-weight rule still makes the issue NOT READY.",
										"=== Hygiene Score (weight-based) === Consider only APPLICABLE rule entries, i.e. those whose status "
												+ "is not \"N/A\" (rule entries sharing a field count separately). totalWeight = SUM of weight "
												+ "across applicable rule entries. earnedWeight = SUM of weight across applicable rule entries "
												+ "whose status is \"Passed\" - \"Partial\" and \"Failed\" earn 0. hygieneScore = round(earnedWeight "
												+ "* 100 / totalWeight), or 100 when totalWeight is 0. The count fields stay plain UNWEIGHTED "
												+ "counts: totalApplicableRules = number of applicable rule entries; passedRules / failedRules / "
												+ "partialRules = number of entries with that status. hygieneGrade = \"GOOD\" when hygieneScore "
												+ ">= 80, \"AVERAGE\" when 50 <= hygieneScore < 80, \"POOR\" when hygieneScore < 50.",
										"=== Improvement Recommendations === Provide 3 to 5 short, actionable suggestions that would raise "
												+ "the hygiene score for this issue, ordered so the heaviest failing rules are tackled first. "
												+ "Each suggestion must reference a specific rule or missing evidence. Return them as ONE string "
												+ "with items joined by \" | \"."))
						.set(INPUT, input)
						.set(OUTPUT_FORMAT, outputFormat)
						.set(PLACEHOLDERS, Arrays.asList(HYGIENE_RULES_PLACEHOLDER, JIRA_ISSUES_PLACEHOLDER));

		mongoTemplate.upsert(
				new Query(Criteria.where(KEY).is(PROJECT_HYGIENE_PROMPT)),
				update,
				PROMPT_DETAILS_COLLECTION);

		log.info("Successfully inserted {} prompt details", PROJECT_HYGIENE_PROMPT);
	}

	/** Deletes the project hygiene prompt document. */
	private void deleteProjectHygienePromptDetails() {
		log.info("Deleting {} prompt details", PROJECT_HYGIENE_PROMPT);

		mongoTemplate.remove(
				new Query(Criteria.where(KEY).is(PROJECT_HYGIENE_PROMPT)), PROMPT_DETAILS_COLLECTION);

		log.info("Successfully deleted {} prompt details", PROJECT_HYGIENE_PROMPT);
	}
}
