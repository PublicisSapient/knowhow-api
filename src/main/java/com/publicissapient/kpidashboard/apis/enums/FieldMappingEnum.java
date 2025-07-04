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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** KpiFieldMapping */
@SuppressWarnings("java:S1192")
public enum FieldMappingEnum {
	KPI200("Processor", KPISource.RALLY.name(),
			Arrays.asList("jiradefecttype", "jiraIssueTypeNames", "jiraIterationCompletionStatusCustomField",
					"rootCauseIdentifier", "rootCause", "rootCauseValues", "sprintName", "estimationCriteria",
					"storyPointToHourMapping", "jiraStoryPointsCustomField", "epicCostOfDelay", "epicRiskReduction",
					"epicUserBusinessValue", "epicWsjf", "epicTimeCriticality", "epicJobSize", "additionalFilterConfig",
					"jiraDueDateField", "jiraDueDateCustomField", "jiraDevDueDateField", "jiraDevDueDateCustomField",
					"jiraIssueEpicType", "storyFirstStatus", "notificationEnabler", "epicLink", "jiraSubTaskDefectType",
					"jiraSubTaskIdentification")),
	KPI0("Processor", KPISource.JIRA.name(),
			Arrays.asList("jiradefecttype", "jiraIssueTypeNames", "jiraIterationCompletionStatusCustomField",
					"rootCauseIdentifier", "rootCause", "rootCauseValues", "sprintName", "estimationCriteria",
					"storyPointToHourMapping", "jiraStoryPointsCustomField", "epicCostOfDelay", "epicRiskReduction",
					"epicUserBusinessValue", "epicWsjf", "epicTimeCriticality", "epicJobSize", "additionalFilterConfig",
					"jiraDueDateField", "jiraDueDateCustomField", "jiraDevDueDateField", "jiraDevDueDateCustomField",
					"jiraIssueEpicType", "storyFirstStatus", "notificationEnabler", "epicLink", "jiraSubTaskDefectType",
					"jiraSubTaskIdentification", "includeActiveSprintInBacklogKPI")),

	KPI1("Processor (Kanban)", KPISource.JIRA.name(),
			Arrays.asList("jiraIssueTypeNames", "storyFirstStatus", "rootCauseIdentifier", "epicCostOfDelay",
					"epicRiskReduction", "epicUserBusinessValue", "epicWsjf", "epicTimeCriticality", "epicJobSize",
					"jiraIssueEpicType", "rootCause", "rootCauseValues", "additionalFilterConfig", "estimationCriteria",
					"storyPointToHourMapping", "jiraStoryPointsCustomField", "kanbanRCACountIssueType", "jiraSubTaskDefectType")),

	KPI40("Issue Count", KPISource.JIRA.name(),
			Arrays.asList("jiraStoryIdentificationKpi40", "thresholdValueKPI40", "jiraStoryCategoryKpi40")),

	KPI39("Sprint Velocity", KPISource.JIRA.name(),
			Arrays.asList("jiraIterationCompletionStatusKpi39", "jiraIterationIssuetypeKPI39", "thresholdValueKPI39")),

	KPI5("Sprint Predictability", KPISource.JIRA.name(),
			Arrays.asList("jiraIterationIssuetypeKpi5", "jiraIterationCompletionStatusKpi5", "thresholdValueKPI5")),

	KPI46("Sprint Capacity Utilization", KPISource.JIRA.name(),
			Arrays.asList("jiraSprintCapacityIssueTypeKpi46", "thresholdValueKPI46")),

	KPI82("First Time Pass Rate", KPISource.JIRA.name(),
			Arrays.asList("jiraStatusForDevelopmentKPI82", "jiraKPI82StoryIdentification", "jiraIssueDeliverdStatusKPI82",
					"resolutionTypeForRejectionKPI82", "jiraDefectRejectionStatusKPI82", "defectPriorityKPI82",
					"includeRCAForKPI82", "jiraStatusForQaKPI82", "jiraFtprRejectStatusKPI82", "thresholdValueKPI82",
					"jiraLabelsKPI82")),

	KPI135("First Time Pass Rate (Iteration)", KPISource.JIRA.name(),
			Arrays.asList("jiraStatusForDevelopmentKPI135", "jiraKPI135StoryIdentification",
					"jiraIterationCompletionStatusKPI135", "resolutionTypeForRejectionKPI135", "includeRCAForKPI135",
					"jiraDefectRejectionStatusKPI135", "defectPriorityKPI135", "jiraDefectRejectionStatusKPI135",
					"jiraStatusForQaKPI135", "jiraFtprRejectStatusKPI135", "jiraLabelsKPI135")),

