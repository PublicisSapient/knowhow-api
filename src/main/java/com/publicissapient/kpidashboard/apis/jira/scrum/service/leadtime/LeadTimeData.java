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
public class LeadTimeData {
	private List<JiraIssueCustomHistory> historyDataList;
	private List<JiraIssue> jiraIssueList;
	private Map<String, String> projectWiseLeadTimeConfigRepoTool;
	private Map<String, List<ScmMergeRequests>> projectWiseMergeRequestList;
	private Map<String, List<String>> projectWiseDodStatus;
	private Map<String, List<Deployment>> projectWiseDeploymentData;
	private Map<String, List<ScmCommits>> scmCommitsByProject;
}
