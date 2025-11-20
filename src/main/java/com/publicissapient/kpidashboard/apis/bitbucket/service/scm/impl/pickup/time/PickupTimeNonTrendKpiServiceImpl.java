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

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.pickup.time;

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
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service implementation for calculating Pickup Time KPI in non-trend mode.
 * Measures the time taken for merge requests to be picked up for review.
 */
@Component
public class PickupTimeNonTrendKpiServiceImpl extends AbstractKpiCalculationStrategy<List<IterationKpiValue>> {

	private static final double PERCENTAGE_MULTIPLIER = 100.0;
	private static final double MILLIS_PER_SECOND = 1000.0;

	/**
	 * Calculates Pickup Time KPI for all SCM tools.
	 *
	 * @param kpiRequest         the KPI request containing filters and parameters
	 * @param mergeRequests      list of merge requests
	 * @param commits            list of SCM commits (unused in this implementation)
	 * @param scmTools           list of SCM tools to process
	 * @param validationDataList list to populate with validation data
	 * @param assignees          set of assignees for developer mapping
	 * @param projectName        name of the project
	 * @return list of iteration KPI values
	 */
	@Override
	public List<IterationKpiValue> calculateKpi(KpiRequest kpiRequest, List<ScmMergeRequests> mergeRequests, List<ScmCommits> commits,
			List<Tool> scmTools, List<RepoToolValidationData> validationDataList, Set<Assignee> assignees,
			String projectName) {
		List<IterationKpiValue> iterationKpiValueList = new ArrayList<>();

		scmTools.forEach(tool -> processToolData(tool, mergeRequests, assignees, iterationKpiValueList,
				validationDataList, kpiRequest, projectName));

		return iterationKpiValueList;
	}

	/**
	 * Processes data for a single SCM tool and calculates pickup times.
	 *
	 * @param tool                   the SCM tool to process
	 * @param mergeRequests          list of all merge requests
	 * @param assignees              set of assignees
	 * @param iterationKpiValueList  list to populate with KPI values
	 * @param validationDataList     list to populate with validation data
	 * @param kpiRequest             the KPI request
	 * @param projectName            name of the project
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
		List<ScmMergeRequests> mergeRequestsForBranch = DeveloperKpiHelper.filterMergeRequestsForBranch(mergeRequests, tool);

		CustomDateRange currentPeriodRange = getCustomDateRange(currentDate, duration, dataPoints);
		List<ScmMergeRequests> currentRequests = filterPickedRequestsByDateRange(mergeRequestsForBranch, currentPeriodRange);
		double currentAveragePickUpTime = calculateAveragePickupTime(currentRequests);

		CustomDateRange previousPeriodRange = getCustomDateRange(currentPeriodRange.getStartDateTime(), duration, dataPoints);
		List<ScmMergeRequests> previousRequests = filterPickedRequestsByDateRange(mergeRequestsForBranch, previousPeriodRange);
		double previousAveragePickUpTime = calculateAveragePickupTime(previousRequests);

		double deviationRate = calculateDeviationRate(currentAveragePickUpTime, previousAveragePickUpTime);

		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;
		iterationKpiValueList.add(new IterationKpiValue(branchName, Constant.AGGREGATED_VALUE,
				List.of(new IterationKpiData(overallKpiGroup, currentAveragePickUpTime, deviationRate, null, "hrs", "%", null))));

		Map<String, List<ScmMergeRequests>> userWiseMergeRequests = DeveloperKpiHelper.groupMergeRequestsByUser(mergeRequestsForBranch);
		validationDataList.addAll(prepareUserValidationData(currentPeriodRange, previousPeriodRange, userWiseMergeRequests,
				assignees, tool, projectName, iterationKpiValueList));
	}

	/**
	 * Filters merge requests picked for review within the specified date range.
	 *
	 * @param mergeRequests list of merge requests to filter
	 * @param dateRange     date range to filter by
	 * @return filtered list of merge requests
	 */
	private List<ScmMergeRequests> filterPickedRequestsByDateRange(List<ScmMergeRequests> mergeRequests, CustomDateRange dateRange) {
		return mergeRequests.stream()
				.filter(request -> request.getPickedForReviewOn() != null)
				.filter(request -> DateUtil.isWithinDateTimeRange(
						DateUtil.convertMillisToLocalDateTime(request.getPickedForReviewOn()),
						dateRange.getStartDateTime(), dateRange.getEndDateTime()))
				.toList();
	}

	/**
	 * Calculates average pickup time for a list of merge requests.
	 *
	 * @param mergeRequests list of merge requests
	 * @return average pickup time in hours
	 */
	private double calculateAveragePickupTime(List<ScmMergeRequests> mergeRequests) {
		List<Long> pickUpTimes = mergeRequests.stream()
				.map(this::calculatePickupTimeHours)
				.toList();
		return pickUpTimes.isEmpty() ? 0 : pickUpTimes.stream().mapToLong(Long::longValue).average().orElse(0);
	}