	KPI3("Lead Time (Scrum)", KPISource.JIRA.name(),
			Arrays.asList("jiraIssueTypeKPI3", "jiraLiveStatusKPI3", "thresholdValueKPI3")),

	KPI34("Defect Removal Efficiency", KPISource.JIRA.name(),
			Arrays.asList("jiraDefectRemovalStatusKPI34", "thresholdValueKPI34", "resolutionTypeForRejectionKPI34",
					"includeRCAForKPI34", "defectPriorityKPI34", "jiraDefectRejectionStatusKPI34")),

	KPI37("Defect Rejection Rate", KPISource.JIRA.name(), Arrays.asList("resolutionTypeForRejectionKPI37",
			"jiraDefectRejectionStatusKPI37", "jiraDodKPI37", "thresholdValueKPI37", "defectRejectionLabelsKPI37")),

	KPI28("Defect Count By Priority (Scrum)", KPISource.JIRA.name(), Arrays.asList("jiraDefectCountlIssueTypeKPI28",
			"resolutionTypeForRejectionKPI28", "jiraDefectRejectionStatusKPI28", "thresholdValueKPI28")),

	KPI140("Defect Count by Priority (Iteration)", KPISource.JIRA.name(), Arrays.asList()),

	KPI36("Defect Count by RCA (Scrum)", KPISource.JIRA.name(), Arrays.asList("jiraDefectCountlIssueTypeKPI36",
			"resolutionTypeForRejectionRCAKPI36", "jiraDefectRejectionStatusRCAKPI36", "thresholdValueKPI36")),

	KPI132("Defect Count by RCA (Iteration)", KPISource.JIRA.name(), Arrays.asList()),

	KPI136("Defect Count by Status (Iteration)", KPISource.JIRA.name(), Arrays.asList()),

	KPI14("Defect Injection Rate", KPISource.JIRA.name(),
			Arrays.asList("includeRCAForKPI14", "resolutionTypeForRejectionKPI14", "jiraDefectRejectionStatusKPI14",
					"defectPriorityKPI14", "jiraDefectInjectionIssueTypeKPI14", "jiraDefectCreatedStatusKPI14", "jiraDodKPI14",
					"thresholdValueKPI14", "jiraLabelsKPI14")),

	KPI111("Defect Density", KPISource.JIRA.name(),
			Arrays.asList("jiraDodQAKPI111", "jiraQAKPI111IssueType", "defectPriorityQAKPI111", "includeRCAForQAKPI111",
					"resolutionTypeForRejectionQAKPI111", "jiraDefectRejectionStatusQAKPI111", "thresholdValueKPI111",
					"jiraLabelsQAKPI111")),

	KPI127("Production Defects Ageing", KPISource.JIRA.name(),
			Arrays.asList("jiraStatusToConsiderKPI127", "thresholdValueKPI127", "productionDefectCustomField",
					"productionDefectValue", "productionDefectComponentValue", "productionDefectIdentifier")),

	KPI35("Defect Seepage Rate", KPISource.JIRA.name(),
			Arrays.asList("jiraIssueTypeKPI35", "resolutionTypeForRejectionKPI35", "jiraDefectRejectionStatusKPI35",
					"thresholdValueKPI35", "defectPriorityKPI35", "includeRCAForKPI35", "excludeUnlinkedDefects",
					"jiraBugRaisedByCustomField", "jiraBugRaisedByValue", "jiraBugRaisedByIdentification")),

	KPI133("Quality Status", KPISource.JIRA.name(),
			Arrays.asList("resolutionTypeForRejectionKPI133", "jiraDefectRejectionStatusKPI133", "defectPriorityKPI133",
					"includeRCAForKPI133", "jiraIterationCompletionStatusKPI133", "jiraItrQSIssueTypeKPI133",
					"jiraLabelsKPI133")),

	KPI126("Created vs Resolved defects", KPISource.JIRA.name(),
			Arrays.asList("jiraIssueDeliverdStatusKPI126", "thresholdValueKPI126")),

	KPI72("Commitment Reliability", KPISource.JIRA.name(),
			Arrays.asList("jiraIterationCompletionStatusKpi72", "jiraIterationIssuetypeKpi72", "thresholdValueKPI72")),

