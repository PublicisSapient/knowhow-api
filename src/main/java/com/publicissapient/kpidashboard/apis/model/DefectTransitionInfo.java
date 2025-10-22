package com.publicissapient.kpidashboard.apis.model;

import org.joda.time.DateTime;

import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** This class stores defect transition information */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DefectTransitionInfo {
	private String priority;
	private DateTime closedDate;
	private DateTime reopenDate;
	private double reopenDuration;
	private JiraIssue defectJiraIssue;
}
