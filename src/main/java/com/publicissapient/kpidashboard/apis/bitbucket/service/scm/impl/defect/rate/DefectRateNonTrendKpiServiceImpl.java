package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.defect.rate;

import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.strategy.KpiCalculationStrategy;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;

import java.util.List;
import java.util.Set;

public class DefectRateNonTrendKpiServiceImpl implements KpiCalculationStrategy {
	@Override
	public Object getTrendValueList(KpiRequest kpiRequest, List<ScmMergeRequests> mergeRequests, List<Tool> scmTools,
			List<RepoToolValidationData> validationDataList, Set<Assignee> assignees, String projectName) {
		return null;
	}
}
