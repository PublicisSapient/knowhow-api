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

package com.publicissapient.kpidashboard.apis.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** object used to bind kpi's value */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IssueKpiModalValue implements Serializable {
	private static final long serialVersionUID = -6376203644006393547L;

	@JsonProperty("Issue Id")
	private String issueId;

	@JsonProperty("Issue URL")
	private String issueURL;

	@JsonProperty("Issue Description")
	private String description;

	@JsonProperty("Issue Status")
	private String issueStatus;

	@JsonProperty("Issue Type")
	private String issueType;

	@JsonProperty("Size(story point/hours)")
	private String issueSize;

	@JsonProperty("Remaining Hours")
	private Integer remainingTime;

	@JsonProperty("Logged Work")
	private String timeSpentInMinutes;

	@JsonProperty("Original Estimate")
	private String originalEstimateMinutes;

	@JsonProperty("Priority")
	private String priority;

	@JsonProperty("Due Date")
	private String dueDate;

	private Integer delay;

	@JsonProperty("Remaining Estimate")
	private String remainingEstimateMinutes;

	@JsonProperty("Blocked Time")
	private String blockedTime;

	@JsonProperty("Wait Time")
	private String waitTime;

	@JsonProperty("Total Wastage")
	private String wastage;

	@JsonProperty("Actual Completion Date")
	private String actualCompletionDate;

	@JsonProperty("Remaining Days")
	private String remainingTimeInDays;

	@JsonProperty("Predicted Completion Date")
	private String predictedCompletionDate;

	@JsonProperty("Potential Delay(in days)")
	private String potentialDelay;

	@JsonProperty("Dev Delay(in days)")
	private String devDelayInDays;

	@JsonProperty("Issue Priority")
	private String issuePriority;

	@JsonProperty("Linked Stories")
	private Map<String, String> linkedStories;

	@JsonProperty("Linked Stories Size")
	private String linkedStoriesSize;

	@JsonProperty("Delay(in days)")
	private String delayInDays;

	@JsonProperty("Actual Start Date")
	private String actualStartDate;

	@JsonProperty("Dev Completion Date")
	private String devCompletionDate;

	@JsonProperty("Dev Due Date")
	private String devDueDate;

	private String marker;

	@JsonProperty("Assignee")
	private String assignee;

	@JsonProperty("First Time Pass")
	private String firstTimePass;

	@JsonProperty("Linked Defect")
	private Map<String, String> linkedDefefect;

	@JsonProperty("Defect Priority")
	private Map<String, String> linkedDefefectPriority;

	// Added for Defect Reopen Rate - submohan1
	@JsonProperty("Closed Date")
	private String closedDate;

	@JsonProperty("Reopen Date")
	private String reopenDate;

	@JsonProperty("Time taken to reopen")
	private String durationToReopen;

	// end for Defect Reopen Rate - submohan1

	@JsonProperty("Change Date")
	private String changeDate;

	@JsonProperty("Labels")
	private List<String> labels;

	@JsonProperty("Created Date")
	private String createdDate;

	@JsonProperty("Root Cause List")
	private List<String> rootCauseList;

	@JsonProperty("Owner Full Name")
	private List<String> ownersFullName;

	@JsonProperty("Sprint Name")
	private String sprintName;

	@JsonProperty("Resolution")
	private String resolution;

	@JsonProperty("Release Name")
	private String releaseName;

	@JsonProperty("Updated Date")
	private String updatedDate;

	@JsonProperty("Lead Time (In Days)")
	private String leadTime;

	@JsonProperty("DIR")
	private String defectInjectRate;

	@JsonProperty("Defect Density")
	private String defectDensity;

	@JsonProperty("Test-Completed")
	private String testCompletedInTime;

	@JsonProperty("Dev-Completion-Date")
	private String devCompletionDateInTime;

	@JsonProperty("Actual-Completion-Date")
	private String actualCompletionDateInTime;

	@JsonProperty("Actual-Start-Date")
	private String actualStartDateInTime;

	@JsonProperty("Squad")
	private List<String> squads;

	@JsonProperty("Intake to DOR")
	private String intakeToDOR;

	@JsonProperty("Testing Phase")
	private List<String> testPhaseList;

	@JsonProperty("DOR to DOD")
	private String dorToDod;

	@JsonProperty("DOD to Live")
	private String dodToLive;

	@JsonProperty("DOR Date")
	private String dorDate;

	@JsonProperty("DOD Date")
	private String dodDate;

	@JsonProperty("Live Date")
	private String liveDate;

	@JsonProperty("Category")
	private List<String> category;

	@JsonProperty("Created during Iteration")
	private String createdDuringIteration;

	private Double value;
	private Integer issueBlockedTime;
	private Integer issueWaitTime;
	private Integer estimateTime;
	private Integer actualTime;
	private Map<String, List<String>> category2;
	private Map<String, Integer> categoryWiseDelay;

	@JsonProperty("Date")
	private String date;

    @JsonProperty("Un-Refined")
    private String unRefined;

	private List<Integer> scopeDuration;

}
