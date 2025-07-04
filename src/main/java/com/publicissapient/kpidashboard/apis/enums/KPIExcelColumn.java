/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.enums;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.bson.types.ObjectId;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.common.model.application.AdditionalFilterCategory;

/** to order the headings of excel columns */
@SuppressWarnings("java:S1192")
public enum KPIExcelColumn {
	CODE_BUILD_TIME("kpi8",
			Arrays.asList("Project Name", "Job Name / Pipeline Name", "Start Time", "End Time", "Duration", "Build Status",
					"Build Url", "Weeks")), ISSUE_COUNT(
							"kpi40",
							Arrays.asList("Squad", "Sprint Name", "Issue ID", "Issue Description", "Issue Type", "Priority",
									"Story Points", "Original Time Estimate (in hours)", "Time Spent (in hours)")), CODE_COMMIT(
											"kpi11",
											Arrays.asList("Project", "Repo", "Branch", "Days/Weeks", "No Of Commit",
													"No of Merge")), REPO_TOOL_CODE_COMMIT("kpi157",
															Arrays.asList("Project", "Repo", "Branch", "Days/Weeks", "Developer", "No Of Commit",
																	"No of Merge")),

	MEAN_TIME_TO_MERGE("kpi84",
			Arrays.asList("Project", "Repo", "Branch", "Merge Request Url", "Days/Weeks", "PR Raised Time", "PR Merged Time",
					"Mean Time To Merge (In Hours)")), REPO_TOOL_MEAN_TIME_TO_MERGE(
							"kpi158",
							Arrays.asList("Project", "Repo", "Branch", "Days/Weeks", "Author", "Merge Request Url", "PR Raised Time",
									"PR Merged Time", "Mean Time To Merge (In Hours)")), AVERAGE_RESOLUTION_TIME("kpi83",
											Arrays.asList("Sprint Name", "Story ID", "Issue Description", "Issue Type",
													"Resolution Time(In Days)")),

	LEAD_TIME("kpi3",
			Arrays.asList("Issue ID", "Issue Type", "Issue Description", "Created Date", "Live Date", "Lead Time (In Days)")),

	LEAD_TIME_KANBAN("kpi53", Arrays.asList("Project Name", "Story ID", "Issue Description", "Open to Triage(In Days)",
			"Triage to Complete (In Days)", "Complete TO Live (In Days)", "Lead Time (In Days)")),

	SPRINT_VELOCITY("kpi39",
			Arrays.asList("Sprint Name", "Issue ID", "Issue Description", "Squad", "Issue Type", "Priority", "Story Points",
					"Original Time Estimate (in hours)", "Time Spent (in hours)")), SPRINT_PREDICTABILITY(
							"kpi5",
							Arrays.asList("Sprint Name", "Issue ID", "Issue Description", "Squad", "Issue Type", "Priority",
									"Story Points", "Original Time Estimate (in hours)",
									"Time Spent (in hours)")), SPRINT_CAPACITY_UTILIZATION(
											"kpi46",
											Arrays.asList("Sprint Name", "Issue ID", "Issue Description", "Squad", "Issue Type", "Priority",
													"Story Points", "Original Time Estimate (in hours)",
													"Time Spent (in hours)")), COMMITMENT_RELIABILITY(
															"kpi72",
															Arrays.asList("Sprint Name", "Issue ID", "Issue Description", "Issue Status", "Scope",
																	"Squad", "Issue Type", "Priority", "Story Points",
																	"Original Time Estimate (in hours)", "Time Spent (in hours)")),

	DEFECT_INJECTION_RATE("kpi14", Arrays.asList("Sprint Name", "Story ID", "Story Description", "Squad", "Defect ID",
			"Defect Description", "Root Cause", "Defect Priority", "Defect Status", "Time Spent (in hours)")),

	FIRST_TIME_PASS_RATE("kpi82",
			Arrays.asList("Sprint Name", "Story ID", "Story Description", "First Time Pass", "Defect ID",
					"Defect Description", "Squad", "Root Cause", "Defect Priority", "Defect Status", "Time Spent (in hours)")),