	KPI122("Closure Possible Today", KPISource.JIRA.name(), Arrays.asList("jiraIterationCompletionStatusKPI122",
			"jiraIterationIssuetypeKPI122", "jiraStatusForInProgressKPI122")),

	KPI145("Dev Completion Status", KPISource.JIRA.name(), Arrays.asList("jiraIterationCompletionStatusKPI145",
			"jiraIterationIssuetypeKPI145", "jiraStatusForInProgressKPI145", "jiraDevDoneStatusKPI145")),

	KPI75("Estimate vs Actual", KPISource.JIRA.name(),
			Arrays.asList("jiraIterationCompletionStatusKPI75", "jiraIterationIssuetypeKPI75", "jiraIssueTypeExcludeKPI75")),

	KPI124("Issue Hygiene", KPISource.JIRA.name(), Arrays.asList("jiraIterationCompletionStatusKPI124",
			"jiraIterationIssuetypeKPI124", "issueStatusExcluMissingWorkKPI124", "jiraIssueTypeExcludeKPI124")),

	KPI123("Issue Likely To Spill", KPISource.JIRA.name(), Arrays.asList("jiraIterationCompletionStatusKPI123",
			"jiraIterationIssuetypeKPI123", "jiraStatusForInProgressKPI123")),

	KPI125("Iteration Burnup", KPISource.JIRA.name(), Arrays.asList("jiraIterationCompletionStatusKPI125",
			"jiraIterationIssuetypeKPI125", "jiraStatusForInProgressKPI125")),

	KPI120("Iteration Commitment", KPISource.JIRA.name(),
			Arrays.asList("jiraIterationCompletionStatusKPI120", "jiraIterationIssuetypeKPI120", "jiraLabelsKPI120")),

	KPI128("Planned Work Status", KPISource.JIRA.name(), Arrays.asList("jiraIterationCompletionStatusKPI128",
			"jiraIterationIssuetypeKPI128", "jiraDevDoneStatusKPI128", "jiraStatusForInProgressKPI128")),

	KPI134("Unplanned Work Status", KPISource.JIRA.name(),
			Arrays.asList("jiraIterationCompletionStatusKPI134", "jiraIterationIssuetypeKPI134")),

	KPI191("Open Defect Rate", KPISource.JIRA.name(), Arrays.asList("resolutionTypeForRejectionKPI191",
			"jiraDefectRejectionStatusKPI191", "thresholdValueKPI191")),

	KPI119("Work Remaining", KPISource.JIRA.name(), Arrays.asList("jiraIterationCompletionStatusKPI119",
			"jiraIterationIssuetypeKPI119", "jiraDevDoneStatusKPI119", "jiraStatusForInProgressKPI119")),

	KPI131("Wastage", KPISource.JIRA.name(), Arrays.asList("jiraIncludeBlockedStatusKPI131", "jiraBlockedStatusKPI131",
			"jiraIterationCompletionStatusKPI131", "jiraIterationIssuetypeKPI131", "jiraWaitStatusKPI131")),

	KPI138("Backlog Readiness Efficiency", KPISource.JIRA.name(), Arrays.asList("jiraIterationCompletionStatusKPI138",
			"jiraIterationIssuetypeKPI138", "readyForDevelopmentStatusKPI138", "jiraIssueDeliverdStatusKPI138")),

	KPI137("Defect Reopen Rate (Backlog)", KPISource.JIRA.name(), Arrays.asList("jiraDefectClosedStatusKPI137")),

	KPI129("Unlinked Work Items", KPISource.JIRA.name(),
			Arrays.asList("jiraStoryIdentificationKPI129", "excludeStatusKpi129")),

	KPI139("Refinement Rejection Rate", KPISource.JIRA.name(), Arrays.asList("jiraAcceptedInRefinementKPI139",
			"jiraReadyForRefinementKPI139", "jiraRejectedInRefinementKPI139")),

	KPI148("Flow Load", KPISource.JIRA.name(), Arrays.asList("storyFirstStatusKPI148", "jiraStatusForQaKPI148",
			"jiraStatusForInProgressKPI148", "jiraIssueTypeNamesKPI148")),

	KPI146("Flow Distribution", KPISource.JIRA.name(), Arrays.asList("jiraIssueTypeNamesKPI146")),

