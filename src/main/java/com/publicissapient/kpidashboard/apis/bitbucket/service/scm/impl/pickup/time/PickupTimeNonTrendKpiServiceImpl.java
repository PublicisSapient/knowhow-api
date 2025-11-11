package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.pickup.time;

import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.strategy.KpiCalculationStrategy;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.IterationKpiData;
import com.publicissapient.kpidashboard.apis.model.IterationKpiValue;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import org.apache.commons.collections4.CollectionUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PickupTimeNonTrendKpiServiceImpl implements KpiCalculationStrategy {

	public List<IterationKpiValue> getTrendValueList(KpiRequest kpiRequest, List<ScmMergeRequests> mergeRequests,
			List<Tool> scmTools, List<RepoToolValidationData> validationDataList, Set<Assignee> assignees,
			String projectName) {
		List<IterationKpiValue> iterationKpiValueList = new ArrayList<>();

		scmTools.forEach(tool -> processToolData(tool, mergeRequests, assignees, iterationKpiValueList,
				validationDataList, kpiRequest, projectName));

		return iterationKpiValueList;
	}

	private CustomDateRange getCustomDateRange(LocalDateTime currentDate, String duration, int dataPoints) {
		CustomDateRange periodRange = new CustomDateRange();
		if (duration.equalsIgnoreCase(CommonConstant.DAY)) {
			periodRange.setEndDateTime(currentDate.minusDays(1));
			periodRange.setStartDateTime(currentDate.minusDays(dataPoints - 1L));
		} else {
			periodRange.setEndDateTime(currentDate);
			periodRange.setStartDateTime(currentDate.minusWeeks(dataPoints - 1L));
		}
		return periodRange;
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
				.filter(request -> request.getPickedForReviewOn() != null)
				.filter(request -> DateUtil.isWithinDateTimeRange(
						DateUtil.convertMillisToLocalDateTime(request.getPickedForReviewOn()),
						periodRange.getStartDateTime(), periodRange.getEndDateTime()))
				.toList();

		CustomDateRange prevPeriodRange = getCustomDateRange(periodRange.getStartDateTime(), duration, dataPoints);
		List<ScmMergeRequests> previousMergeRequestsInRange = mergeRequestsForBranch.stream()
				.filter(request -> request.getPickedForReviewOn() != null)
				.filter(request -> DateUtil.isWithinDateTimeRange(
						DateUtil.convertMillisToLocalDateTime(request.getPickedForReviewOn()),
						prevPeriodRange.getStartDateTime(), prevPeriodRange.getStartDateTime()))
				.toList();

		List<Long> currentPickUpTimes = currentMergedRequestsInRange.stream().map(mr -> {
			LocalDateTime pickedForReviewOn = DateUtil.convertMillisToLocalDateTime(mr.getPickedForReviewOn());
			LocalDateTime createdDate = DateUtil.convertMillisToLocalDateTime(mr.getCreatedDate());
			return Duration.between(createdDate, pickedForReviewOn).toHours();
		}).toList();
		double currentAveragePickUpTime = currentPickUpTimes.isEmpty() ? 0
				: currentPickUpTimes.stream().mapToLong(Long::longValue).average().orElse(0);

		List<Long> pickUpTimes = previousMergeRequestsInRange.stream().map(mr -> {
			LocalDateTime pickedForReviewOn = DateUtil.convertMillisToLocalDateTime(mr.getPickedForReviewOn());
			LocalDateTime createdDate = DateUtil.convertMillisToLocalDateTime(mr.getCreatedDate());
			return Duration.between(createdDate, pickedForReviewOn).toHours();
		}).toList();

		double previousAveragePickUpTime = pickUpTimes.isEmpty() ? 0
				: pickUpTimes.stream().mapToLong(Long::longValue).average().orElse(0);

		double deviationRate = Math.round((currentAveragePickUpTime - previousAveragePickUpTime)
				/ (previousAveragePickUpTime + currentAveragePickUpTime) * 100);

		iterationKpiValueList.add(new IterationKpiValue(branchName, Constant.AGGREGATED_VALUE,
				List.of(new IterationKpiData(overallKpiGroup, currentAveragePickUpTime, deviationRate, null, "hrs", "%",
						null))));
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
					.filter(request -> request.getPickedForReviewOn() != null)
					.filter(request -> DateUtil.isWithinDateTimeRange(
							DateUtil.convertMillisToLocalDateTime(request.getPickedForReviewOn()),
							periodRange.getStartDateTime(), periodRange.getEndDateTime()))
					.toList();

			List<ScmMergeRequests> previousMergeRequestsInRange = userMergeRequests.stream()
					.filter(request -> request.getPickedForReviewOn() != null)
					.filter(request -> DateUtil.isWithinDateTimeRange(
							DateUtil.convertMillisToLocalDateTime(request.getPickedForReviewOn()),
							prevPeriodRange.getStartDateTime(), prevPeriodRange.getStartDateTime()))
					.toList();

			String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);
			double currentMeanTimeToMergeSeconds = calculateMeanTimeToMerge(currentMergedRequestsInRange);
			double userAverageHrs = currentMeanTimeToMergeSeconds / (3600);
			double previousMeanTimeToMergeSeconds = calculateMeanTimeToMerge(previousMergeRequestsInRange);
			double deviationRate = Math.round((currentMeanTimeToMergeSeconds - previousMeanTimeToMergeSeconds)
					/ previousMeanTimeToMergeSeconds * 100);

			String userKpiGroup = DeveloperKpiHelper.getBranchSubFilter(tool, projectName) + "#" + developerName;
			iterationKpiValueList.add(new IterationKpiValue(DeveloperKpiHelper.getBranchSubFilter(tool, projectName),
					developerName, List.of(new IterationKpiData(userKpiGroup, userAverageHrs, deviationRate, null,
							"hrs", "%", null))));
			List<RepoToolValidationData> userValidationData = new ArrayList<>();
			userMergeRequests.forEach(mr -> {
				if (mr.getCreatedDate() != null && mr.getPickedForReviewOn() != null) {
					userValidationData.add(createValidationData(projectName, tool, developerName, mr));
				}
			});
			return userValidationData;
		}).flatMap(List::stream).toList();
	}

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
		validationData
				.setPickupTime((double) KpiHelperService.convertMilliSecondsToHours(timeToMergeSeconds * 1000.00));
		validationData.setMergeRequestUrl(mergeRequest.getMergeRequestUrl());
		LocalDateTime createdDateTimeUTC = DateUtil.localDateTimeToUTC(createdDateTime);
		LocalDateTime pickUpDateTimeUTC = DateUtil.localDateTimeToUTC(pickUpDateTime);
		validationData.setPrRaisedTime(String.valueOf(createdDateTimeUTC));
		validationData.setPrActivityTime(String.valueOf(pickUpDateTimeUTC));
		validationData.setPrStatus(mergeRequest.getState());
		return validationData;
	}

	private double calculateMeanTimeToMerge(List<ScmMergeRequests> mergeRequests) {
		if (CollectionUtils.isEmpty(mergeRequests)) {
			return 0.0;
		}

		List<Long> timeToMergeList = mergeRequests.stream()
				.filter(mr -> mr.getCreatedDate() != null && mr.getMergedAt() != null)
				.filter(mr -> ScmMergeRequests.MergeRequestState.MERGED.name().equalsIgnoreCase(mr.getState()))
				.map(mr -> {
					LocalDateTime createdDateTime = DateUtil.convertMillisToLocalDateTime(mr.getCreatedDate());
					return ChronoUnit.SECONDS.between(createdDateTime, mr.getMergedAt());
				}).toList();

		if (CollectionUtils.isEmpty(timeToMergeList)) {
			return 0.0;
		}

		return timeToMergeList.stream().mapToLong(Long::longValue).average().orElse(0.0);
	}

}