	DEFECT_DENSITY("kpi111",
			Arrays.asList("Sprint Name", "Story ID", "Story Description", "Size(story point/hours)", "Squad", "Defect ID",
					"Defect Description", "Root Cause", "Defect Priority", "Defect Status", "Time Spent (in hours)")),

	OPEN_DEFECT_RATE("kpi191",
			Arrays.asList("Sprint Name", "Defect ID", "Defect Description","Open Defect", "Story ID", "Story Description", "Squad",
					"Root Cause", "Defect Priority", "Time Spent (in hours)")),
	DEFECT_SEEPAGE_RATE("kpi35",
			Arrays.asList("Sprint Name", "Defect ID", "Defect Description", "Escaped defect identifier", "Story ID",
					"Story Description", "Squad", "Root Cause", "Defect Priority", "Defect Status", "Time Spent (in hours)")),

	DEFECT_REMOVAL_EFFICIENCY("kpi34",
			Arrays.asList("Sprint Name", "Defect ID", "Defect Description", "Story ID", "Story Description", "Squad",
					"Root Cause", "Defect Priority", "Defect Removed", "Defect Status", "Time Spent (in hours)")),

	DEFECT_REJECTION_RATE("kpi37", Arrays.asList("Sprint Name", "Defect ID", "Defect Description", "Story ID",
			"Story Description", "Squad", "Defect Rejected")),

	DEFECT_COUNT_BY_PRIORITY("kpi28", Arrays.asList("Sprint Name", "Defect ID", "Defect Description", "Story ID",
			"Story Description", "Squad", "Root Cause", "Defect Priority", "Defect Status", "Time Spent (in hours)")),

	DEFECT_COUNT_BY_RCA("kpi36", Arrays.asList("Sprint Name", "Defect ID", "Defect Description", "Story ID",
			"Story Description", "Squad", "Root Cause", "Defect Priority", "Defect Status", "Time Spent (in hours)")),

	DEFECT_COUNT_BY_PRIORITY_PIE_CHART("kpi140", Arrays.asList("Defect ID", "Issue Description", "Squad", "Issue Status",
			"Issue Type", "Size(story point/hours)", "Root Cause", "Priority", "Assignee", "Created during Iteration")),

	DEFECT_COUNT_BY_RCA_PIE_CHART("kpi132", Arrays.asList("Defect ID", "Issue Description", "Squad", "Issue Status",
			"Issue Type", "Size(story point/hours)", "Root Cause", "Priority", "Assignee", "Created during Iteration")),

	CREATED_VS_RESOLVED_DEFECTS("kpi126",
			Arrays.asList("Sprint Name", "Created Defect ID", "Defect Description", "Squad", "Story ID", "Story Description",
					"Root Cause", "Defect Priority", "Defect Status", "Time Spent (in hours)", "Defect added after Sprint Start",
					"Resolved Status")),

	DEFECT_COUNT_BY_STATUS_PIE_CHART("kpi136", Arrays.asList("Issue Id", "Issue Description", "Squad", "Issue Status",
			"Issue Type", "Size(story point/hours)", "Root Cause List", "Priority", "Assignee", "Created during Iteration")),

	REGRESSION_AUTOMATION_COVERAGE("kpi42", Arrays.asList("Sprint Name", "Test Case ID", "Automated")),

	INSPRINT_AUTOMATION_COVERAGE("kpi16", Arrays.asList("Sprint Name", "Test Case ID", "Linked Story ID", "Automated")),

	UNIT_TEST_COVERAGE("kpi17", Arrays.asList("Project", "Job Name / Pipeline Name", "Unit Coverage", "Weeks")),

	SONAR_VIOLATIONS("kpi38",
			Arrays.asList("Project", "Job Name / Pipeline Name", "Violation Type", "Violation Severity", "Weeks")),

	SONAR_TECH_DEBT("kpi27", Arrays.asList("Project", "Job Name / Pipeline Name", "Tech Debt (in days)", "Weeks")),