	KPI151("Backlog Count By Status", KPISource.JIRA.name(), Arrays.asList("jiraDodKPI151",
			"jiraDefectRejectionStatusKPI151", "jiraLiveStatusKPI151", "jiraIssueTypeNamesKPI151")),

	KPI152("Backlog Count By Issue Type", KPISource.JIRA.name(), Arrays.asList("jiraDodKPI152",
			"jiraDefectRejectionStatusKPI152", "jiraLiveStatusKPI152", "jiraIssueTypeNamesKPI152")),

	KPI153("PI Predictability", KPISource.JIRA.name(),
			Arrays.asList("jiraIssueEpicTypeKPI153", "thresholdValueKPI153", "epicPlannedValue", "epicAchievedValue")),

	KPI154("Daily StandUp View", KPISource.JIRA.name(),
			Arrays.asList("jiraStatusStartDevelopmentKPI154", "jiraDevDoneStatusKPI154", "jiraQADoneStatusKPI154",
					"jiraIterationCompletionStatusKPI154", "jiraStatusForInProgressKPI154", "storyFirstStatusKPI154",
					"jiraOnHoldStatusKPI154")),

	// DTS-26123 start
	KPI155("Defect Count By Type", KPISource.JIRA.name(),
			Arrays.asList("jiraDefectRejectionStatusKPI155", "jiraDodKPI155", "jiraLiveStatusKPI155")),
	// DTS-26123 end
	KPI42("Regression Automation Coverage", KPISource.ZEPHYR.name(),
			Arrays.asList("uploadDataKPI42", "thresholdValueKPI42")),

	KPI16("Insprint Automation Coverage", KPISource.ZEPHYR.name(),
			Arrays.asList("uploadDataKPI16", "thresholdValueKPI16", "jiraTestAutomationIssueType")),

	KPI161("Iteration Readiness", KPISource.JIRA.name(), Arrays.asList("jiraIssueTypeNamesKPI161",
			"jiraStatusForNotRefinedKPI161", "jiraStatusForRefinedKPI161", "jiraStatusForInProgressKPI161")),

	KPI164("Scope Churn", KPISource.JIRA.name(), Arrays.asList("jiraStoryIdentificationKPI164", "thresholdValueKPI164")),

	KPI156("Lead Time For Change", KPISource.JIRA.name(), Arrays.asList("leadTimeConfigRepoTool", "toBranchForMRKPI156",
			"jiraDodKPI156", "jiraIssueTypeKPI156", "thresholdValueKPI156")),

	KPI163("Defect by Testing Phase", KPISource.JIRA.name(),
			Arrays.asList("jiraDodKPI163", "excludeRCAFromKPI163", "testingPhaseDefectCustomField",
					"testingPhaseDefectsIdentifier", "testingPhaseDefectValue", "testingPhaseDefectComponentValue")),

	KPI150("Release BurnUp", KPISource.JIRA.name(),
			Arrays.asList("startDateCountKPI150", "populateByDevDoneKPI150", "jiraDevDoneStatusKPI150", "releaseListKPI150")),

	KPI17("Unit Test Coverage", KPISource.SONAR.name(), Arrays.asList("thresholdValueKPI17")),

	KPI38("Sonar Violations", KPISource.SONAR.name(), Arrays.asList("thresholdValueKPI38")),

	KPI84("Mean Time To Merge", KPISource.BITBUCKET.name(), Arrays.asList("thresholdValueKPI84")),

	KPI11("Check-Ins & Merge Requests", KPISource.BITBUCKET.name(), Arrays.asList("thresholdValueKPI11")),

	KPI157("Check-Ins & Merge Requests (Developer) ", KPISource.BITBUCKET.name(), Arrays.asList("thresholdValueKPI157")),

	KPI158("Mean Time To Merge (Developer) ", KPISource.BITBUCKET.name(), Arrays.asList("thresholdValueKPI158")),

	KPI160("Pickup Time (Developer) ", KPISource.BITBUCKET.name(), Arrays.asList("thresholdValueKPI160")),

	KPI27("Sonar Tech Debt", KPISource.SONAR.name(), Arrays.asList("thresholdValueKPI27")),

	KPI166("Mean Time to Recover", KPISource.JIRA.name(),
			Arrays.asList("jiraStoryIdentificationKPI166", "jiraDodKPI166", "jiraProductionIncidentIdentification",
					"jiraProdIncidentRaisedByCustomField", "jiraProdIncidentRaisedByValue", "thresholdValueKPI166")),