	/**
	 * Calculates pickup time in hours for a single merge request.
	 *
	 * @param mergeRequest the merge request
	 * @return pickup time in hours
	 */
	private long calculatePickupTimeHours(ScmMergeRequests mergeRequest) {
		LocalDateTime pickedForReviewOn = DateUtil.convertMillisToLocalDateTime(mergeRequest.getPickedForReviewOn());
		LocalDateTime createdDate = DateUtil.convertMillisToLocalDateTime(mergeRequest.getCreatedDate());
		return Duration.between(createdDate, pickedForReviewOn).toHours();
	}

	/**
	 * Calculates the deviation rate between current and previous pickup times.
	 *
	 * @param currentTime  current period average pickup time
	 * @param previousTime previous period average pickup time
	 * @return deviation rate as a percentage
	 */
	private double calculateDeviationRate(double currentTime, double previousTime) {
		double sum = currentTime + previousTime;
		if (sum == 0) {
			return 0.0;
		}
		return Math.round((currentTime - previousTime) / sum * PERCENTAGE_MULTIPLIER);
	}

	/**
	 * Prepares validation data for each user/developer.
	 *
	 * @param currentPeriodRange     current period date range
	 * @param previousPeriodRange    previous period date range
	 * @param userWiseMergeRequests  map of user email to their merge requests
	 * @param assignees              set of assignees
	 * @param tool                   the SCM tool
	 * @param projectName            name of the project
	 * @param iterationKpiValueList  list to populate with user-level KPI values
	 * @return list of validation data for all users
	 */
	private List<RepoToolValidationData> prepareUserValidationData(CustomDateRange currentPeriodRange,
			CustomDateRange previousPeriodRange, Map<String, List<ScmMergeRequests>> userWiseMergeRequests,
			Set<Assignee> assignees, Tool tool, String projectName, List<IterationKpiValue> iterationKpiValueList) {
		String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);
		return userWiseMergeRequests.entrySet().stream().map(entry -> {
			String userEmail = entry.getKey();
			List<ScmMergeRequests> userMergeRequests = entry.getValue();
			String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);

			List<ScmMergeRequests> currentRequests = filterPickedRequestsByDateRange(userMergeRequests, currentPeriodRange);
			double currentAveragePickUpTime = calculateAveragePickupTime(currentRequests);

			List<ScmMergeRequests> previousRequests = filterPickedRequestsByDateRange(userMergeRequests, previousPeriodRange);
			double previousAveragePickUpTime = calculateAveragePickupTime(previousRequests);

			double deviationRate = calculateDeviationRate(currentAveragePickUpTime, previousAveragePickUpTime);

			String userKpiGroup = branchName + "#" + developerName;
			iterationKpiValueList.add(new IterationKpiValue(branchName, developerName,
					List.of(new IterationKpiData(userKpiGroup, currentAveragePickUpTime, deviationRate, null, "hrs", "%", null))));

			return userMergeRequests.stream()
					.filter(mr -> mr.getCreatedDate() != null && mr.getPickedForReviewOn() != null)
					.map(mr -> createValidationData(projectName, tool, developerName, mr))
					.toList();
		}).flatMap(List::stream).toList();
	}

	/**
	 * Creates validation data object for a merge request.
	 *
	 * @param projectName   name of the project
	 * @param tool          the SCM tool
	 * @param developerName name of the developer
	 * @param mergeRequest  the merge request
	 * @return populated validation data object
	 */
	private RepoToolValidationData createValidationData(String projectName, Tool tool, String developerName,
			ScmMergeRequests mergeRequest) {
		RepoToolValidationData validationData = new RepoToolValidationData();
		validationData.setProjectName(projectName);
		validationData.setBranchName(tool.getBranch());
		validationData.setRepoUrl(tool.getRepositoryName() != null ? tool.getRepositoryName() : tool.getRepoSlug());
		validationData.setDeveloperName(developerName);
		validationData.setMergeRequestUrl(mergeRequest.getMergeRequestUrl());

		LocalDateTime createdDateTime = DateUtil.convertMillisToLocalDateTime(mergeRequest.getCreatedDate());
		LocalDateTime pickUpDateTime = DateUtil.convertMillisToLocalDateTime(mergeRequest.getPickedForReviewOn());
		long timeToMergeSeconds = ChronoUnit.SECONDS.between(createdDateTime, pickUpDateTime);
		validationData.setPickupTime((double) KpiHelperService.convertMilliSecondsToHours(timeToMergeSeconds * MILLIS_PER_SECOND));

		LocalDateTime createdDateTimeUTC = DateUtil.localDateTimeToUTC(createdDateTime);
		LocalDateTime pickUpDateTimeUTC = DateUtil.localDateTimeToUTC(pickUpDateTime);
		validationData.setPrRaisedTime(String.valueOf(createdDateTimeUTC));
		validationData.setPrActivityTime(String.valueOf(pickUpDateTimeUTC));
		validationData.setPrStatus(mergeRequest.getState());
		return validationData;
	}

	/**
	 * Returns the strategy type identifier.
	 *
	 * @return strategy type string
	 */
	@Override
	public String getStrategyType() {
		return "PICKUP_TIME_NON_TREND";
	}
}