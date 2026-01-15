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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.strategy.AbstractKpiCalculationStrategy;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
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

/**
 * Service implementation for calculating Mean Time To Merge KPI in non-trend mode. Measures the
 * average time taken to merge pull/merge requests.
 */
@Component
public class MeanTimeToMergeNonTrendKpiServiceImpl
		extends AbstractKpiCalculationStrategy<List<IterationKpiValue>> {

	private static final int SECONDS_PER_HOUR = 3600;
	private static final long MILLIS_PER_SECOND = 1000L;
	private static final int DECIMAL_SCALE = 2;
	private static final String LABEL_INFO = "pi-clock";

	/**
	 * Calculates Mean Time To Merge KPI for all SCM tools.
	 *
	 * @param kpiRequest the KPI request containing filters and parameters
	 * @param mergeRequests list of merge requests
	 * @param commits list of SCM commits (unused in this implementation)
	 * @param scmTools list of SCM tools to process
	 * @param validationDataList list to populate with validation data
	 * @param assignees set of assignees for developer mapping
	 * @param projectName name of the project
	 * @return list of iteration KPI values
	 */
	@Override
	public List<IterationKpiValue> calculateKpi(
			KpiRequest kpiRequest,
			List<ScmMergeRequests> mergeRequests,
			List<ScmCommits> commits,
			List<Tool> scmTools,
			List<RepoToolValidationData> validationDataList,
			Set<Assignee> assignees,
			String projectName) {
		List<IterationKpiValue> iterationKpiValueList = new ArrayList<>();

		scmTools.forEach(
				tool ->
						processToolData(
								tool,
								mergeRequests,
								assignees,
								iterationKpiValueList,
								validationDataList,
								kpiRequest,
								projectName));

		return iterationKpiValueList;
	}

	/**
	 * Processes data for a single SCM tool and calculates mean time to merge.
	 *
	 * @param tool the SCM tool to process
	 * @param mergeRequests list of all merge requests
	 * @param assignees set of assignees
	 * @param iterationKpiValueList list to populate with KPI values
	 * @param validationDataList list to populate with validation data
	 * @param kpiRequest the KPI request
	 * @param projectName name of the project
	 */
	private void processToolData(
			Tool tool,
			List<ScmMergeRequests> mergeRequests,
			Set<Assignee> assignees,
			List<IterationKpiValue> iterationKpiValueList,
			List<RepoToolValidationData> validationDataList,
			KpiRequest kpiRequest,
			String projectName) {
		if (!DeveloperKpiHelper.isValidTool(tool)) {
			return;
		}

		int dataPoints = kpiRequest.getXAxisDataPoints();
		LocalDateTime currentDate = DateUtil.getTodayTime();
		String duration = kpiRequest.getDuration();
		String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);
		List<ScmMergeRequests> mergeRequestsForBranch =
				DeveloperKpiHelper.filterMergeRequestsForBranch(mergeRequests, tool);

		CustomDateRange currentPeriodRange = getCustomDateRange(currentDate, duration, dataPoints);
		List<ScmMergeRequests> currentMergedRequests =
				filterMergedRequestsByDateRange(mergeRequestsForBranch, currentPeriodRange);
		double currentMeanTimeToMergeSeconds = calculateMeanTimeToMerge(currentMergedRequests);

		CustomDateRange previousPeriodRange =
				getCustomDateRange(currentPeriodRange.getStartDateTime(), duration, dataPoints);
		List<ScmMergeRequests> previousMergedRequests =
				filterMergedRequestsByDateRange(mergeRequestsForBranch, previousPeriodRange);
		double previousMeanTimeToMergeSeconds = calculateMeanTimeToMerge(previousMergedRequests);

		double meanTimeToMergeHours =
				BigDecimal.valueOf(currentMeanTimeToMergeSeconds / SECONDS_PER_HOUR)
						.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP)
						.doubleValue();
		double deviationRate =
				calculateDeviationRate(currentMeanTimeToMergeSeconds, previousMeanTimeToMergeSeconds);

		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;
		iterationKpiValueList.add(
				new IterationKpiValue(
						branchName,
						Constant.AGGREGATED_VALUE,
						List.of(
								new IterationKpiData(
										overallKpiGroup,
										meanTimeToMergeHours,
										deviationRate,
										LABEL_INFO,
										"hrs",
										"%",
										null))));

		Map<String, List<ScmMergeRequests>> userWiseMergeRequests =
				DeveloperKpiHelper.groupMergeRequestsByUser(mergeRequestsForBranch);
		validationDataList.addAll(
				prepareUserValidationData(
						currentPeriodRange,
						previousPeriodRange,
						userWiseMergeRequests,
						assignees,
						tool,
						projectName,
						iterationKpiValueList));
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
	 * Prepares validation data for each user/developer.
	 *
	 * @param currentPeriodRange current period date range
	 * @param previousPeriodRange previous period date range
	 * @param userWiseMergeRequests map of user email to their merge requests
	 * @param assignees set of assignees
	 * @param tool the SCM tool
	 * @param projectName name of the project
	 * @param iterationKpiValueList list to populate with user-level KPI values
	 * @return list of validation data for all users
	 */
	private List<RepoToolValidationData> prepareUserValidationData(
			CustomDateRange currentPeriodRange,
			CustomDateRange previousPeriodRange,
			Map<String, List<ScmMergeRequests>> userWiseMergeRequests,
			Set<Assignee> assignees,
			Tool tool,
			String projectName,
			List<IterationKpiValue> iterationKpiValueList) {
		String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);
		return userWiseMergeRequests.entrySet().stream()
				.map(
						entry -> {
							String userEmail = entry.getKey();
							List<ScmMergeRequests> userMergeRequests = entry.getValue();
							String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);

							List<ScmMergeRequests> currentMergedRequests =
									filterMergedRequestsByDateRange(userMergeRequests, currentPeriodRange);
							double currentMeanTimeToMergeSeconds =
									calculateMeanTimeToMerge(currentMergedRequests);
							double userAverageHrs =
									BigDecimal.valueOf(currentMeanTimeToMergeSeconds / SECONDS_PER_HOUR)
											.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP)
											.doubleValue();

							List<ScmMergeRequests> previousMergedRequests =
									filterMergedRequestsByDateRange(userMergeRequests, previousPeriodRange);
							double previousMeanTimeToMergeSeconds =
									calculateMeanTimeToMerge(previousMergedRequests);
							double deviationRate =
									calculateDeviationRate(
											currentMeanTimeToMergeSeconds, previousMeanTimeToMergeSeconds);

							String userKpiGroup = branchName + "#" + developerName;
							iterationKpiValueList.add(
									new IterationKpiValue(
											branchName,
											developerName,
											List.of(
													new IterationKpiData(
															userKpiGroup,
															userAverageHrs,
															deviationRate,
															LABEL_INFO,
															"hrs",
															"%",
															null))));

							return userMergeRequests.stream()
									.filter(mr -> mr.getCreatedDate() != null && mr.getMergedAt() != null)
									.map(mr -> createValidationData(projectName, tool, developerName, null, mr))
									.toList();
						})
				.flatMap(List::stream)
				.toList();
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

	/**
	 * Creates validation data object for a merge request.
	 *
	 * @param projectName name of the project
	 * @param tool the SCM tool
	 * @param developerName name of the developer
	 * @param dateLabel date label (can be null)
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
		return "REPO_TOOL_MEAN_TIME_TO_MERGE_NON_TREND";
	}
}