	CHANGE_FAILURE_RATE("kpi116",
			Arrays.asList("Project", "Job Name / Pipeline Name", "Total Build Count", "Total Build Failure Count",
					"Build Failure Percentage", "Weeks")), CHANGE_FAILURE_RATE_KANBAN(
							"kpi184",
							Arrays.asList("Project", "Job Name / Pipeline Name", "Total Build Count", "Total Build Failure Count",
									"Build Failure Percentage", "Weeks")), BUILD_FREQUENCY("kpi172",
											Arrays.asList("Project Name", "Job Name / Pipeline Name", "Weeks", "Start Date", "Build Url")),

	TEST_EXECUTION_AND_PASS_PERCENTAGE("kpi70",
			Arrays.asList("Sprint Name", "Total Test", "Executed Test", "Execution %", "Passed Test", "Passed %")),

	COST_OF_DELAY("kpi113",
			Arrays.asList("Project Name", "Cost of Delay", "Epic ID", "Epic Name", "Squad", "Epic End Date", "Month")),

	RELEASE_FREQUENCY("kpi73",
			Arrays.asList("Project Name", "Release Name", "Release Description", "Release End Date", "Month")),

	RELEASE_FREQUENCY_KANBAN("kpi74",
			Arrays.asList("Project Name", "Release Name", "Release Description", "Release End Date", "Month")),

	DEPLOYMENT_FREQUENCY("kpi118",
			Arrays.asList("Project Name", "Job Name / Pipeline Name", "Date", "Weeks",
					"Environment")), DEPLOYMENT_FREQUENCY_KANBAN("kpi183",
							Arrays.asList("Project Name", "Job Name / Pipeline Name", "Date", "Weeks", "Environment")),

	DEFECTS_WITHOUT_STORY_LINK("kpi80",
			Arrays.asList("Project Name", "Priority", "Defects Without Story Link", "Issue Description")),

	TEST_WITHOUT_STORY_LINK("kpi79", Arrays.asList("Project Name", "Test Case ID", "Linked to Story")),

	UNLINKED_WORK_ITEMS("kpi129", Arrays.asList("Issue Id", "Issue Description")),

	PRODUCTION_DEFECTS_AGEING("kpi127",
			Arrays.asList("Project Name", "Defect ID", "Issue Description", "Priority", "Created Date", "Status")),

	UNIT_TEST_COVERAGE_KANBAN("kpi62",
			Arrays.asList("Project", "Job Name / Pipeline Name", "Unit Coverage", "Day/Week/Month")),

	SONAR_VIOLATIONS_KANBAN("kpi64",
			Arrays.asList("Project", "Job Name / Pipeline Name", "Sonar Violations", "Day/Week/Month")),

	SONAR_TECH_DEBT_KANBAN("kpi67",
			Arrays.asList("Project", "Job Name / Pipeline Name", "Tech Debt (in days)", "Day/Week/Month")),

	TEST_EXECUTION_KANBAN("kpi71", Arrays.asList("Project", "Execution Date", "Total Test", "Executed Test",
			"Execution %", "Passed Test", "Passed %")),

	KANBAN_REGRESSION_PASS_PERCENTAGE("kpi63", Arrays.asList("Project", "Day/Week/Month", "Test Case ID", "Automated")),

	OPEN_TICKET_AGING_BY_PRIORITY("kpi997",
			Arrays.asList("Project", "Ticket Issue ID", "Priority", "Created Date", "Issue Status")),

	NET_OPEN_TICKET_COUNT_BY_STATUS("kpi48",
			Arrays.asList("Project", "Day/Week/Month", "Ticket Issue ID", "Issue Status", "Created Date")),

	NET_OPEN_TICKET_COUNT_BY_RCA("kpi51",
			Arrays.asList("Project", "Day/Week/Month", "Ticket Issue ID", "Root Cause", "Created Date")),

	TICKET_COUNT_BY_PRIORITY("kpi50",
			Arrays.asList("Project", "Day/Week/Month", "Ticket Issue ID", "Priority", "Created Date")),

	TICKET_OPEN_VS_CLOSED_RATE_BY_TYPE("kpi55",
			Arrays.asList("Project", "Day/Week/Month", "Ticket Issue ID", "Issue Type", "Status")),

	TICKET_OPEN_VS_CLOSE_BY_PRIORITY("kpi54",
			Arrays.asList("Project", "Day/Week/Month", "Ticket Issue ID", "Issue Priority", "Status")),

