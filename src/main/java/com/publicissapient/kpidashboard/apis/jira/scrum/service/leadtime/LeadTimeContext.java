package com.publicissapient.kpidashboard.apis.jira.scrum.service.leadtime;

import java.util.List;
import java.util.Map;

import com.publicissapient.kpidashboard.common.model.application.Deployment;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LeadTimeContext {
	private String weekOrMonth;
	private List<JiraIssueCustomHistory> jiraIssueHistoryDataList;
	private Map<String, JiraIssue> jiraIssueMap;
	private List<String> dodStatus;
	private List<ScmMergeRequests> mergeRequestList;
	private List<Deployment> deploymentList;
	private List<ScmCommits> scmCommitList;
}
