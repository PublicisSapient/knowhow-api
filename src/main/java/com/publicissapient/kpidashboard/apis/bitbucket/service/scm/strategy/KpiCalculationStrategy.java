package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.strategy;

import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;

import java.util.List;
import java.util.Set;

public interface KpiCalculationStrategy<T> {
    T calculateKpi(KpiRequest kpiRequest, List<ScmMergeRequests> mergeRequests, 
                   List<ScmCommits> commits, List<Tool> scmTools,
                   List<RepoToolValidationData> validationDataList, 
                   Set<Assignee> assignees, String projectName);
    
    String getStrategyType();
}