	TICKET_VELOCITY("kpi49",
			Arrays.asList("Project Name", "Day/Week/Month", "Ticket Issue ID", "Issue Type", "Size (In Story Points)")),

	CODE_BUILD_TIME_KANBAN("kpi66", Arrays.asList("Project Name", "Job Name / Pipeline Name", "Start Time", "End Time",
			"Duration", "Build Status", "Build Url")),

	CODE_COMMIT_MERGE_KANBAN("kpi65", Arrays.asList("Project Name", "Repo", "Branch", "Days/Weeks", "No Of Commit")),

	REPO_TOOL_CODE_COMMIT_MERGE_KANBAN("kpi159",
			Arrays.asList("Project Name", "Repo", "Branch", "Developer", "Days/Weeks", "No Of Commit")),

	TEAM_CAPACITY_KANBAN("kpi58",
			Arrays.asList("Project Name", "Start Date", "End Date", "Estimated Capacity (in hours)")),

	ISSUES_LIKELY_TO_SPILL("kpi123",
			Arrays.asList("Issue Id", "Issue Type", "Issue Description", "Issue Priority", "Size(story point/hours)",
					"Issue Status", "Due Date", "Remaining Estimate", "Predicted Completion Date", "Assignee")),

	ITERATION_COMMITMENT("kpi120", Arrays.asList("Issue Id", "Issue Description", "Issue Status", "Issue Type",
			"Size(story point/hours)", "Priority", "Due Date", "Original Estimate", "Remaining Estimate", "Assignee")),

	ISSUE_HYGINE("kpi124",
			Arrays.asList("Issue Id", "Issue Description", "Issue Status", "Issue Type", "Assignee", "Category")),

	ESTIMATE_VS_ACTUAL("kpi75", Arrays.asList("Issue Id", "Issue Description", "Issue Status", "Issue Type",
			"Original Estimate", "Logged Work", "Assignee")),

	WORK_STATUS("kpi128", Arrays.asList(new KPIExcelColumnInfo("Issue Id", ""),
			new KPIExcelColumnInfo("Issue Description", ""), new KPIExcelColumnInfo("Issue Status", ""),
			new KPIExcelColumnInfo("Issue Type", ""), new KPIExcelColumnInfo("Size(story point/hours)", ""),
			new KPIExcelColumnInfo("Original Estimate", ""), new KPIExcelColumnInfo("Remaining Estimate", ""),
			new KPIExcelColumnInfo("Due Date", ""), new KPIExcelColumnInfo("Actual Start Date", ""),
			new KPIExcelColumnInfo("Dev Completion Date", ""), new KPIExcelColumnInfo("Actual Completion Date", ""),
			new KPIExcelColumnInfo("Delay(in days)",
					"Delay is calculated based on difference between time taken to complete an issue that depends on the Due date and Actual completion date (In Days)"),
			new KPIExcelColumnInfo("Predicted Completion Date", ""), new KPIExcelColumnInfo("Potential Delay(in days)", ""),
			new KPIExcelColumnInfo("Assignee", ""), new KPIExcelColumnInfo("Category", ""))),

	WORK_REMAINING("kpi119",
			Arrays.asList("Issue Id", "Issue Description", "Issue Status", "Issue Type", "Size(story point/hours)",
					"Original Estimate", "Remaining Estimate", "Dev Due Date", "Dev Completion Date", "Due Date",
					"Predicted Completion Date", "Potential Delay(in days)", "Assignee")),

	WASTAGE("kpi131", Arrays.asList("Issue Id", "Issue Type", "Issue Description", "Priority", "Size(story point/hours)",
			"Blocked Time", "Wait Time", "Total Wastage", "Assignee")),

	QUALITY_STATUS("kpi133", Arrays.asList("Issue Id", "Issue Type", "Issue Description", "Issue Status", "Priority",
			"Linked Stories", "Linked Stories Size", "Assignee")),

	CLOSURES_POSSIBLE_TODAY("kpi122", Arrays.asList("Issue Id", "Issue Type", "Issue Description",
			"Size(story point/hours)", "Issue Status", "Due Date", "Remaining Estimate", "Assignee")),