	KPI170("Flow Efficiency", KPISource.JIRA.name(),
			Arrays.asList("jiraIssueWaitStateKPI170", "jiraIssueClosedStateKPI170", "thresholdValueKPI170")),

	KPI171("Cycle Time", KPISource.JIRA.name(),
			Arrays.asList("jiraIssueTypeKPI171", "jiraDorKPI171", "jiraDodKPI171", "jiraLiveStatusKPI171",
					"storyFirstStatusKPI171")), KPI162("PR Size", KPISource.BITBUCKET.name(),
							Collections.singletonList("thresholdValueKPI162")),

	KPI73("Release Frequency", KPISource.JIRA.name(), Collections.singletonList("thresholdValueKPI73")), KPI149(
			"Happiness Index", KPISource.JIRA.name(), Collections.singletonList("thresholdValueKPI149")), KPI113(
					"Value delivered (Cost of Delay)", KPISource.JIRA.name(),
					Arrays.asList("thresholdValueKPI113", "issueTypesToConsiderKpi113",
							"closedIssueStatusToConsiderKpi113")), KPI70("Test Execution and pass percentage",
									KPISource.ZEPHYR.name(), Collections.singletonList("thresholdValueKPI70")), KPI8("Code Build Time",
											KPISource.JENKINS.name(), Collections.singletonList("thresholdValueKPI8")), KPI172(
													"Build FREQUENCY", KPISource.JENKINS.name(),
													Collections.singletonList("thresholdValueKPI172")), KPI116("Change Failure Rate",
															KPISource.JENKINS.name(), Collections.singletonList("thresholdValueKPI116")), KPI118(
																	"Deployment Frequency", KPISource.JENKINS.name(),
																	Collections.singletonList("thresholdValueKPI118")), KPI144("Defect Count By Priority",
																			KPISource.JIRA.name(), Collections.singletonList("jiraDodKPI144")), KPI143(
																					"Defect Count By Assignee", KPISource.JIRA.name(),
																					Collections.singletonList("jiraDodKPI143")), KPI142("Defect Count By RCA",
																							KPISource.JIRA.name(),
																							Collections.singletonList("jiraDodKPI142")), KPI173("Rework Rate",
																									KPISource.BITBUCKET.name(),
																									Collections.singletonList("thresholdValueKPI173")), KPI176(
																											"Risks and Dependencies", KPISource.JIRA.name(),
																											Arrays.asList("jiraIterationCompletionStatusKPI176",
																													"jiraIssueDependencyTypeKPI176",
																													"jiraIssueRiskTypeKPI176")), KPI182("PR Success Rate",
																															KPISource.BITBUCKET.name(),
																															Collections
																																	.singletonList("thresholdValueKPI182")), KPI180(
																																			"Revert Rate", KPISource.BITBUCKET.name(),
																																			Collections.singletonList(
																																					"thresholdValueKPI180")), KPI181(
																																							"PR Decline Rate",
																																							KPISource.BITBUCKET.name(),
																																							Collections.singletonList(
																																									"thresholdValueKPI181")),

    KPI187("Late Refinement", KPISource.JIRA.name(), Arrays.asList( "jiraIssueTypeNamesKPI187", "jiraStatusKPI187")),
    KPI188("Next Sprint Late Refinement", KPISource.JIRA.name(), Arrays.asList("jiraRefinementCriteriaKPI188", "jiraRefinementByCustomFieldKPI188", "jiraRefinementMinLengthKPI188",
            "jiraRefinementKeywordsKPI188", "jiraIssueTypeNamesKPI188")),

	/** Kanban fieldMapping Enum starts * */
	KPI48("Net Open Ticket Count By Status", KPISource.JIRAKANBAN.name(),
			Arrays.asList("jiraLiveStatusKPI48", "ticketCountIssueTypeKPI48", "jiraTicketClosedStatusKPI48",
					"jiraTicketRejectedStatusKPI48", "thresholdValueKPI48")),

	KPI49("Ticket Velocity", KPISource.JIRAKANBAN.name(),
			Arrays.asList("jiraTicketVelocityIssueTypeKPI49", "ticketDeliveredStatusKPI49", "thresholdValueKPI49")),

