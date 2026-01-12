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

package com.publicissapient.kpidashboard.apis.mongock.rollback.release_1330;

import static com.publicissapient.kpidashboard.common.constant.PromptKeys.KPI_CORRELATION_ANALYSIS_REPORT;
import static com.publicissapient.kpidashboard.common.constant.PromptKeys.KPI_RECOMMENDATION_PROMPT;
import static com.publicissapient.kpidashboard.common.constant.PromptKeys.SPRINT_GOALS_SUMMARY;

import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.MongoCollection;
import com.publicissapient.kpidashboard.common.constant.PromptKeys;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;

@ChangeUnit(
		id = "r_insert_Prompt_Details",
		order = "013302",
		author = "shunaray",
		systemVersion = "13.3.0")
public class PromptDetailsChangeUnit {

	private static final String PROMPT_DETAILS_COLLECTION = "prompt_details";
	public static final String CONTEXT = "context";
	public static final String INSTRUCTIONS = "instructions";
	public static final String INPUT = "input";
	public static final String OUTPUT_FORMAT = "outputFormat";
	public static final String PLACEHOLDER = "placeHolders";

	private final MongoTemplate mongoTemplate;

	public PromptDetailsChangeUnit(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Execution
	public void execution() {
		MongoCollection<Document> collection = mongoTemplate.getCollection(PROMPT_DETAILS_COLLECTION);
		collection.deleteMany(
				new Document(
						"key",
						new Document(
								"$in",
								Arrays.asList(
										KPI_CORRELATION_ANALYSIS_REPORT,
										KPI_RECOMMENDATION_PROMPT,
										SPRINT_GOALS_SUMMARY,
										PromptKeys.KPI_DATA,
										PromptKeys.KPI_SEARCH))));
	}

	@RollbackExecution
	public void rollback() {
		MongoCollection<Document> collection = mongoTemplate.getCollection(PROMPT_DETAILS_COLLECTION);

		Document sprintGoalsPrompt =
				new Document()
						.append("key", SPRINT_GOALS_SUMMARY)
						.append(
								CONTEXT,
								"You are a helpful assistant that summarizes sprint goals for executive stakeholders.")
						.append(
								"task",
								"Rewrite the following sprint goals into a concise and professional summary tailored for executive stakeholders.")
						.append(
								INSTRUCTIONS,
								Arrays.asList(
										"Present the output as a bulleted list.",
										"Use clear, outcome-focused language.",
										"Limit the total response to 50 words.",
										"Emphasize business value or strategic alignment where possible."))
						.append(INPUT, "Sprint goals: SPRINT_GOALS_PLACEHOLDER")
						.append(OUTPUT_FORMAT, "A concise and professional bulleted list.")
						.append(PLACEHOLDER, List.of("SPRINT_GOALS_PLACEHOLDER"));

		Document kpiCorrelationAnalysis =
				new Document()
						.append("key", KPI_CORRELATION_ANALYSIS_REPORT)
						.append(
								CONTEXT,
								"Below is the “analysis‐report” that summarizes the comprehensive pair–by–pair correlation analysis among the seven key‐performance indicator (KPI) columns on our multi–sprint project dataset. In this example we assume that all KPI values were measured using a Pearson–based procedure and that for each computed correlation a “weight” (an accuracy or reliability measure on a scale from 0 to 1, with values nearer 1 indicating very high confidence given the large number of sprints in the dataset) has been derived. (In our analysis no KPI was excluded and all 7×7 combinations – including the “self–correlations” – are shown.)\n")
						.append(
								"task",
								"Analyze and interpret the correlation matrix of KPIs to identify strong relationships and potential areas of concern in project performance.")
						.append(
								INSTRUCTIONS,
								Arrays.asList(
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

		Document kpiRecommendationPrompt =
				new Document()
						.append("key", KPI_RECOMMENDATION_PROMPT)
						.append(
								CONTEXT,
								"You are an AI data scientist, your task is to analyze the KPI data for a project using the steps provided below:")
						.append("task", "Analyze the KPI data and generate a comprehensive report.")
						.append(
								INSTRUCTIONS,
								Arrays.asList(
										"1. Extract the correlation coefficients and weights for the benchmark matrix from analysis report generated through large kpi dataset: ANALYSIS_REPORT_PLACEHOLDER.",
										"2. Compute the pairwise correlation coefficients and weights for the project data",
										"3. Calculate the pairwise scores based on the benchmark comparison using the formula: text(pair_score)_(i,j) = (1 - Delta r) cdot w_(benchmark) - (0.2 cdot Delta r text(if) Delta r > 0.2)' Normalize between 0 and 1.",
										"4. Aggregate all pairwise scores and normalize across 49 pairs.",
										"5. Scale the normalized score to a percentage to get the project health."))
						.append(
								INPUT,
								"Based on the KPIs – sprint_velocity_kpi_value, commitment_reliability_kpi_value, sprint_capacity_utilization_kpi_value, defect_injection_rate_kpi_value, scope_churn_issue_count_kpi_value, lead_time_for_change_kpi_value, code_build_time_kpi_value – analyze the provided dataset KPI_DATA_BY_PROJECT_PLACEHOLDER, which contains kpi data of latest five sprints marked by date,analyze the performance of each kpi and generate a score for the project performance.")
						.append(
								OUTPUT_FORMAT,
								"Provide a comprehensive report with:\n  1. The project health score as a percentage\n  2. Observations on significant deviations between the project and benchmark values\n  3. Provide any recommendations to address observed discrepancies and how can the project health be improved,\n  do not mention the size of dataset in this section\n\n  Format the comprehensive report in parsable json format,\n  the recommendations should be actionable and specific to the KPIs analyzed and specifically tailored\n  for USER_ROLE_PLACEHOLDER role\n  the key\n  for project health should be titled 'project_health_value'\n  and recommendation key should be 'project_recommendations',\n  in the project_recommendations key,\n  provide a list of recommendations with each recommendation should have key recommendation and value as a string and another key severity and value as a string,\n  the severity can be one of the following: low,\n  medium,\n  high,\n  critical {\n    \"project_health_value\": 0,\n    \"project_recommendations\": [{\n      \"recommendation\": \"\",\n      \"severity\": \"\"\n    }],\n    \"observations\": []\n  }")
						.append(
								PLACEHOLDER,
								Arrays.asList(
										"ANALYSIS_REPORT_PLACEHOLDER",
										"KPI_DATA_BY_PROJECT_PLACEHOLDER",
										"USER_ROLE_PLACEHOLDER"));

		Document kpiData = insertKpiData();
		Document kpiSearch = insertKpiSearch();

		collection.insertMany(
				Arrays.asList(
						kpiCorrelationAnalysis,
						kpiRecommendationPrompt,
						sprintGoalsPrompt,
						kpiData,
						kpiSearch));
	}

	private Document insertKpiSearch() {
		return new Document("key", PromptKeys.KPI_SEARCH)
				.append(CONTEXT, "You are a helpful assistant with a system role")
				.append(
						"task",
						"Search for relevant kpis and provide the kpiid in parsable format on the basis of kpi information and user query")
				.append(
						INSTRUCTIONS,
						Arrays.asList(
								"1. Given a list of KPIs in the format <kpiId>:<definition> : KPI_DATA and a user query",
								"2. Match based on context, including synonyms or implied meanings.- Return multiple IDs if relevant and a message"))
				.append(INPUT, "User Query: USER_QUERY")
				.append(
						OUTPUT_FORMAT,
						"Format the response in parsable format where message is the reason for match or mismatch\n and kpis will be comma-seperated matched kpis otherwise null")
				.append(PLACEHOLDER, Arrays.asList("KPI_DATA"));
	}

	private Document insertKpiData() {
		return new Document("key", PromptKeys.KPI_DATA)
				.append(
						INSTRUCTIONS,
						Arrays.asList(
								"kpi14:Percentage of Defect created and linked to stories in a sprint against the number of stories in the same sprint",
								"kpi82:percentage of tickets that passed QA with no return transition or any tagging to a specific configured status and no linkage of a defect",
								"kpi111:total number of defect created and linked to stories in a sprint against the size of stories in the same sprint",
								"kpi35:percentage of defects leaked from the QA (sprint) testing stage to the UAT/Production stage",
								"kpi34:percentage of defects closed against the total count tagged to the iteration",
								"kpi37:percentage of defect rejection  based on status or resolution of the defect",
								"kpi28:number of defects grouped by priority in an iteration",
								"kpi36:number of defects grouped by root cause in an iteration",
								"kpi126:Comparative view of number of defects created and number of defects closed in an iteration.",
								"kpi42:progress of automation of regression test cases (the test cases which are marked as part of regression suite.",
								"kpi16:progress of automation of test cases created within the Sprint",
								"kpi17:amount of code that is covered by unit tests.",
								"kpi38:count of issues that voilates the set of coding rules, defined through the associated Quality profile for each programming language in the project.",
								"kpi27:Time Estimate required to fix all Issues/code smells reported in Sonar code analysis.",
								"kpi116:proportion of builds that have failed over a given period of time",
								"kpi70percentage of test cases that have been executed & and the test that have passed.",
								"kpi40:Number of Issues assigned in a sprint.",
								"kpi72:percentage of work completed at the end of a iteration in comparison to the initial scope and the final scope",
								"kpi5:percentage the iteration velocity against the average velocity of last 3 iteration.",
								"kpi39:rate of delivery across Sprints. Average velocity is calculated for the latest 5 sprints",
								"kpi46:the outcome of sprint as planned estimate vs actual estimate",
								"kpi84:efficiency of the code review process in a team",
								"kpi11:Comparative view of number of check-ins and number of merge request raised for a period.",
								"kpi8:time taken for a builds of a given Job.",
								"kpi118:how often code is deployed to production in a period",
								"kpi51:overall open tickets during a defined period grouped by RCA. It considers the gross open and closed count during the period.",
								"kpi73:number of releases done in a month",
								"kpi113:Cost of delay (CoD) is a indicator of the economic value of completing a feature sooner as opposed to later.",
								"kpi55:Ticket open vs closed rate by type gives a comparison of new tickets getting raised vs number of tickets getting closed grouped by issue type during a defined period.",
								"kpi54:Ticket open vs closed rate by priority gives a comparison of new tickets getting raised vs number of tickets getting closed grouped by priority during a defined period.",
								"kpi50:overall open tickets during a defined period grouped by priority. It considers the gross open and closed count during the period.",
								"kpi48:overall open tickets during a defined period grouped by Status. It considers the gross open and closed count during the period.",
								"kpi997:Measure of all the open tickets based on their ageing, grouped by priority",
								"kpi63:Measures progress of automation of regression test cases",
								"kpi62:Measure  of the amount of code that is covered by unit tests.",
								"kpi64:count of issues that voilates the set of coding rules, defined through the associated Quality profile for each programming language in the project.",
								"kpi128:It shows count of the issues having a due date which are planned to be completed until today and how many of these issues have actually been completed. It also depicts the delay in completing the planned issues in terms of days.",
								"kpi67:Time Estimate required to fix all Issues/code smells reported in Sonar code analysis",
								"kpi71:percentage of test cases that have been executed & the percentage that have passed in a defined duration.",
								"kpi49:Ticket velocity measures the size of tickets (in story points) completed in a defined duration",
								"kpi58:Team Capacity is sum of capacity of all team member measured in hours during a defined period. This is defined/managed by project administration section",
								"kpi66:time taken for a build of a given Job.",
								"kpi65:count of check in in repo for the defined period.",
								"kpi53:Total time between a request was made and  all work on this item is completed and the request was delivered .",
								"kpi74:number of releases done in a month",
								"kpi114:Cost of delay (CoD) is a indicator of the economic value of completing a feature sooner as opposed to later.",
								"kpi119:Remaining work in the iteration in terms count of issues & sum of story estimates. Sum of remaining hours required to complete pending work.",
								"kpi127:It groups all the open production defects based on their ageing in the backlog.",
								"kpi75:Estimate vs Actual gives a comparative view of the sum of estimated hours of all issues in an iteration as against the total time spent on these issues.",
								"kpi123:It gives intelligence to the team about number of issues that could potentially not get completed during the iteration. Issues which have a Predicted Completion date > Sprint end date are considered.",
								"kpi122:It gives intelligence to users about how many issues can be completed on a particular day of an iteration. An issue is included as a possible closure based on the calculation of Predicted completion date.",
								"kpi120:Iteration commitment shows in terms of issue count and story points the Initial commitment (issues tagged when the iteration starts), Scope added and Scope removed.",
								"kpi124:It shows the count of issues which do not have estimates and count of In progress issues without any work logs.",
								"kpi133:It showcases the count of defect linked to stories and count that are not linked to any story. The defect injection rate and defect density are shown to give a wholistic view of quality of ongoing iteration",
								"kpi125:Iteration Burnup KPI shows the cumulative actual progress against the overall scope of the iteration on a daily basis. For teams putting due dates at the beginning of iteration, the graph additionally shows the actual progress in comparison to the planning done and also predicts the probable progress for the remaining days of the iteration.",
								"kpi135:Percentage of tickets that passed QA with no return transition or any tagging to a specific configured status and no linkage of a defect.",
								"kpi139:It measures the percentage of stories rejected during refinement as compared to the overall stories discussed in a week.",
								"kpi136:It shows the breakup of all defects within an iteration by status. User can view the total defects in the iteration as well as the defects created after iteration start.",
								"kpi137:It shows number of defects reopened in a given span of time in comparison to the total closed defects. For all the reopened defects, the average time to reopen is also available.",
								"kpi141:It shows the breakup of all defects tagged to a release based on Status. The breakup is shown in terms of count & percentage.",
								"kpi142:It shows the breakup of all defects tagged to a release based on RCA. The breakup is shown in terms of count at different testing phases.",
								"kpi143:It shows the breakup of all defects tagged to a release based on Assignee. The breakup is shown in terms of count & percentage.",
								"kpi144:It shows the breakup of all defects tagged to a release based on Priority. The breakup is shown in terms of count & percentage.",
								"kpi147:It shows the breakup by status of issues tagged to a release. The breakup is based on both issue count and story points",
								"kpi3:Lead Time is the time from the moment when the request was made by a client and placed on a board to when all work on this item is completed and the request was delivered to the client",
								"kpi148:Flow load indicates how many items are currently in the backlog. This KPI emphasizes on limiting work in progress to enabling a fast flow of issues",
								"kpi146:Flow Distribution evaluates the amount of each kind of work (issue types) which are open in the backlog over a period of time.",
								"kpi150:It shows the cumulative daily actual progress of the release against the overall scope. It also shows additionally the scope added or removed during the release w.r.t Dev/Qa completion date and Dev/Qa completion status for the Release tagged issues",
								"kpi151:Total count of issues in the Backlog with a breakup by Status.",
								"kpi152:Total count of issues in the backlog with a breakup by issue type.",
								"kpi153:PI predictability is calculated by the sum of the actual value achieved against the planned value at the beginning of the PI",
								"kpi155:Total count of issues in the backlog with a breakup by defect type.",
								"kpi156:LEAD TIME FOR CHANGE measures the velocity of software delivery.",
								"kpi157:NUMBER OF CHECK-INS helps in measuring the transparency as well the how well the tasks have been broken down. NUMBER OF MERGE REQUESTS when looked at along with commits highlights the efficiency of the review process",
								"kpi158:MEAN TIME TO MERGE measures the efficiency of the code review process in a team",
								"kpi159:NUMBER OF CHECK-INS helps in measuring the transparency as well the how well the tasks have been broken down.",
								"kpi160:Pickup time measures the time a pull request waits for someone to start reviewing it. Low pickup time represents strong teamwork and a healthy review",
								"kpi162:Pull request size measures the number of code lines modified in a pull request. Smaller pull requests are easier to review, safer to merge, and correlate to a lower cycle time.",
								"kpi164:Scope churn explain the change in the scope of sprint since the start of iteration",
								"kpi163:It gives a breakup of escaped defects by testing phase",
								"kpi165:It depicts the progress of each epic in a release in terms of total count and %age completion.",
								"kpi169:It depicts the progress of each epic in terms of total count and %age completion.",
								"kpi161:Iteration readiness depicts the state of future iterations w.r.t the quality of refined Backlog",
								"kpi166:Mean time to recover will be based on the Production incident tickets raised during a certain period of time.",
								"kpi168:Sonar Code Quality is graded based on the static and dynamic code analysis procedure built in Sonarqube that analyses code from multiple perspectives.",
								"kpi170:The percentage of time spent in work states vs wait states across the lifecycle of an issue",
								"kpi171:Cycle time helps ascertain time spent on each step of the complete issue lifecycle. It is being depicted in the visualization as 3 core cycles - Intake to DOR, DOR to DOD, DOD to Live",
								"kpi172:Build frequency refers the number of successful builds done in a specific time frame.",
								"kpi173:Percentage of code changes in which an engineer rewrites code that they recently updated (within the past three weeks).",
								"kpi176:It displayed all the risks and dependencies tagged in a sprint",
								"kpi178:It shows the breakup of all defects tagged to a release grouped by Status, Priority, or RCA.",
								"kpi179:Displays the cumulative daily planned dues of the release based on the due dates of work items within the release scope.It provides an overview of the entire release scope.",
								"kpi180:The percentage of total pull requests opened that are reverts.",
								"kpi181:The percentage of opened Pull Requests that are declined within a timeframe.",
								"kpi182:PR success rate measures the number of pull requests that went through the process without being abandoned or discarded as against the total PRs raised in a defined period  A low or declining Pull Request Success Rate represents high or increasing waste",
								"kpi184:proportion of builds that have failed over a given period of time",
								"kpi183:how often code is deployed to production in a period",
								"kpi185:Innovation rate aims at identifying the volume of brand new additions to a codebase (functional and non-functional features, etc) by measuring the newly added code at repository level.\nMore precisely, the New source lines of code(LOC) from a commit. (git log stats insertions)",
								"kpi186:The percentage of merged pull requests that are addressing defects.",
								"kpi187:number of work items that are not adequately refined in time for development planning. This includes: Work items in the current sprint that do not meet DoR (Definition of Ready).",
								"kpi188:number of work items that are slated for the next sprint but still lack necessary refinement details. Work items tagged to the next sprint that lack sufficient refinement (e.g., missing description, acceptance criteria, etc).",
								"kpi191:percentage of defects unresolved against the total count tagged to the iteration",
								"kpi190:It shows number of defects reopened in a given span of time in comparison to the total closed defects. For all the reopened defects, the average time to reopen is also available.",
								"kpi121:Planned capacity is the development team's available time",
								"kpi989:the maturity of the project",
								"kpi131:Total time when any issue is unable to proceed due to internal or external dependency or issue needs to move to picked up by a team member",
								"kpi129:issue progress and identifies areas for improvement by calculating work versus wait time percentage.",
								"kpi138:Count of issues which are refined in the backlog, and average time for a backlog to be refined, backlog strength",
								"kpi149:tracking moral of team members",
								"kpi154:Current iteration progress"));
	}
}
