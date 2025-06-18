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

package com.publicissapient.kpidashboard.apis.mongock.installation;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.MongoCollection;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

import java.util.Arrays;
import java.util.List;

import static com.publicissapient.kpidashboard.apis.ai.constants.PromptKeys.KPI_CORRELATION_ANALYSIS_REPORT;
import static com.publicissapient.kpidashboard.apis.ai.constants.PromptKeys.KPI_RECOMMENDATION_PROMPT;
import static com.publicissapient.kpidashboard.apis.ai.constants.PromptKeys.SPRINT_GOALS_SUMMARY;

@ChangeUnit(id = "ddl10", order = "0010", author = "shunaray")
public class PromptDetailsChangeLog {

	private static final String PROMPT_DETAILS_COLLECTION = "prompt_details";
	private final MongoTemplate mongoTemplate;
	public static final String CONTEXT = "context";
	public static final String INSTRUCTIONS = "instructions";

	public PromptDetailsChangeLog(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
		MongoCollection<Document> collection = mongoTemplate.getCollection(PROMPT_DETAILS_COLLECTION);

		Document sprintGoalsPrompt = new Document().append("key", SPRINT_GOALS_SUMMARY)
				.append(CONTEXT, "You are a helpful assistant that summarizes sprint goals for executive stakeholders.")
				.append("task",
						"Rewrite the following sprint goals into a concise and professional summary tailored for executive stakeholders.")
				.append(INSTRUCTIONS,
						Arrays.asList("Present the output as a bulleted list.", "Use clear, outcome-focused language.",
								"Limit the total response to 50 words.",
								"Emphasize business value or strategic alignment where possible."))
				.append("input", "Sprint goals: SPRINT_GOALS_PLACEHOLDER")
				.append("outputFormat", "A concise and professional bulleted list.")
				.append("placeHolders", List.of("SPRINT_GOALS_PLACEHOLDER"));

		Document kpiCorrelationAnalysis = new Document().append("key", KPI_CORRELATION_ANALYSIS_REPORT).append(CONTEXT,
				"Below is the “analysis‐report” that summarizes the comprehensive pair–by–pair correlation analysis among the seven key‐performance indicator (KPI) columns on our multi–sprint project dataset. In this example we assume that all KPI values were measured using a Pearson–based procedure and that for each computed correlation a “weight” (an accuracy or reliability measure on a scale from 0 to 1, with values nearer 1 indicating very high confidence given the large number of sprints in the dataset) has been derived. (In our analysis no KPI was excluded and all 7×7 combinations – including the “self–correlations” – are shown.)\n")
				.append("task",
						"Analyze and interpret the correlation matrix of KPIs to identify strong relationships and potential areas of concern in project performance.")
				.append(INSTRUCTIONS, Arrays.asList(
						"For reference, the seven KPIs are:\n1. Sprint Velocity (sprint_velocity_kpi_value)\n2. Commitment Reliability (commitment_reliability_kpi_value)\n3. Sprint Capacity Utilization (sprint_capacity_utilization_kpi_value)\n4. Defect Injection Rate (defect_injection_rate_kpi_value)\n5. Scope Churn/Issue Count (scope_churn_issue_count_kpi_value)\n6. Lead Time for Change (lead_time_for_change_kpi_value)\n7. Code Build Time (code_build_time_kpi_value)\n",
						"Note that the “weight” is our internal accuracy metric – obtained via a robust boot–strapping/confidence–interval procedure – and it tells us how “trustworthy” the estimated correlation is. In what follows every unique pair is shown (remember that A vs. B is the same as B vs. A, but we list each cell of the 7×7 matrix for completeness). Should you ever have a smaller dataset (say, only five sprints for one project), the same reporting format may be applied to “diagnose” overall project health by comparing the observed pairwise correlations and their reliability weights with these benchmark numbers.",
						"THE CORRELATION MATRIX (Each cell shows: r (coefficient) | weight)",
						"For our (illustrative) analysis, the computed Pearson correlation coefficients and their corresponding weights are as follows:",
						"                 |    SV    |    CR    |   SCU    |    DIR   |   SCIC   |   LTC    |   CBT",
						"SV (Sprint Velocity)           |  1.00 |  0.42 (0.92) |  0.55 (0.94) | –0.25 (0.88) |  0.38 (0.91) | –0.10 (0.85) |  0.22 (0.87)",
						"CR (Commitment Reliability)    |  0.42 (0.92) |  1.00 |  0.33 (0.90) | –0.30 (0.89) |  0.45 (0.93) |  0.05 (0.84) |  0.15 (0.86)",
						"SCU (Sprint Capacity Util.)    |  0.55 (0.94) |  0.33 (0.90) |  1.00 |  0.12 (0.85) |  0.28 (0.90) | –0.05 (0.83) |  0.10 (0.84)",
						"DIR (Defect Injection Rate)    | –0.25 (0.88) | –0.30 (0.89) |  0.12 (0.85) |  1.00 | –0.40 (0.92) |  0.60 (0.95) | –0.35 (0.91)",
						"SCIC (Scope Churn/Issues)      |  0.38 (0.91) |  0.45 (0.93) |  0.28 (0.90) | –0.40 (0.92) |  1.00 |  0.10 (0.87) |  0.05 (0.85)",
						"LTC (Lead Time for Change)     | –0.10 (0.85) |  0.05 (0.84) | –0.05 (0.83) |  0.60 (0.95) |  0.10 (0.87) |  1.00 | –0.20 (0.88)",
						"CBT (Code Build Time)          |  0.22 (0.87) |  0.15 (0.86) |  0.10 (0.84) | –0.35 (0.91) |  0.05 (0.85) | –0.20 (0.88) |    1.00"));

		Document kpiRecommendationPrompt = new Document().append("key", KPI_RECOMMENDATION_PROMPT).append(CONTEXT,
				"You are an AI data scientist, your task is to analyze the KPI data for a project using the steps provided below:")
				.append("task", "Analyze the KPI data and generate a comprehensive report.")
				.append(INSTRUCTIONS, Arrays.asList(
						"1. Extract the correlation coefficients and weights for the benchmark matrix from analysis report generated through large kpi dataset: ANALYSIS_REPORT_PLACEHOLDER.",
						"2. Compute the pairwise correlation coefficients and weights for the project data",
						"3. Calculate the pairwise scores based on the benchmark comparison using the formula: text(pair_score)_(i,j) = (1 - Delta r) cdot w_(benchmark) - (0.2 cdot Delta r text(if) Delta r > 0.2)' Normalize between 0 and 1.",
						"4. Aggregate all pairwise scores and normalize across 49 pairs.",
						"5. Scale the normalized score to a percentage to get the project health.",
						"Analyze the provided dataset KPI_DATA_BY_PROJECT_PLACEHOLDER, which contains KPI data of the latest five sprints marked by date, analyze the performance of each KPI and generate a score for the project performance."))
				.append("input",
						"Based on the KPIs – sprint_velocity_kpi_value, commitment_reliability_kpi_value, sprint_capacity_utilization_kpi_value, defect_injection_rate_kpi_value, scope_churn_issue_count_kpi_value, lead_time_for_change_kpi_value, code_build_time_kpi_value – analyze the provided dataset KPI_DATA_BY_PROJECT_PLACEHOLDER, which contains kpi data of latest five sprints marked by date,analyze the performance of each kpi and generate a score for the project performance.")
				.append("outputFormat",
						"Provide a comprehensive report with:\n  1. The project health score as a percentage\n  2. Observations on significant deviations between the project and benchmark values\n  3. Provide any recommendations to address observed discrepancies and how can the project health be improved,\n  do not mention the size of dataset in this section\n\n  Format the comprehensive report in parsable json format,\n  the recommendations should be actionable and specific to the KPIs analyzed and specifically tailored\n  for USER_ROLE_PLACEHOLDER role\n  the key\n  for project health should be titled 'project_health_value'\n  and recommendation key should be 'project_recommendations',\n  in the project_recommendations key,\n  provide a list of recommendations with each recommendation should have key recommendation and value as a string and another key severity and value as a string,\n  the severity can be one of the following: low,\n  medium,\n  high,\n  critical {\n    \"project_health_value\": 0,\n    \"project_recommendations\": [{\n      \"recommendation\": \"\",\n      \"severity\": \"\"\n    }],\n    \"observations\": []\n  }")
				.append("placeHolders", Arrays.asList("ANALYSIS_REPORT_PLACEHOLDER", "KPI_DATA_BY_PROJECT_PLACEHOLDER",
						"USER_ROLE_PLACEHOLDER"));

		collection.insertMany(Arrays.asList(kpiCorrelationAnalysis, kpiRecommendationPrompt, sprintGoalsPrompt));

	}

	@RollbackExecution
	public void rollback() {
		// We are inserting the documents through DDL, no rollback required.
	}
}