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

package com.publicissapient.kpidashboard.apis.mongock.upgrade.release_1510;

import java.util.Arrays;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.publicissapient.kpidashboard.common.constant.PromptKeys;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@ChangeUnit(
		id = "kpi_level_recommendation_change_unit",
		order = "15110",
		author = "shunaray",
		systemVersion = "15.1.0")
public class KpiLevelRecommendationChangeUnit {

	private static final String PROMPT_DETAILS_COLLECTION = "prompt_details";
	private static final String RECOMMENDATIONS_COLLECTION = "recommendations_action_plan";
	private static final String BATCH_PROJECT_LEVEL_RECOMMENDATION_PROMPT =
			PromptKeys.BATCH_PROJECT_LEVEL_RECOMMENDATION_PROMPT;
	private static final String KPI_LEVEL_RECOMMENDATION_PROMPT = PromptKeys.BATCH_KPI_LEVEL_RECOMMENDATION_PROMPT;
	private static final String OLD_BATCH_RECOMMENDATION_KEY = "batch-recommendation";
	private static final String INDEX_NAME = "basicProjectConfigId_1_level_1_createdAt_-1";
	private static final String CONTEXT = "context";
	private static final String TASK = "task";
	private static final String INSTRUCTIONS = "instructions";
	private static final String INPUT = "input";
	private static final String OUTPUT_FORMAT = "outputFormat";
	private static final String PLACEHOLDERS = "placeHolders";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
		renameBatchRecommendationToProjectLevel();
		insertKpiLevelRecommendationPromptDetails();
		createCompoundIndex();
	}

	@RollbackExecution
	public void rollback() {
		dropCompoundIndex();
		renameProjectLevelRecommendationToBatch();
		deleteKpiLevelRecommendationPromptDetails();
	}

	/**
	 * Renames existing batch-recommendation key to batch-project-level-recommendation
	 * for clarity in distinguishing project-level vs KPI-level analysis
	 */
	private void renameBatchRecommendationToProjectLevel() {
		log.info("Renaming batch-recommendation prompt to batch-project-level-recommendation");

		Update update = new Update().set("key", BATCH_PROJECT_LEVEL_RECOMMENDATION_PROMPT);

		mongoTemplate.updateFirst(
				new Query(Criteria.where("key").is(OLD_BATCH_RECOMMENDATION_KEY)),
				update,
				PROMPT_DETAILS_COLLECTION);

		log.info("Successfully renamed batch-recommendation to batch-project-level-recommendation");
	}

	/**
	 * Inserts complete KPI-level recommendation prompt details document.
	 */
	private void insertKpiLevelRecommendationPromptDetails() {
		log.info("Inserting kpi-level-recommendation prompt details");

		String outputFormat =
				"Provide a comprehensive KPI-level recommendation formatted as parsable JSON exactly as below:\n\n"
						+ "{\n"
						+ "  \"title\": \"KPI Health Analysis - [kpiName]\",\n"
						+ "  \"description\": \"Health score of [X%] for [kpiName]. Summarize how this KPI is trending, why it matters in business terms, and the impact of its current state.\",\n"
						+ "  \"severity\": \"[CRITICAL|HIGH|MEDIUM|LOW]\",\n"
						+ "  \"timeToValue\": \"[Estimated improvement timeline]\",\n"
						+ "  \"actionPlans\": [\n"
						+ "    {\n"
						+ "      \"title\": \"Improve [kpiName] - Current: [value or %]\",\n"
						+ "      \"description\": \"**Observation:** [Deviation from benchmark and recent sprint trend]. **Correlated KPIs:** [List benchmark-based correlations >50%]. **Recommendation:** [Specific steps for Persona_PLACEHOLDER]. **Expected Impact:** [Explain cascading effects on correlated KPIs].\"\n"
						+ "    }\n"
						+ "  ]\n"
						+ "}\n\n"
						+ "**Requirements:**\n"
						+ "- Focus ONLY on the KPI provided in KPI_DATA_PLACEHOLDER\n"
						+ "- Use benchmark correlations for interdependency analysis\n"
						+ "- Include 1–3 action plans maximum\n"
						+ "- Translate technical metrics into business impact\n"
						+ "- No inline comments, only valid JSON";

		Update update =
				new Update()
						.set("key", KPI_LEVEL_RECOMMENDATION_PROMPT)
						.set(
								CONTEXT,
								"You are an AI data scientist. Your task is to analyze the health of a SINGLE KPI using its recent sprint data and a benchmark KPI correlation matrix to generate actionable KPI-level recommendations.")
						.set(
								INPUT,
								"Analyze the KPI provided in KPI_DATA_PLACEHOLDER (includes kpiId, kpiName, and latest five sprint values). Use KPI_CORRELATION_REPORT_PLACEHOLDER as the benchmark reference for expected interdependencies and weights.")
						.set(
								INSTRUCTIONS,
								Arrays.asList(
										"1. Extract kpiId and kpiName from kpi data to identify the target KPI.",
										"2. From kpi correlation report, extract benchmark correlation coefficients and weights ONLY for pairs involving the identified KPI.",
										"3. Analyze the trend, variability, and deviation of the KPI values across the last five sprints.",
										"4. Identify significant benchmark relationships where correlation > 0.5 (positive) or < -0.5 (negative).",
										"5. Compute a KPI alignment score by comparing observed KPI behavior against benchmark-expected behavior, weighted by benchmark correlation strengths. Normalize the score between 0 and 1.",
										"6. Scale the normalized score to a percentage to derive the KPI health score.",
										"7. Identify downstream KPIs most likely to be impacted due to misalignment of this KPI based on benchmark correlations.",
										"8. Analyze interdependencies: explain how improving this KPI is expected to influence correlated KPIs and delivery outcomes.",
										"9. Determine severity based on deviation of this KPI from benchmark expectations (>30% CRITICAL, 20–30% HIGH, 10–20% MEDIUM, <10% LOW). Severity must guide recommendation intensity."))
						.set(
								OUTPUT_FORMAT,
								outputFormat)
						.set(
								TASK,
								"Generate KPI-level health analysis and recommendations.")
						.set(
								PLACEHOLDERS,
								Arrays.asList(
										"KPI_DATA_PLACEHOLDER",
										"KPI_CORRELATION_REPORT_PLACEHOLDER",
										"Persona_PLACEHOLDER"));

		mongoTemplate.upsert(
				new Query(Criteria.where("key").is(KPI_LEVEL_RECOMMENDATION_PROMPT)),
				update,
				PROMPT_DETAILS_COLLECTION);

		log.info("Successfully inserted kpi-level-recommendation prompt details");
	}

	/**
	 * Renames batch-project-level-recommendation key back to batch-recommendation for
	 * rollback
	 */
	private void renameProjectLevelRecommendationToBatch() {
		log.info("Rolling back batch-project-level-recommendation to batch-recommendation");

		Update update = new Update().set("key", OLD_BATCH_RECOMMENDATION_KEY);

		mongoTemplate.updateFirst(
				new Query(Criteria.where("key").is(BATCH_PROJECT_LEVEL_RECOMMENDATION_PROMPT)),
				update,
				PROMPT_DETAILS_COLLECTION);

		log.info("Successfully rolled back batch-project-level-recommendation to batch-recommendation");
	}

	/** Deletes KPI-level recommendation prompt details document */
	private void deleteKpiLevelRecommendationPromptDetails() {
		log.info("Deleting kpi-level-recommendation prompt details");

		mongoTemplate.remove(
				new Query(Criteria.where("key").is(KPI_LEVEL_RECOMMENDATION_PROMPT)),
				PROMPT_DETAILS_COLLECTION);

		log.info("Successfully deleted kpi-level-recommendation prompt details");
	}

	/**
	 * Creates compound index for efficient project + level filtering queries.
	 * Index fields in order:
	 * 1. basicProjectConfigId (ASC) - equality match, highest selectivity
	 * 2. level (ASC) - optional equality filter
	 * 3. createdAt (DESC) - sort field for retrieving latest recommendations
	 *
     * */
	private void createCompoundIndex() {
		log.info("Creating compound index on {} collection: {}", RECOMMENDATIONS_COLLECTION, INDEX_NAME);

		Index index = new Index()
				.on("basicProjectConfigId", Sort.Direction.ASC)
				.on("level", Sort.Direction.ASC)
				.on("createdAt", Sort.Direction.DESC)
				.named(INDEX_NAME);

		mongoTemplate.indexOps(RECOMMENDATIONS_COLLECTION).ensureIndex(index);

		log.info("Successfully created compound index: {}", INDEX_NAME);
	}

	/**
	 * Drops the compound index during rollback.
	 */
	private void dropCompoundIndex() {
		log.info("Dropping compound index: {} from collection: {}", INDEX_NAME, RECOMMENDATIONS_COLLECTION);

		try {
			mongoTemplate.indexOps(RECOMMENDATIONS_COLLECTION).dropIndex(INDEX_NAME);
			log.info("Successfully dropped compound index: {}", INDEX_NAME);
		} catch (Exception e) {
			log.warn("Index {} may not exist, skipping drop: {}", INDEX_NAME, e.getMessage());
		}
	}
}
