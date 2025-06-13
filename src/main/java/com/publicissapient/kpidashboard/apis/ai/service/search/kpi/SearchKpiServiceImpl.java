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

package com.publicissapient.kpidashboard.apis.ai.service.search.kpi;

import java.util.List;
import java.util.Objects;

import com.publicissapient.kpidashboard.apis.ai.dto.response.search.kpi.SearchKpiResponseDTO;
import com.publicissapient.kpidashboard.apis.ai.parser.ParserStategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.StringUtils;

import com.publicissapient.kpidashboard.apis.aigateway.dto.response.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.apis.aigateway.service.AiGatewayService;

import jakarta.ws.rs.InternalServerErrorException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Slf4j
@Service
public class SearchKpiServiceImpl implements SearchKPIService {

	private static final String COULD_NOT_PROCESS_SPRINT_GOALS_SUMMARIZATION_ERROR = "Could not process the sprint goals summarization.";
	private static final String PROMPT = "You are given a dictionary of KPI definitions, where each key is a kpiId and each value is the definition.\n"
			+ "KPI Dictionary: %s /nUser Query: %s /n Please return all relevant kpiIds and explain why each one matches.";

	private final AiGatewayService aiGatewayService;

	@Qualifier("SearchParser")
	private final ParserStategy<SearchKpiResponseDTO> parserStategy;

