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

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.defect.rate;

import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.strategy.AbstractKpiCalculationStrategy;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.IterationKpiData;
import com.publicissapient.kpidashboard.apis.model.IterationKpiValue;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DefectRateNonTrendKpiServiceImpl extends AbstractKpiCalculationStrategy<List<IterationKpiValue>> {

	private static final List<String> DEFECT_KEYWORDS = List.of("fix", "bug", "repair", "defect");

	@Override
	public List<IterationKpiValue> calculateKpi(KpiRequest kpiRequest, List<ScmMergeRequests> mergeRequests, List<ScmCommits> commits, List<Tool> scmTools,
                                    List<RepoToolValidationData> validationDataList, Set<Assignee> assignees, String projectName) {
		List<IterationKpiValue> iterationKpiValueList = new ArrayList<>();


		scmTools.forEach(tool -> processToolData(tool, mergeRequests, assignees, iterationKpiValueList,
				validationDataList, kpiRequest, projectName));

		return iterationKpiValueList;
	}

	private void processToolData(Tool tool, List<ScmMergeRequests> mergeRequests, Set<Assignee> assignees,
			List<IterationKpiValue> iterationKpiValueList, List<RepoToolValidationData> validationDataList,
			KpiRequest kpiRequest, String projectName) {
		int dataPoints = kpiRequest.getXAxisDataPoints();
		LocalDateTime currentDate = DateUtil.getTodayTime();
		String duration = kpiRequest.getDuration();
		if (!DeveloperKpiHelper.isValidTool(tool)) {
			return;
		}

		String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);
		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;

		List<ScmMergeRequests> mergeRequestsForBranch = DeveloperKpiHelper.filterMergeRequestsForBranch(mergeRequests,
				tool);

		CustomDateRange periodRange = getCustomDateRange(currentDate, duration, dataPoints);
		List<ScmMergeRequests> currentMergedRequestsInRange = mergeRequestsForBranch.stream()
				.filter(request -> DateUtil.isWithinDateTimeRange(
						DateUtil.convertMillisToLocalDateTime(request.getUpdatedDate()), periodRange.getStartDateTime(),
						periodRange.getEndDateTime()))
				.toList();

		CustomDateRange prevPeriodRange = getCustomDateRange(periodRange.getStartDateTime(), duration, dataPoints);
		List<ScmMergeRequests> previousMergeRequestsInRange = mergeRequestsForBranch.stream()
				.filter(request -> DateUtil.isWithinDateTimeRange(
						DateUtil.convertMillisToLocalDateTime(request.getUpdatedDate()),
						prevPeriodRange.getStartDateTime(), prevPeriodRange.getEndDateTime()))
				.toList();

		long currentDefectMergeRequestsCount = currentMergedRequestsInRange.stream()
				.filter(mergeRequest -> mergeRequest.getTitle() != null && DEFECT_KEYWORDS.stream()
						.anyMatch(keyword -> mergeRequest.getTitle().toLowerCase().contains(keyword.toLowerCase())))
				.count();
		long currentTotalMergeRequests = currentMergedRequestsInRange.size();
		double currentDefectRate = currentTotalMergeRequests > 0
				? (currentDefectMergeRequestsCount * 100.0) / currentTotalMergeRequests
				: 0.0;

		long previousDefectMergeRequestsCount = previousMergeRequestsInRange.stream()
				.filter(mergeRequest -> mergeRequest.getTitle() != null && DEFECT_KEYWORDS.stream()
						.anyMatch(keyword -> mergeRequest.getTitle().toLowerCase().contains(keyword.toLowerCase())))
				.count();
		long previousTotalMergeRequests = previousMergeRequestsInRange.size();
		double previousDefectRate = previousTotalMergeRequests > 0
				? (previousDefectMergeRequestsCount * 100.0) / previousTotalMergeRequests
				: 0.0;

		double deviationRate = Math
				.round((currentDefectRate - previousDefectRate) / (currentDefectRate + previousDefectRate) * 100);

		iterationKpiValueList.add(new IterationKpiValue(branchName, Constant.AGGREGATED_VALUE, List
				.of(new IterationKpiData(overallKpiGroup, currentDefectRate, deviationRate, null, "hrs", "%", null))));

		Map<String, List<ScmMergeRequests>> userWiseMergeRequests = DeveloperKpiHelper
				.groupMergeRequestsByUser(mergeRequestsForBranch);

		validationDataList.addAll(prepareUserValidationData(periodRange, prevPeriodRange, userWiseMergeRequests,
				assignees, tool, projectName, iterationKpiValueList));
	}

	private List<RepoToolValidationData> prepareUserValidationData(CustomDateRange periodRange,
			CustomDateRange prevPeriodRange, Map<String, List<ScmMergeRequests>> userWiseMergeRequests,
			Set<Assignee> assignees, Tool tool, String projectName, List<IterationKpiValue> iterationKpiValueList) {
		return userWiseMergeRequests.entrySet().stream().map(entry -> {
			String userEmail = entry.getKey();
			List<ScmMergeRequests> userMergeRequests = entry.getValue();
			List<ScmMergeRequests> currentMergedRequestsInRange = userMergeRequests.stream()
					.filter(request -> DateUtil.isWithinDateTimeRange(
							DateUtil.convertMillisToLocalDateTime(request.getUpdatedDate()),
							periodRange.getStartDateTime(), periodRange.getEndDateTime()))
					.toList();

			List<ScmMergeRequests> previousMergeRequestsInRange = userMergeRequests.stream()
					.filter(request -> DateUtil.isWithinDateTimeRange(
							DateUtil.convertMillisToLocalDateTime(request.getUpdatedDate()),
							prevPeriodRange.getStartDateTime(), prevPeriodRange.getEndDateTime()))
					.toList();

			String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);

			long currentDefectMergeRequestsCount = currentMergedRequestsInRange.stream()
					.filter(mergeRequest -> mergeRequest.getTitle() != null && DEFECT_KEYWORDS.stream()
							.anyMatch(keyword -> mergeRequest.getTitle().toLowerCase().contains(keyword.toLowerCase())))
					.count();
			long currentTotalMergeRequests = currentMergedRequestsInRange.size();
			double currentDefectRate = currentTotalMergeRequests > 0
					? (currentDefectMergeRequestsCount * 100.0) / currentTotalMergeRequests
					: 0.0;

			long previousDefectMergeRequestsCount = previousMergeRequestsInRange.stream()
					.filter(mergeRequest -> mergeRequest.getTitle() != null && DEFECT_KEYWORDS.stream()
							.anyMatch(keyword -> mergeRequest.getTitle().toLowerCase().contains(keyword.toLowerCase())))
					.count();
			long previousTotalMergeRequests = previousMergeRequestsInRange.size();
			double previousDefectRate = previousTotalMergeRequests > 0
					? (previousDefectMergeRequestsCount * 100.0) / previousTotalMergeRequests
					: 0.0;

			double deviationRate = Math
					.round((currentDefectRate - previousDefectRate) / (currentDefectRate + previousDefectRate) * 100);

			String userKpiGroup = DeveloperKpiHelper.getBranchSubFilter(tool, projectName) + "#" + developerName;
			iterationKpiValueList.add(new IterationKpiValue(DeveloperKpiHelper.getBranchSubFilter(tool, projectName),
					developerName, List.of(new IterationKpiData(userKpiGroup, currentDefectRate, deviationRate, null,
							"hrs", "%", null))));

			return createValidationData(projectName, tool, developerName, null, currentDefectRate,
					currentDefectMergeRequestsCount, currentTotalMergeRequests);
		}).toList();
	}

	@Override
	public String getStrategyType() {
		return "DEFECT_RATE_NON_TREND";
	}

	private RepoToolValidationData createValidationData(String projectName, Tool tool, String developerName,
			String dateLabel, double defectRate, long defectMrCount, long mrCount) {
		RepoToolValidationData validationData = new RepoToolValidationData();
		validationData.setProjectName(projectName);
		validationData.setBranchName(tool.getBranch());
		validationData.setRepoUrl(tool.getRepositoryName() != null ? tool.getRepositoryName() : tool.getRepoSlug());
		validationData.setDeveloperName(developerName);
		validationData.setDate(dateLabel);
		validationData.setDefectRate(defectRate);
		validationData.setKpiPRs(defectMrCount);
		validationData.setMrCount(mrCount);
		return validationData;
	}

}
