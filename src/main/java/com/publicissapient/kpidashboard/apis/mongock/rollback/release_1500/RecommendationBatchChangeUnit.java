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

package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1500;

import com.publicissapient.kpidashboard.common.constant.PromptKeys;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Arrays;

/**
 * Rollback change unit for recommendation batch processing setup
 */
@Slf4j
@RequiredArgsConstructor
@ChangeUnit(id = "r_recommendation_batch_change_unit", order = "015111", author = "shunaray", systemVersion = "15.0.0")
public class RecommendationBatchChangeUnit {

	private static final String PROMPT_DETAILS_COLLECTION = "prompt_details";
	private static final String RECOMMENDATIONS_ACTION_PLAN_COLLECTION = "recommendations_action_plan";
	private static final String BATCH_RECOMMENDATION_PROMPT = PromptKeys.BATCH_RECOMMENDATION_PROMPT;
	private static final String OUTPUT_FORMAT = "outputFormat";
	private static final String CONTEXT = "context";
	private static final String TASK = "task";
	private static final String INSTRUCTIONS = "instructions";
	private static final String INPUT = "input";
	private static final String PLACEHOLDERS = "placeHolders";

	private final MongoTemplate mongoTemplate;

	@Execution
	public void execution() {
        deleteBatchRecommendationPromptDetails();
        rollbackRecommendationsActionPlanIndexes();

	}

	@RollbackExecution
	public void rollback() {
        insertBatchRecommendationPromptDetails();
        createRecommendationsActionPlanIndexes();

    }

	/**
	 * Inserts complete batch-recommendation prompt details document
	 */
	private void insertBatchRecommendationPromptDetails() {
		log.info("Inserting batch-recommendation prompt details");

		String outputFormat = "Provide a comprehensive recommendation with:\n"
				+ "1. Calculate project health score as a percentage based on benchmark comparison\n"
				+ "2. Analyze KPI deviations, interdependencies using correlation matrix, and prioritize improvements\n"
				+ "3. Format as parsable JSON matching this structure exactly:\n\n" + "{\n"
				+ "  \"title\": \"Project Health Analysis\",\n"
				+ "  \"description\": \"Comprehensive analysis with project health score of [X%]. Key findings: [summarize top 3 deviations and their business impact in 2-3 sentences]. Translate technical metrics into business-friendly language.\",\n"
				+ "  \"severity\": \"[CRITICAL|HIGH|MEDIUM|LOW - use highest severity from all action plans]\",\n"
				+ "  \"timeToValue\": \"[Estimated time for overall improvement: e.g., '2-4 weeks', '1-2 months']\",\n"
				+ "  \"actionPlans\": [\n" + "    {\n" + "      \"title\": \"Improve [KPI_NAME] - Current: [X%]\",\n"
				+ "      \"description\": \"**Observation:** [Current state, deviation from benchmark, trend analysis]. **Correlated KPIs:** [List as 'kpi_name: correlation%' - only include correlations > 50%]. **Recommendation:** [Specific, actionable steps tailored for Persona_PLACEHOLDER role]. **Expected Impact:** [Explain improvement effects on correlated KPIs].\"\n"
				+ "    }\n" + "  ]\n" + "}\n\n" + "**Requirements:**\n"
				+ "- Order actionPlans by severity (CRITICAL → HIGH → MEDIUM → LOW)\n"
				+ "- Each actionPlan should focus on ONE main KPI\n"
				+ "- Include top 3-5 actionPlans (most impactful)\n"
				+ "- Use severity based on deviation: >30% = CRITICAL, 20-30% = HIGH, 10-20% = MEDIUM, <10% = LOW\n"
				+ "- timeToValue based on severity and complexity\n" + "- No inline comments, only valid JSON";

		Update update = new Update().set("key", BATCH_RECOMMENDATION_PROMPT).set(CONTEXT,
				"You are an AI data scientist, your task is to analyze the KPI data for a project using the steps provided below:")
				.set(TASK, "Analyze the KPI data and generate a comprehensive report.")
				.set(INSTRUCTIONS, Arrays.asList(
						"1. Extract the correlation coefficients and weights for the benchmark matrix from analysis report generated through large kpi dataset: KPI_CORRELATION_REPORT_PLACEHOLDER.",
						"2. Compute the pairwise correlation coefficients and weights for the project data",
						"3. Identify significant relationships in the benchmark:-correlation whose value are greater then 0.5 for positive and less than -0.5 for negative",
						"4. Calculate the pairwise scores based on the benchmark comparison using the formula: text(pair_score)_(i,j) = (1 - Delta r) cdot w_(benchmark) - (0.5 cdot Delta r text(if) Delta r > 0.)',Normalize between 0 and 1.",
						"5. Aggregate all pairwise scores and normalize across 676 pairs.",
						"6. Scale the normalized score to a percentage to get the project health.",
						"7. Identify the KPIs that are most out of alignment.",
						"8. Analyze **interdependencies** between KPIs using the benchmark correlation matrix - If improving one KPI is likely to improve others suggest it in recommendation,  Explain **which KPI(s)** should be prioritized to help multiple others improve indirectly.",
						"9. Severity of any recommendation should be according the deviation, if deviation is more keep it high"))
				.set(INPUT,
						"Based on the KPIs – sprint_velocity, sprint_capacity_utilization, test_execution_pass_percentage, build_frequency, unit_test_coverage, code_build_time, tech_debt_sonar_maintainability, lead_time_for_change, defect_injection_rate, defect_rejection_rate, defect_removal_efficiency, defect_density, in_sprint_automation_coverage, regression_automation_coverage, sprint_predictability, first_time_pass_rate, happiness_index_rate, value_delivery, sonar_code_quality, release_frequency, issue_count, scope_churn, created_vs_resolved, defect_seepage_rate, commitment_reliability, code_violations – analyze the provided dataset KPI_DATA_BY_PROJECT_PLACEHOLDER, which contains kpi data of latest five sprints marked by date,analyze the performance of each kpi and generate a score for the project performance.")
				.set(OUTPUT_FORMAT, outputFormat).set(PLACEHOLDERS, Arrays.asList("KPI_CORRELATION_REPORT_PLACEHOLDER",
						"KPI_DATA_BY_PROJECT_PLACEHOLDER", "Persona_PLACEHOLDER"));

		mongoTemplate.upsert(new Query(Criteria.where("key").is(BATCH_RECOMMENDATION_PROMPT)), update,
				PROMPT_DETAILS_COLLECTION);

		log.info("Successfully inserted batch-recommendation prompt details");
	}

