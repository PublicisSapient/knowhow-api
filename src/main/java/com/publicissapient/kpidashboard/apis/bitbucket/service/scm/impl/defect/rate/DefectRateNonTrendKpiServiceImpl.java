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

/**
 * Service implementation for calculating defect rate KPI in non-trend mode.
 * Analyzes merge requests to determine the percentage of defect-related changes
 * by identifying keywords in merge request titles.
 *
 * @author KnowHOW Team
 */
@Component
public class DefectRateNonTrendKpiServiceImpl extends AbstractKpiCalculationStrategy<List<IterationKpiValue>> {

	private static final List<String> DEFECT_KEYWORDS = List.of("fix", "bug", "repair", "defect");
	private static final double PERCENTAGE_MULTIPLIER = 100.0;

	/**
	 * Calculates defect rate KPI for all SCM tools.
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
	 * @return list of iteration KPI values
	 */
	@Override
	public List<IterationKpiValue> calculateKpi(KpiRequest kpiRequest, List<ScmMergeRequests> mergeRequests,
			List<ScmCommits> commits, List<Tool> scmTools, List<RepoToolValidationData> validationDataList,
			Set<Assignee> assignees, String projectName) {
		List<IterationKpiValue> iterationKpiValueList = new ArrayList<>();

		scmTools.forEach(tool -> processToolData(tool, mergeRequests, assignees, iterationKpiValueList,
				validationDataList, kpiRequest, projectName));

		return iterationKpiValueList;
	}

	/**
	 * Processes data for a single SCM tool and calculates aggregated defect rate.
	 *
	 * @param tool
	 *            the SCM tool to process
	 * @param mergeRequests
	 *            all merge requests
	 * @param assignees
	 *            set of assignees
	 * @param iterationKpiValueList
	 *            list to add KPI values
	 * @param validationDataList
	 *            list to add validation data
	 * @param kpiRequest
	 *            the KPI request
	 * @param projectName
	 *            name of the project
	 */
	private void processToolData(Tool tool, List<ScmMergeRequests> mergeRequests, Set<Assignee> assignees,
			List<IterationKpiValue> iterationKpiValueList, List<RepoToolValidationData> validationDataList,
			KpiRequest kpiRequest, String projectName) {
		if (!DeveloperKpiHelper.isValidTool(tool)) {
			return;
		}

		int dataPoints = kpiRequest.getXAxisDataPoints();
		LocalDateTime currentDate = DateUtil.getTodayTime();
		String duration = kpiRequest.getDuration();
		String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);

		List<ScmMergeRequests> mergeRequestsForBranch = DeveloperKpiHelper.filterMergeRequestsForBranch(mergeRequests,
				tool);

		CustomDateRange periodRange = getCustomDateRange(currentDate, duration, dataPoints);
		CustomDateRange prevPeriodRange = getCustomDateRange(periodRange.getStartDateTime(), duration, dataPoints);

		List<ScmMergeRequests> currentMergedRequestsInRange = filterMergeRequestsByDateRange(mergeRequestsForBranch,
				periodRange);
		List<ScmMergeRequests> previousMergeRequestsInRange = filterMergeRequestsByDateRange(mergeRequestsForBranch,
				prevPeriodRange);

		double currentDefectRate = calculateDefectRate(currentMergedRequestsInRange);
		double previousDefectRate = calculateDefectRate(previousMergeRequestsInRange);
		double deviationRate = calculateDeviationRate(currentDefectRate, previousDefectRate);

		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;
		iterationKpiValueList.add(new IterationKpiValue(branchName, Constant.AGGREGATED_VALUE, List
				.of(new IterationKpiData(overallKpiGroup, currentDefectRate, deviationRate, null, "hrs", "%", null))));

		Map<String, List<ScmMergeRequests>> userWiseMergeRequests = DeveloperKpiHelper
				.groupMergeRequestsByUser(mergeRequestsForBranch);
		validationDataList.addAll(prepareUserValidationData(periodRange, prevPeriodRange, userWiseMergeRequests,
				assignees, tool, projectName, iterationKpiValueList));
	}

	/**
	 * Prepares user-level validation data by calculating defect rate per developer.
	 *
	 * @param periodRange
	 *            current period date range
	 * @param prevPeriodRange
	 *            previous period date range
	 * @param userWiseMergeRequests
	 *            map of user email to their merge requests
	 * @param assignees
	 *            set of assignees
	 * @param tool
	 *            the SCM tool
	 * @param projectName
	 *            name of the project
	 * @param iterationKpiValueList
	 *            list to add user-level KPI values
	 * @return list of validation data for each user
	 */
	private List<RepoToolValidationData> prepareUserValidationData(CustomDateRange periodRange,
			CustomDateRange prevPeriodRange, Map<String, List<ScmMergeRequests>> userWiseMergeRequests,
			Set<Assignee> assignees, Tool tool, String projectName, List<IterationKpiValue> iterationKpiValueList) {
		return userWiseMergeRequests.entrySet().stream().map(entry -> {
			String userEmail = entry.getKey();
			List<ScmMergeRequests> userMergeRequests = entry.getValue();

			List<ScmMergeRequests> currentMergedRequestsInRange = filterMergeRequestsByDateRange(userMergeRequests,
					periodRange);
			List<ScmMergeRequests> previousMergeRequestsInRange = filterMergeRequestsByDateRange(userMergeRequests,
					prevPeriodRange);

			String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);

			long currentDefectMergeRequestsCount = countDefectMergeRequests(currentMergedRequestsInRange);
			long currentTotalMergeRequests = currentMergedRequestsInRange.size();
			double currentDefectRate = calculateDefectRate(currentDefectMergeRequestsCount, currentTotalMergeRequests);

			double previousDefectRate = calculateDefectRate(previousMergeRequestsInRange);
			double deviationRate = calculateDeviationRate(currentDefectRate, previousDefectRate);

			String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);
			String userKpiGroup = branchName + "#" + developerName;
			iterationKpiValueList.add(new IterationKpiValue(branchName, developerName, List
					.of(new IterationKpiData(userKpiGroup, currentDefectRate, deviationRate, null, "hrs", "%", null))));

			return createValidationData(projectName, tool, developerName, null, currentDefectRate,
					currentDefectMergeRequestsCount, currentTotalMergeRequests);
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
	 * @param mergeRequests
	 *            list of merge requests
	 * @return defect rate percentage
	 */
	private double calculateDefectRate(List<ScmMergeRequests> mergeRequests) {
		long defectCount = countDefectMergeRequests(mergeRequests);
		long totalCount = mergeRequests.size();
		return calculateDefectRate(defectCount, totalCount);
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

	/**
	 * Calculates deviation rate between current and previous periods.
	 *
	 * @param currentRate
	 *            current period defect rate
	 * @param previousRate
	 *            previous period defect rate
	 * @return deviation rate percentage
	 */
	private double calculateDeviationRate(double currentRate, double previousRate) {
		double sum = currentRate + previousRate;
		return sum > 0 ? Math.round((currentRate - previousRate) / sum * PERCENTAGE_MULTIPLIER) : 0.0;
	}

	@Override
	public String getStrategyType() {
		return "DEFECT_RATE_NON_TREND";
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
	 *            date label (can be null)
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
