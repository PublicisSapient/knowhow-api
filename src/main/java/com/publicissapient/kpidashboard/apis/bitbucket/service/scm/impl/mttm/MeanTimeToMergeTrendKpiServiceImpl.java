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

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.mttm;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.strategy.AbstractKpiCalculationStrategy;
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

/**
 * Service implementation for calculating Mean Time To Merge KPI in trend mode. Tracks mean time to
 * merge over multiple time periods to show trends.
 */
@Component
public class MeanTimeToMergeTrendKpiServiceImpl
		extends AbstractKpiCalculationStrategy<Map<String, List<DataCount>>> {

	private static final long MILLIS_PER_SECOND = 1000L;

	/**
	 * Calculates Mean Time To Merge KPI trend data across multiple time periods.
	 *
	 * @param kpiRequest the KPI request containing filters and parameters
	 * @param mergeRequests list of merge requests
	 * @param commits list of SCM commits (unused in this implementation)
	 * @param scmTools list of SCM tools to process
	 * @param validationDataList list to populate with validation data
	 * @param assignees set of assignees for developer mapping
	 * @param projectName name of the project
	 * @return map of KPI group to trend data
	 */
	@Override
	public Map<String, List<DataCount>> calculateKpi(
			KpiRequest kpiRequest,
			List<ScmMergeRequests> mergeRequests,
			List<ScmCommits> commits,
			List<Tool> scmTools,
			List<RepoToolValidationData> validationDataList,
			Set<Assignee> assignees,
			String projectName) {
		Map<String, List<DataCount>> kpiTrendDataByGroup = new LinkedHashMap<>();
		int dataPoints = kpiRequest.getXAxisDataPoints();
		LocalDateTime currentDate = DateUtil.getTodayTime();
		String duration = kpiRequest.getDuration();

		for (int i = 0; i < dataPoints; i++) {
			CustomDateRange periodRange =
					KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration);
			String dateLabel = KpiHelperService.getDateRange(periodRange, duration);
			List<ScmMergeRequests> mergedRequestsInRange =
					filterMergedRequestsByDateRange(mergeRequests, periodRange);

			scmTools.forEach(
					tool ->
							processToolData(
									tool,
									mergedRequestsInRange,
									assignees,
									kpiTrendDataByGroup,
									validationDataList,
									dateLabel,
									projectName));

			currentDate = DeveloperKpiHelper.getNextRangeDate(duration, currentDate);
		}
		return kpiTrendDataByGroup;
	}

	/**
	 * Filters merged requests within the specified date range.
	 *
	 * @param mergeRequests list of merge requests to filter
	 * @param dateRange date range to filter by
	 * @return filtered list of merged requests
	 */
	private List<ScmMergeRequests> filterMergedRequestsByDateRange(
			List<ScmMergeRequests> mergeRequests, CustomDateRange dateRange) {
		return mergeRequests.stream()
				.filter(request -> request.getMergedAt() != null)
				.filter(
						request ->
								DateUtil.isWithinDateTimeRange(
										request.getMergedAt(),
										dateRange.getStartDateTime(),
										dateRange.getEndDateTime()))
				.toList();
	}

	/**
	 * Processes data for a single SCM tool and calculates mean time to merge.
	 *
	 * @param tool the SCM tool to process
	 * @param mergeRequests list of merge requests for the period
	 * @param assignees set of assignees
	 * @param kpiTrendDataByGroup map to populate with trend data
	 * @param validationDataList list to populate with validation data
	 * @param dateLabel label for the time period
	 * @param projectName name of the project
	 */
	private void processToolData(
			Tool tool,
			List<ScmMergeRequests> mergeRequests,
			Set<Assignee> assignees,
			Map<String, List<DataCount>> kpiTrendDataByGroup,
			List<RepoToolValidationData> validationDataList,
			String dateLabel,
			String projectName) {
		if (!DeveloperKpiHelper.isValidTool(tool)) {
			return;
		}

		String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);
		List<ScmMergeRequests> mergeRequestsForBranch =
				DeveloperKpiHelper.filterMergeRequestsForBranch(mergeRequests, tool);

		double meanTimeToMergeSeconds = calculateMeanTimeToMerge(mergeRequestsForBranch);
		long meanTimeToMergeHours =
				KpiHelperService.convertMilliSecondsToHours(meanTimeToMergeSeconds * MILLIS_PER_SECOND);

		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;
		DeveloperKpiHelper.setDataCount(
				projectName,
				dateLabel,
				overallKpiGroup,
				meanTimeToMergeHours,
				new HashMap<>(),
				kpiTrendDataByGroup);

		Map<String, List<ScmMergeRequests>> userWiseMergeRequests =
				DeveloperKpiHelper.groupMergeRequestsByUser(mergeRequestsForBranch);
		validationDataList.addAll(
				prepareUserValidationData(
						userWiseMergeRequests, assignees, tool, projectName, dateLabel, kpiTrendDataByGroup));
	}

	/**
	 * Prepares validation data for each user/developer.
	 *
	 * @param userWiseMergeRequests map of user email to their merge requests
	 * @param assignees set of assignees
	 * @param tool the SCM tool
	 * @param projectName name of the project
	 * @param dateLabel label for the time period
	 * @param kpiTrendDataByGroup map to populate with user-level trend data
	 * @return list of validation data for all users
	 */
	private List<RepoToolValidationData> prepareUserValidationData(
			Map<String, List<ScmMergeRequests>> userWiseMergeRequests,
			Set<Assignee> assignees,
			Tool tool,
			String projectName,
			String dateLabel,
			Map<String, List<DataCount>> kpiTrendDataByGroup) {
		String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);
		return userWiseMergeRequests.entrySet().stream()
				.map(
						entry -> {
							String userEmail = entry.getKey();
							List<ScmMergeRequests> userMergeRequests = entry.getValue();
							String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);

							double userAverageSeconds = calculateMeanTimeToMerge(userMergeRequests);
							Long userAverageHrs =
									KpiHelperService.convertMilliSecondsToHours(
											userAverageSeconds * MILLIS_PER_SECOND);

							String userKpiGroup = branchName + "#" + developerName;
							DeveloperKpiHelper.setDataCount(
									projectName,
									dateLabel,
									userKpiGroup,
									userAverageHrs,
									new HashMap<>(),
									kpiTrendDataByGroup);

							return userMergeRequests.stream()
									.filter(mr -> mr.getCreatedDate() != null && mr.getMergedAt() != null)
									.map(mr -> createValidationData(projectName, tool, developerName, dateLabel, mr))
									.toList();
						})
				.flatMap(List::stream)
				.toList();
	}

	/**
	 * Creates validation data object for a merge request.
	 *
	 * @param projectName name of the project
	 * @param tool the SCM tool
	 * @param developerName name of the developer
	 * @param dateLabel label for the time period
	 * @param mergeRequest the merge request
	 * @return populated validation data object
	 */
	private RepoToolValidationData createValidationData(
			String projectName,
			Tool tool,
			String developerName,
			String dateLabel,
			ScmMergeRequests mergeRequest) {
		RepoToolValidationData validationData = new RepoToolValidationData();
		validationData.setProjectName(projectName);
		validationData.setBranchName(tool.getBranch());
		validationData.setRepoUrl(
				tool.getRepositoryName() != null ? tool.getRepositoryName() : tool.getRepoSlug());
		validationData.setDeveloperName(developerName);
		validationData.setDate(dateLabel);

		LocalDateTime createdDateTime =
				DateUtil.convertMillisToLocalDateTime(mergeRequest.getCreatedDate());
		long timeToMergeSeconds =
				ChronoUnit.SECONDS.between(createdDateTime, mergeRequest.getMergedAt());
		validationData.setMeanTimeToMerge(
				KpiHelperService.convertMilliSecondsToHours(timeToMergeSeconds * MILLIS_PER_SECOND));

		validationData.setMergeRequestUrl(mergeRequest.getMergeRequestUrl());
		LocalDateTime createdDateTimeUTC = DateUtil.localDateTimeToUTC(createdDateTime);
		LocalDateTime mergedAtUTC = DateUtil.localDateTimeToUTC(mergeRequest.getMergedAt());

		validationData.setPrRaisedTime(DateUtil.tranformUTCLocalTimeToZFormat(createdDateTimeUTC));
		validationData.setPrActivityTime(DateUtil.tranformUTCLocalTimeToZFormat(mergedAtUTC));

		return validationData;
	}

	/**
	 * Returns the strategy type identifier.
	 *
	 * @return strategy type string
	 */
	@Override
	public String getStrategyType() {
		return "REPO_TOOL_MEAN_TIME_TO_MERGE_TREND";
	}

	/**
	 * Calculates the mean time to merge for a list of merge requests.
	 *
	 * @param mergeRequests list of merge requests
	 * @return average time to merge in seconds
	 */
	private double calculateMeanTimeToMerge(List<ScmMergeRequests> mergeRequests) {
		if (CollectionUtils.isEmpty(mergeRequests)) {
			return 0.0;
		}

		List<Long> timeToMergeList =
				mergeRequests.stream()
						.filter(mr -> mr.getCreatedDate() != null && mr.getMergedAt() != null)
						.filter(
								mr ->
										ScmMergeRequests.MergeRequestState.MERGED
												.name()
												.equalsIgnoreCase(mr.getState()))
						.map(this::calculateTimeToMergeSeconds)
						.toList();

		if (CollectionUtils.isEmpty(timeToMergeList)) {
			return 0.0;
		}

		return timeToMergeList.stream().mapToLong(Long::longValue).average().orElse(0.0);
	}

	/**
	 * Calculates time to merge in seconds for a single merge request.
	 *
	 * @param mergeRequest the merge request
	 * @return time to merge in seconds
	 */
	private long calculateTimeToMergeSeconds(ScmMergeRequests mergeRequest) {
		LocalDateTime createdDateTime =
				DateUtil.convertMillisToLocalDateTime(mergeRequest.getCreatedDate());
		return ChronoUnit.SECONDS.between(createdDateTime, mergeRequest.getMergedAt());
	}
}