	INVALID("INVALID_KPI", Arrays.asList("Invalid")), BACKLOG_READINESS_EFFICIENCY("kpi138", Arrays.asList("Issue Id",
			"Issue Type", "Issue Description", "Priority", "Size(story point/hours)", "Created Date", "DOR Date")),

	FIRST_TIME_PASS_RATE_ITERATION("kpi135",
			Arrays.asList("Issue Id", "Issue Description", "First Time Pass", "Linked Defect", "Defect Priority")),

	ITERATION_BURNUP("kpi125", Arrays.asList("Issue Id", "Issue Type", "Issue Description", "Size(story point/hours)",
			"Priority", "Assignee", "Issue Status", "Due Date")),

	DEFECT_REOPEN_RATE("kpi137", Arrays.asList("Issue Id", "Issue Description", "Issue Status", "Priority", "Closed Date",
			"Reopen Date", "Time taken to reopen")),

	REFINEMENT_REJECTION_RATE("kpi139",
			Arrays.asList("Issue ID", "Issue Description", "Priority", "Status", "Change Date", "Weeks", "Issue Status")),

	DEFECT_COUNT_BY_STATUS_RELEASE("kpi141",
			Arrays.asList("Issue ID", "Issue Description", "Sprint Name", "Issue Type", "Issue Status", "Root Cause",
					"Priority", "Assignee")), BACKLOG_COUNT_BY_STATUS(
							"kpi151",
							Arrays.asList("Issue ID", "Issue Description", "Issue Type", "Issue Status", "Priority", "Created Date",
									"Updated Date", "Assignee")), ITERATION_READINESS(
											"kpi161",
											Arrays.asList("Issue ID", "Issue Description", "Sprint Name", "Sprint Start Date", "Issue Type",
													"Issue Status", "Size(story point/hours)")), BACKLOG_COUNT_BY_ISSUE_TYPE("kpi152",
															Arrays.asList("Issue ID", "Issue Description", "Issue Type", "Issue Status", "Priority",
																	"Created Date", "Updated Date", "Assignee")),

	DEFECT_COUNT_BY_RCA_RELEASE("kpi142", Arrays.asList("Issue ID", "Issue Description", "Sprint Name", "Issue Type",
			"Issue Status", "Root Cause", "Priority", "Assignee", "Testing Phase")),

	DEFECT_COUNT_BY_ASSIGNEE_RELEASE("kpi143", Arrays.asList("Issue ID", "Issue Description", "Sprint Name", "Issue Type",
			"Issue Status", "Root Cause", "Priority", "Assignee")),

	DEFECT_COUNT_BY_PRIORITY_RELEASE("kpi144", Arrays.asList("Issue ID", "Issue Description", "Sprint Name", "Issue Type",
			"Issue Status", "Root Cause", "Priority", "Assignee")),

	RELEASE_PROGRESS("kpi147", Arrays.asList("Issue ID", "Issue Type", "Issue Description", "Size(story point/hours)",
			"Priority", "Assignee", "Issue Status")),

	HAPPINESS_INDEX_RATE("kpi149", Arrays.asList("Sprint Name", "User Name", "Sprint Rating")),

	FLOW_DISTRIBUTION("Kpi146", Arrays.asList("Date")), FLOW_LOAD("kpi148", Arrays.asList("Date")),

	RELEASE_BURNUP("kpi150", Arrays.asList("Issue ID", "Issue Type", "Issue Description", "Size(story point/hours)",
			"Priority", "Assignee", "Issue Status", "Release Tag Date (Latest)", "Dev Completion Date", "Completion Date")),

	PI_PREDICTABILITY("kpi153", Arrays.asList("Project Name", "Epic ID", "Epic Name", "Squad", "Status", "PI Name",
			"Planned Value", "Achieved Value")),

	DAILY_STANDUP_VIEW("kpi154", Arrays.asList("Remaining Capacity", "Remaining Estimate", "Remaining Work", "Delay")),