	KPI50("Net Open Ticket Count By Priority", KPISource.JIRAKANBAN.name(),
			Arrays.asList("jiraLiveStatusKPI50", "ticketCountIssueTypeKPI50", "jiraTicketClosedStatusKPI50",
					"jiraTicketRejectedStatusKPI50", "thresholdValueKPI50")),

	KPI51("Net Open Ticket Count By Rca", KPISource.JIRAKANBAN.name(),
			Arrays.asList("jiraLiveStatusKPI51", "kanbanRCACountIssueTypeKPI51", "jiraTicketClosedStatusKPI51",
					"jiraTicketRejectedStatusKPI151", "thresholdValueKPI51")),

	KPI53("Lead Time Kanban", KPISource.JIRAKANBAN.name(), Arrays.asList("jiraLiveStatusKPI53",
			"jiraTicketClosedStatusKPI53", "kanbanCycleTimeIssueTypeKPI53", "jiraTicketTriagedStatusKPI53")),

	KPI54("Ticket Open Vs Close By Priority", KPISource.JIRAKANBAN.name(), Arrays.asList("ticketCountIssueTypeKPI54",
			"ticketCountIssueTypeKPI54", "jiraTicketClosedStatusKPI54", "thresholdValueKPI54")),

	KPI55("Ticket Open Vs Close By Type", KPISource.JIRAKANBAN.name(), Arrays.asList("ticketCountIssueTypeKPI55",
			"ticketCountIssueTypeKPI55", "jiraTicketClosedStatusKPI55", "thresholdValueKPI55")),

	KPI58("Team Capacity", KPISource.JIRAKANBAN.name(), Arrays.asList("thresholdValueKPI58")),

	KPI62("Unit Test Coverage (Kanban) ", KPISource.SONARKANBAN.name(), Arrays.asList("thresholdValueKPI62")),

	KPI63("Regression Automation Coverage", KPISource.ZEPHYRKANBAN.name(), Arrays.asList("thresholdValueKPI63")),

	KPI64("Sonar Violations (Kanban) ", KPISource.SONARKANBAN.name(), Arrays.asList("thresholdValueKPI64")),

	KPI65("Check-Ins & Merge Requests (Kanban) ", KPISource.BITBUCKETKANBAN.name(), Arrays.asList("thresholdValueKPI65")),

	KPI66("Code Build Time", KPISource.JENKINSKANBAN.name(), Arrays.asList("thresholdValueKPI66")),

	KPI67("Sonar Tech Debt (Kanban)", KPISource.SONARKANBAN.name(), Arrays.asList("thresholdValueKPI67")),

	KPI71("Test Execution and pass percentage", KPISource.ZEPHYRKANBAN.name(), Arrays.asList("thresholdValueKPI71")),

	KPI74("Release Frequency", KPISource.JIRAKANBAN.name(), Arrays.asList("thresholdValueKPI74")),

	KPI114("Value delivered (Cost of Delay)", KPISource.JIRAKANBAN.name(), Arrays.asList("thresholdValueKPI114")),

	KPI159("Number of Check-ins (Developer) ", KPISource.BITBUCKETKANBAN.name(), Arrays.asList("thresholdValueKPI159")),

	KPI184("Change Failure Rate", KPISource.JENKINSKANBAN.name(), Arrays.asList("thresholdValueKPI184")),

	KPI183("Deployment Frequency", KPISource.JENKINSKANBAN.name(), Arrays.asList("thresholdValueKPI183")),

	KPI190("Defect Reopen Rate", KPISource.JIRA.name(), Arrays.asList("defectReopenStatusKPI190",
			"resolutionTypeForRejectionKPI190", "jiraDefectRejectionStatusKPI190", "thresholdValueKPI190")),

	KPI997("Open Ticket Aging By Priority", KPISource.JIRAKANBAN.name(),
			Arrays.asList("jiraLiveStatusKPI997", "ticketCountIssueTypeKPI997", "jiraTicketClosedStatusKPI997",
					"jiraTicketRejectedStatusKPI997", "thresholdValueKPI997"));

	/** kanban field mapping enums end * */
	private List<String> fields;

	private String kpiName;
	private String kpiSource;

	FieldMappingEnum(String kpiName, String kpiSource, List<String> fields) {
		this.kpiName = kpiName;
		this.fields = fields;
		this.kpiSource = kpiSource;
	}

	public String getKpiSource() {
		return kpiSource;
	}

	public List<String> getFields() {
		return fields;
	}

	public String getKpiName() {
		return kpiName;
	}
}