	@Override
	public SearchKpiResponseDTO searchRelatedKpi(String userMessage) {
		if (StringUtils.isEmpty(userMessage)) {
			log.error(String.format("%s No prompt configuration was found",
					"Could not process the user message to seach kpi."));
			throw new InternalServerErrorException("Could not process the user message to seach kpi.");
		}
		List<String> kpis = List.of(
				"kpi14:Meausures the Percentage of Defect created and linked to stories in a sprint against the number of stories in the same sprint",
				"kpi82:Measures the percentage of tickets that passed QA with no return transition or any tagging to a specific configured status and no linkage of a defect",
				"kpi111:Measures the total number of defect created and linked to stories in a sprint against the size of stories in the same sprint",
				"kpi35:Measures the percentage of defects leaked from the QA (sprint) testing stage to the UAT/Production stage",
				"kpi34:Measure of percentage of defects closed against the total count tagged to the iteration",
				"kpi37:Measures the percentage of defect rejection  based on status or resolution of the defect",
				"kpi28:Measures the number of defects grouped by priority in an iteration",
				"kpi36:Measures the number of defects grouped by root cause in an iteration",
				"kpi126:Comparative view of number of defects created and number of defects closed in an iteration.",
				"kpi42:Measures the progress of automation of regression test cases (the test cases which are marked as part of regression suite.",
				"kpi16:Measures the progress of automation of test cases created within the Sprint",
				"kpi17:Measure  of the amount of code that is covered by unit tests.",
				"kpi38:Measures the count of issues that voilates the set of coding rules, defined through the associated Quality profile for each programming language in the project.",
				"kpi27:Time Estimate required to fix all Issues/code smells reported in Sonar code analysis.",
				"kpi116:Measures the proportion of builds that have failed over a given period of time",
				"kpi70:Measures the percentage of test cases that have been executed & and the test that have passed.",
				"kpi40:Number of Issues assigned in a sprint.",
				"kpi72:Measures the percentage of work completed at the end of a iteration in comparison to the initial scope and the final scope",
				"kpi5:Measures the percentage the iteration velocity against the average velocity of last 3 iteration.",
				"kpi39:Measures the rate of delivery across Sprints. Average velocity is calculated for the latest 5 sprints",
				"kpi46:Measure the outcome of sprint as planned estimate vs actual estimate",
				"kpi84:Measures the efficiency of the code review process in a team",
				"kpi11:Comparative view of number of check-ins and number of merge request raised for a period.",
				"kpi8:Measures the time taken for a builds of a given Job.",
				"kpi118:Measures how often code is deployed to production in a period",
				"kpi51:Measures of  overall open tickets during a defined period grouped by RCA. It considers the gross open and closed count during the period.",
				"kpi73:Measures the number of releases done in a month",
				"kpi113:Cost of delay (CoD) is a indicator of the economic value of completing a feature sooner as opposed to later.",
				"kpi55:Ticket open vs closed rate by type gives a comparison of new tickets getting raised vs number of tickets getting closed grouped by issue type during a defined period.",
				"kpi54:Ticket open vs closed rate by priority gives a comparison of new tickets getting raised vs number of tickets getting closed grouped by priority during a defined period.",
				"kpi50:Measures of  overall open tickets during a defined period grouped by priority. It considers the gross open and closed count during the period.",
				"kpi48:Measures the overall open tickets during a defined period grouped by Status. It considers the gross open and closed count during the period.",
				"kpi997:Measure of all the open tickets based on their ageing, grouped by priority",
				"kpi63:Measures progress of automation of regression test cases",
				"kpi62:Measure  of the amount of code that is covered by unit tests.",
				"kpi64:Measures the count of issues that voilates the set of coding rules, defined through the associated Quality profile for each programming language in the project.",
				"kpi128:It shows count of the issues having a due date which are planned to be completed until today and how many of these issues have actually been completed. It also depicts the delay in completing the planned issues in terms of days.",
				"kpi67:Time Estimate required to fix all Issues/code smells reported in Sonar code analysis",
				"kpi71:Measures the percentage of test cases that have been executed & the percentage that have passed in a defined duration.",
				"kpi49:Ticket velocity measures the size of tickets (in story points) completed in a defined duration",
				"kpi58:Team Capacity is sum of capacity of all team member measured in hours during a defined period. This is defined/managed by project administration section",
				"kpi66:Measures the time taken for a build of a given Job.",
				"kpi65:Measures of the the count of check in in repo for the defined period.",
				"kpi53:Measures  Total time between a request was made and  all work on this item is completed and the request was delivered .",
				"kpi74:Measures the number of releases done in a month",
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
				"kpi179:Displays the cumulative daily planned dues of the release based on the due dates of work items within the release scope.\n\nAdditionally, it provides an overview of the entire release scope.",
				"kpi180:The percentage of total pull requests opened that are reverts.",
				"kpi181:The percentage of opened Pull Requests that are declined within a timeframe.",
				"kpi182:PR success rate measures the number of pull requests that went through the process without being abandoned or discarded as against the total PRs raised in a defined period  A low or declining Pull Request Success Rate represents high or increasing waste",
				"kpi184:Measures the proportion of builds that have failed over a given period of time",
				"kpi183:Measures how often code is deployed to production in a period",
				"kpi185:Innovation rate aims at identifying the volume of brand new additions to a codebase (functional and non-functional features, etc) by measuring the newly added code at repository level.\nMore precisely, the New source lines of code(LOC) from a commit. (git log stats insertions)",
				"kpi186:The percentage of merged pull requests that are addressing defects.",
				"kpi187:Measures the number of work items that are not adequately refined in time for development planning. This includes: Work items in the current sprint that do not meet DoR (Definition of Ready).",
				"kpi188:Measures the number of work items that are slated for the next sprint but still lack necessary refinement details. Work items tagged to the next sprint that lack sufficient refinement (e.g., missing description, acceptance criteria, etc).",
				"kpi191:Measure of percentage of defects unresolved against the total count tagged to the iteration",
				"kpi190:It shows number of defects reopened in a given span of time in comparison to the total closed defects. For all the reopened defects, the average time to reopen is also available.");

		StringBuilder kpiBlock = new StringBuilder();
		for (String line : kpis) {
			kpiBlock.append(line).append("\n");
		}

		// Prompt to the AI
		String prompt = """
				You are a helpful assistant with a system role. 
				Given a list of KPIs in the format <kpiId>:<definition> and a user query,
				Instructions: - Match based on context, including synonyms or implied meanings.- Return multiple IDs if relevant.- If no match is found, reply with: "No relevant KPI found.			
				Format the response in parsable json format, 
				if kpi not found provide code as 500 and kpis will be empty.
				if found then provide the code as 200 and kpis will be **comma-separated list of matching KPI IDs**.
				{
				  "code": 
				  "kpis":
				}
				"
				 KPI List:"""
				+ kpiBlock + "\nUser Query: " + userMessage;
		ChatGenerationResponseDTO chatGenerationResponseDTO = aiGatewayService.generateChatResponse(prompt);
		if (Objects.isNull(chatGenerationResponseDTO) || StringUtils.isEmpty(chatGenerationResponseDTO.content())) {
			log.error(String.format("%s. Ai Gateway returned a null or empty response",
					COULD_NOT_PROCESS_SPRINT_GOALS_SUMMARIZATION_ERROR));
			throw new InternalServerErrorException(COULD_NOT_PROCESS_SPRINT_GOALS_SUMMARIZATION_ERROR);
		}

		return parserStategy.parse(chatGenerationResponseDTO.content());
		
	}
}