	// DTS-26123 start
	DEFECT_COUNT_BY_TYPE("kpi155", Arrays.asList("Issue ID", "Issue Description", "Issue Type", "Issue Status",
			"Sprint Name", "Priority", "Created Date", "Updated Date", "Assignee")),
	// DTS-26123 end
	SCOPE_CHURN("kpi164",
			Arrays.asList("Sprint Name", "Issue ID", "Issue Description", "Issue Status", "Scope Change Date",
					"Scope Change (Added/Removed)", "Squad", "Issue Type", "Priority", "Story Points",
					"Original Time Estimate (in hours)", "Time Spent (in hours)")),

	LEAD_TIME_FOR_CHANGE("Kpi156", Arrays.asList("Project Name", "Weeks", "Story ID", "Lead Time (In Days) [B-A]",
			"Change Completion Date [A]", "Change Release Date [B]")),

	LEAD_TIME_FOR_CHANGE_REPO("Kpi156", Arrays.asList("Project Name", "Weeks", "Story ID", "Lead Time (In Days) [B-A]",
			"Change Completion Date [A]", "Change Release Date [B]", "Merge Request Id", "Branch")),

	RELEASE_DEFECT_BY_TEST_PHASE("kpi163", Arrays.asList("Issue ID", "Issue Description", "Issue Type", "Priority",
			"Sprint Name", "Assignee", "Issue Status", "Testing Phase")),

	PICKUP_TIME("kpi160", Arrays.asList("Project", "Repo", "Branch", "Days/Weeks", "Author", "Merge Request Url",
			"PR Raised Time", "PR Review Time", "PR Status", "Pickup Time (In Hours)")),

	PR_SIZE("kpi162",
			Arrays.asList("Project", "Repo", "Branch", "Author", "Days/Weeks", "No of Merge", "PR Size (No. of lines)")),

	EPIC_PROGRESS("kpi165", Arrays.asList("Epic ID", "Epic Name", "Size(story point/hours)", "Epic Status",
			"To Do(Value/Percentage)", "In Progress(Value/Percentage)", "Done(Value/Percentage)")),

	BACKLOG_EPIC_PROGRESS("kpi169", Arrays.asList("Epic ID", "Epic Name", "Size(story point/hours)", "Epic Status",
			"To Do(Value/Percentage)", "In Progress(Value/Percentage)", "Done(Value/Percentage)")),

	MEAN_TIME_TO_RECOVER("kpi166", Arrays.asList("Project Name", "Weeks", "Story ID", "Issue Type", "Issue Description",
			"Created Date", "Completion Date", "Time to Recover (In Hours)")),

	CODE_QUALITY("kpi168",
			Arrays.asList("Project", "Job Name / Pipeline Name", "Code Quality", "Month")), FLOW_EFFICIENCY("kpi170",
					Arrays.asList("Issue ID", "Issue Type", "Issue Description", "Size (In Story Points)", "Wait Time",
							"Total Time", "Flow Efficiency")),

	CYCLE_TIME("kpi171",
			Arrays.asList("Issue Id", "Issue Type", "Issue Description", "DOR Date", "Intake to DOR", "DOD Date",
					"DOR to DOD", "Live Date", "DOD to Live")), REWORK_RATE("kpi173",
							Arrays.asList("Project", "Repo", "Branch", "Developer", "Days/Weeks", "Rework Rate")),

	REVERT_RATE("kpi180",
			Arrays.asList("Project", "Repo", "Branch", "Author", "Days/Weeks", "Revert PR", "No of Merge", "Revert Rate")),

	PR_SUCCESS_RATE("kpi182", Arrays.asList("Project", "Repo", "Branch", "Author", "Days/Weeks", "No of Merge",
			"Closed PR", "PR Success Rate")),

	PR_DECLINE_RATE("kpi181", Arrays.asList("Project", "Repo", "Branch", "Author", "Days/Weeks", "Declined PR",
			"Closed PR", "PR Decline Rate")),

	RISKS_AND_DEPENDENCIES("kpi176", Arrays.asList("Issue Id", "Issue Type", "Issue Description", "Issue Status",
			"Priority", "Created Date", "Assignee", "Category")),