	/**
	 * Deletes batch-recommendation prompt details document
	 */
	private void deleteBatchRecommendationPromptDetails() {
		log.info("Deleting batch-recommendation prompt details");

		mongoTemplate.remove(new Query(Criteria.where("key").is(BATCH_RECOMMENDATION_PROMPT)),
				PROMPT_DETAILS_COLLECTION);

		log.info("Successfully deleted batch-recommendation prompt details");
	}

	/**
	 * Creates indexes on recommendations_action_plan collection for efficient
	 * querying
	 */
	private void createRecommendationsActionPlanIndexes() {
		log.info("Creating indexes on recommendations_action_plan collection");

		IndexOperations indexOps = mongoTemplate.indexOps(RECOMMENDATIONS_ACTION_PLAN_COLLECTION);

		// Compound index: basicProjectConfigId (ASC) + createdAt (DESC)
		indexOps.ensureIndex(new Index().on("basicProjectConfigId", Sort.Direction.ASC)
				.on("createdAt", Sort.Direction.DESC).named("basicProjectConfigId_1_createdAt_-1"));

		log.info("Successfully created indexes on recommendations_action_plan collection");
	}

	/**
	 * Rollback indexes on recommendations_action_plan collection
	 */
	private void rollbackRecommendationsActionPlanIndexes() {
		log.info("Dropping indexes on recommendations_action_plan collection");

		IndexOperations indexOps = mongoTemplate.indexOps(RECOMMENDATIONS_ACTION_PLAN_COLLECTION);

		// Drop created index
		indexOps.dropIndex("basicProjectConfigId_1_createdAt_-1");

		log.info("Successfully dropped indexes on recommendations_action_plan collection");
	}
}
