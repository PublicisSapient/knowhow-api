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

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.defect.rate;

import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.strategy.AbstractKpiCalculationStrategy;
import org.springframework.stereotype.Component;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service implementation for calculating defect rate KPI in trend mode.
 * Analyzes merge requests over time to track the percentage of defect-related
 * changes by identifying keywords in merge request titles.
 *
 * @author KnowHOW Team
 */
@Component
public class DefectRateTrendKpiServiceImpl extends AbstractKpiCalculationStrategy<Map<String, List<DataCount>>> {

	private static final String MR_COUNT = "No of PRs";
	private static final List<String> DEFECT_KEYWORDS = List.of("fix", "bug", "repair", "defect");
	private static final double PERCENTAGE_MULTIPLIER = 100.0;

	/**
	 * Calculates defect rate KPI trend over multiple time periods for all SCM
	 * tools.
	 *
	 * @param kpiRequest
	 *            the KPI request containing duration and data points
	 * @param mergeRequests
	 *            list of merge requests to analyze
	 * @param commits
	 *            list of commits (not used in this implementation)
	 * @param scmTools
	 *            list of SCM tools to process
	 * @param validationDataList
	 *            list to populate with validation data
	 * @param assignees
	 *            set of assignees for developer name resolution
	 * @param projectName
	 *            name of the project
	 * @return map of KPI trend data grouped by branch and developer
	 */
	@Override
	public Map<String, List<DataCount>> calculateKpi(KpiRequest kpiRequest, List<ScmMergeRequests> mergeRequests,
			List<ScmCommits> commits, List<Tool> scmTools, List<RepoToolValidationData> validationDataList,
			Set<Assignee> assignees, String projectName) {
		Map<String, List<DataCount>> kpiTrendDataByGroup = new LinkedHashMap<>();
		int dataPoints = kpiRequest.getXAxisDataPoints();
		LocalDateTime currentDate = DateUtil.getTodayTime();
		String duration = kpiRequest.getDuration();

		for (int i = 0; i < dataPoints; i++) {
			CustomDateRange periodRange = KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration);
			String dateLabel = KpiHelperService.getDateRange(periodRange, duration);
			List<ScmMergeRequests> filteredMergeRequests = filterMergeRequestsByDateRange(mergeRequests, periodRange);

			scmTools.forEach(tool -> processToolData(tool, filteredMergeRequests, assignees, kpiTrendDataByGroup,
					validationDataList, dateLabel, projectName));

			currentDate = DeveloperKpiHelper.getNextRangeDate(duration, currentDate);
		}
		return kpiTrendDataByGroup;
	}

	/**
	 * Processes data for a single SCM tool and calculates aggregated defect rate
	 * for a time period.
	 *
	 * @param tool
	 *            the SCM tool to process
	 * @param mergeRequests
	 *            merge requests for the time period
	 * @param assignees
	 *            set of assignees
	 * @param kpiTrendDataByGroup
	 *            map to add trend data
	 * @param validationDataList
	 *            list to add validation data
	 * @param dateLabel
	 *            label for the time period
	 * @param projectName
	 *            name of the project
	 */
	private void processToolData(Tool tool, List<ScmMergeRequests> mergeRequests, Set<Assignee> assignees,
			Map<String, List<DataCount>> kpiTrendDataByGroup, List<RepoToolValidationData> validationDataList,
			String dateLabel, String projectName) {
		if (!DeveloperKpiHelper.isValidTool(tool)) {
			return;
		}

		String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);
		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;

		List<ScmMergeRequests> mergeRequestsForBranch = DeveloperKpiHelper.filterMergeRequestsForBranch(mergeRequests,
				tool);

		long defectMergeRequestsCount = countDefectMergeRequests(mergeRequestsForBranch);
		long totalMergeRequests = mergeRequestsForBranch.size();
		double defectRate = calculateDefectRate(defectMergeRequestsCount, totalMergeRequests);

		DeveloperKpiHelper.setDataCount(projectName, dateLabel, overallKpiGroup, defectRate, new HashMap<>(),
				kpiTrendDataByGroup);

		Map<String, List<ScmMergeRequests>> userWiseMergeRequests = DeveloperKpiHelper
				.groupMergeRequestsByUser(mergeRequestsForBranch);
		validationDataList.addAll(prepareUserValidationData(userWiseMergeRequests, assignees, tool, projectName,
				dateLabel, kpiTrendDataByGroup));
	}

	/**
	 * Prepares user-level validation data by calculating defect rate per developer.
	 *
	 * @param userWiseMergeRequests
	 *            map of user email to their merge requests
	 * @param assignees
	 *            set of assignees
	 * @param tool
	 *            the SCM tool
	 * @param projectName
	 *            name of the project
	 * @param dateLabel
	 *            label for the time period
	 * @param kpiTrendDataByGroup
	 *            map to add trend data
	 * @return list of validation data for each user
	 */
	private List<RepoToolValidationData> prepareUserValidationData(
			Map<String, List<ScmMergeRequests>> userWiseMergeRequests, Set<Assignee> assignees, Tool tool,
			String projectName, String dateLabel, Map<String, List<DataCount>> kpiTrendDataByGroup) {
		return userWiseMergeRequests.entrySet().stream().map(entry -> {
			String userEmail = entry.getKey();
			List<ScmMergeRequests> userMergeRequests = entry.getValue();

			String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);
			long defectMergeRequestsCount = countDefectMergeRequests(userMergeRequests);
			long totalMergeRequests = userMergeRequests.size();
			double defectRate = calculateDefectRate(defectMergeRequestsCount, totalMergeRequests);

			String userKpiGroup = DeveloperKpiHelper.getBranchSubFilter(tool, projectName) + "#" + developerName;
			DeveloperKpiHelper.setDataCount(projectName, dateLabel, userKpiGroup, defectRate,
					Map.of(MR_COUNT, defectMergeRequestsCount), kpiTrendDataByGroup);

			return createValidationData(projectName, tool, developerName, dateLabel, defectRate,
					defectMergeRequestsCount, totalMergeRequests);
		}).toList();
	}

	/**
	 * Filters merge requests within the specified date range.
	 *
	 * @param mergeRequests
	 *            list of merge requests to filter
	 * @param dateRange
	 *            date range to filter by
	 * @return filtered list of merge requests
	 */
	private List<ScmMergeRequests> filterMergeRequestsByDateRange(List<ScmMergeRequests> mergeRequests,
			CustomDateRange dateRange) {
		return mergeRequests.stream()
				.filter(request -> DateUtil.isWithinDateTimeRange(
						DateUtil.convertMillisToLocalDateTime(request.getUpdatedDate()), dateRange.getStartDateTime(),
						dateRange.getEndDateTime()))
				.toList();
	}

	/**
	 * Checks if a merge request is defect-related based on title keywords.
	 *
	 * @param mergeRequest
	 *            the merge request to check
	 * @return true if the merge request is defect-related
	 */
	private boolean isDefectRelated(ScmMergeRequests mergeRequest) {
		if (mergeRequest.getTitle() == null) {
			return false;
		}
		String titleLower = mergeRequest.getTitle().toLowerCase();
		return DEFECT_KEYWORDS.stream().anyMatch(titleLower::contains);
	}

	/**
	 * Counts defect-related merge requests in the given list.
	 *
	 * @param mergeRequests
	 *            list of merge requests
	 * @return count of defect-related merge requests
	 */
	private long countDefectMergeRequests(List<ScmMergeRequests> mergeRequests) {
		return mergeRequests.stream().filter(this::isDefectRelated).count();
	}

	/**
	 * Calculates defect rate as a percentage.
	 *
	 * @param defectCount
	 *            number of defect-related merge requests
	 * @param totalCount
	 *            total number of merge requests
	 * @return defect rate percentage
	 */
	private double calculateDefectRate(long defectCount, long totalCount) {
		return totalCount > 0 ? (defectCount * PERCENTAGE_MULTIPLIER) / totalCount : 0.0;
	}

	@Override
	public String getStrategyType() {
		return "DEFECT_RATE_TREND";
	}

	/**
	 * Creates validation data object for a developer.
	 *
	 * @param projectName
	 *            name of the project
	 * @param tool
	 *            the SCM tool
	 * @param developerName
	 *            name of the developer
	 * @param dateLabel
	 *            label for the time period
	 * @param defectRate
	 *            calculated defect rate
	 * @param defectMrCount
	 *            count of defect merge requests
	 * @param mrCount
	 *            total merge request count
	 * @return populated validation data object
	 */
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