	RELEASE_PLAN("kpi179",
			Arrays.asList("Issue ID", "Issue Type", "Issue Description", "Size(story point/hours)", "Priority", "Assignee",
					"Issue Status", "Planned Completion Date (Due Date)")), DEFECT_COUNT_BY("kpi178",
							Arrays.asList("Issue ID", "Issue Description", "Sprint Name", "Issue Type", "Issue Status",
									"Root Cause List", "Priority", "Testing Phase", "Assignee")),

	DEFECT_COUNT_BY_EXPORT("kpi178",
			Arrays.asList("Issue ID", "Issue Description", "Sprint Name", "Issue Type", "Issue Status", "Root Cause",
					"Priority", "Testing Phase", "Assignee")), INNOVATION_RATE(
							"kpi185",
							Arrays.asList("Project", "Repo", "Branch", "Developer", "Days/Weeks", "Added Lines",
									"Total Lines Changed", "Innovation Rate")), DEFECT_RATE("kpi186",
											Arrays.asList("Project", "Repo", "Branch", "Author", "Days/Weeks", "Defect PR", "No of Merge",
													"Defect Rate")),
	LATE_REFINEMENT("kpi187", Arrays.asList("Sprint Name", "Date", "Issue Id", "Issue Type", "Issue Description", "Size(story point/hours)",
			"Due Date", "Assignee", "Issue Status", "Un-Refined" )),
    NEXT_SPRINT_LATE_REFINEMENT("kpi188",
                                  Arrays.asList("Issue Id", "Issue Description", "Sprint Name", "Issue Type", "Issue Status", "Un-Refined")),

	DEFECT_REOPEN_RATE_QUALITY("kpi190", Arrays.asList("Issue Id", "Defect Description", "Issue Status",
			"Defect Priority", "Closed Date", "Reopen Date", "Time taken to reopen"));

	// @formatter:on

	private String kpiId;

	private List<String> columns;
	private List<KPIExcelColumnInfo> kpiExcelColumnInfo;

	KPIExcelColumn(String kpiID, List<Object> columns) {
		this.kpiId = kpiID;
		if (columns.get(0) instanceof String) {
			this.columns = columns.stream().map(Object::toString).toList();
		} else {
			ObjectMapper objectMapper = new ObjectMapper();
			List<KPIExcelColumnInfo> kpiExcelColumnInfoList = new ArrayList<>();
			columns.forEach(o -> {
				KPIExcelColumnInfo kpiExcelColumnInfo1 = objectMapper.convertValue(o, KPIExcelColumnInfo.class);
				kpiExcelColumnInfoList.add(kpiExcelColumnInfo1);
			});
			this.kpiExcelColumnInfo = kpiExcelColumnInfoList;
		}
	}

	public List<KPIExcelColumnInfo> getKpiExcelColumnInfo() {
		return kpiExcelColumnInfo;
	}

	/**
	 * Gets kpi id.
	 *
	 * @return the kpi id
	 */
	public String getKpiId() {
		return kpiId;
	}

	/**
	 * Gets source.
	 *
	 * @return the source
	 */
	public List<String> getColumns() {
		return columns;
	}

	public List<String> getColumns(List<Node> nodeList, CacheService cacheService,
			FilterHelperService filterHelperService) {
		Set<ObjectId> projectIds = nodeList.stream().map(a -> a.getProjectFilter().getBasicProjectConfigId())
				.collect(Collectors.toSet());
		boolean squadConfigured = false;
		Map<String, AdditionalFilterCategory> addFilterCat = filterHelperService.getAdditionalFilterHierarchyLevel();
		List<AccountHierarchyData> accountHierarchyData = (List<AccountHierarchyData>) cacheService
				.cacheAccountHierarchyData();
		for (ObjectId projectId : projectIds) {
			if (CollectionUtils.isNotEmpty(accountHierarchyData) && CollectionUtils.isNotEmpty((accountHierarchyData).stream()
					.filter(a -> addFilterCat.containsKey(a.getLabelName()) && a.getBasicProjectConfigId().equals(projectId))
					.toList())) {
				squadConfigured = true;
				break;
			}
		}
		if (!squadConfigured) {
			List<String> modifiableList = new ArrayList<>(columns);
			modifiableList.removeIf(a -> a.equalsIgnoreCase("Squad"));
			return modifiableList;
		}
		return columns;
	}
}
