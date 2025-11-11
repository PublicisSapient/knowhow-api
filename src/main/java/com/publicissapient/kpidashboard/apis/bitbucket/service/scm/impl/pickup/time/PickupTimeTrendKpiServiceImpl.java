package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.pickup.time;

import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.strategy.KpiCalculationStrategy;
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
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class PickupTimeTrendKpiServiceImpl implements KpiCalculationStrategy {

	private static final String MR_COUNT = "No of PRs";

	@Override
	public Object getTrendValueList(KpiRequest kpiRequest, List<ScmMergeRequests> mergeRequests, List<Tool> scmTools,
			List<RepoToolValidationData> validationDataList, Set<Assignee> assignees, String projectName) {
		LocalDateTime currentDate = DateUtil.getTodayTime();
		int dataPoints = kpiRequest.getXAxisDataPoints();
		String duration = kpiRequest.getDuration();

		Map<String, List<DataCount>> kpiTrendDataByGroup = new LinkedHashMap<>();

		for (int i = 0; i < dataPoints; i++) {
			CustomDateRange periodRange = KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration);
			String dateLabel = KpiHelperService.getDateRange(periodRange, duration);

			List<ScmMergeRequests> filteredMergeRequests = mergeRequests.stream()
					.filter(request -> request.getPickedForReviewOn() != null)
					.filter(request -> DateUtil.isWithinDateTimeRange(
							DateUtil.convertMillisToLocalDateTime(request.getPickedForReviewOn()),
							periodRange.getStartDateTime(), periodRange.getEndDateTime()))
					.toList();

			scmTools.forEach(tool -> processToolData(tool, filteredMergeRequests, assignees, kpiTrendDataByGroup,
					validationDataList, dateLabel, projectName));

			currentDate = DeveloperKpiHelper.getNextRangeDate(duration, currentDate);
		}
		return kpiTrendDataByGroup;
	}

	private void processToolData(Tool tool, List<ScmMergeRequests> mergeRequests, Set<Assignee> assignees,
			Map<String, List<DataCount>> kpiTrendDataByGroup, List<RepoToolValidationData> validationDataList,
			String dateLabel, String projectName) {
		if (!DeveloperKpiHelper.isValidTool(tool)) {
			return;
		}

		String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);
		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;

		List<ScmMergeRequests> matchingRequests = DeveloperKpiHelper.filterMergeRequestsForBranch(mergeRequests, tool);

		List<Long> pickUpTimes = matchingRequests.stream().map(mr -> {
			LocalDateTime pickedForReviewOn = DateUtil.convertMillisToLocalDateTime(mr.getPickedForReviewOn());
			LocalDateTime createdDate = DateUtil.convertMillisToLocalDateTime(mr.getCreatedDate());
			return Duration.between(createdDate, pickedForReviewOn).toHours();
		}).toList();

		long averagePickUpTime = pickUpTimes.isEmpty() ? 0
				: (long) pickUpTimes.stream().mapToLong(Long::longValue).average().orElse(0);
		long totalMergeRequests = matchingRequests.size();

		DeveloperKpiHelper.setDataCount(projectName, dateLabel, overallKpiGroup, averagePickUpTime,
				Map.of(MR_COUNT, totalMergeRequests), kpiTrendDataByGroup);

		Map<String, List<ScmMergeRequests>> userWiseMergeRequests = DeveloperKpiHelper
				.groupMergeRequestsByUser(matchingRequests);

		validationDataList.addAll(prepareUserValidationData(userWiseMergeRequests, assignees, tool, projectName,
				dateLabel, kpiTrendDataByGroup));
	}

	private List<RepoToolValidationData> prepareUserValidationData(
			Map<String, List<ScmMergeRequests>> userWiseMergeRequests, Set<Assignee> assignees, Tool tool,
			String projectName, String dateLabel, Map<String, List<DataCount>> kpiTrendDataByGroup) {
		return userWiseMergeRequests.entrySet().stream().map(entry -> {
			String userEmail = entry.getKey();
			List<ScmMergeRequests> userMergeRequests = entry.getValue();

			String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);
			AtomicLong pickUpTime = new AtomicLong(0L);
			userMergeRequests.forEach(mr -> {
				LocalDateTime pickedForReviewOn = DateUtil.convertMillisToLocalDateTime(mr.getPickedForReviewOn());
				LocalDateTime createdDate = DateUtil.convertMillisToLocalDateTime(mr.getCreatedDate());
				pickUpTime.addAndGet(Duration.between(createdDate, pickedForReviewOn).toHours());
			});
			long userMrCount = userMergeRequests.size();

			String userKpiGroup = DeveloperKpiHelper.getBranchSubFilter(tool, projectName) + "#" + developerName;

			DeveloperKpiHelper.setDataCount(projectName, dateLabel, userKpiGroup, pickUpTime.longValue(),
					Map.of(MR_COUNT, userMrCount), kpiTrendDataByGroup);

			List<RepoToolValidationData> userValidationData = new ArrayList<>();
			userMergeRequests.forEach(mr -> {
				if (mr.getCreatedDate() != null && mr.getMergedAt() != null) {
					userValidationData.add(createValidationData(projectName, tool, developerName, dateLabel, mr));
				}
			});
			return userValidationData;
		}).flatMap(List::stream).toList();
	}

	private RepoToolValidationData createValidationData(String projectName, Tool tool, String developerName,
			String dateLabel, ScmMergeRequests mergeRequest) {
        RepoToolValidationData validationData = new RepoToolValidationData();
        validationData.setProjectName(projectName);
        validationData.setBranchName(tool.getBranch());
        validationData.setRepoUrl(
                tool.getRepositoryName() != null ? tool.getRepositoryName() : tool.getRepoSlug());
        validationData.setDeveloperName(developerName);
        validationData.setDate(dateLabel);
        validationData.setMergeRequestUrl(mergeRequest.getMergeRequestUrl());
        LocalDateTime createdDateTime =
                DateUtil.convertMillisToLocalDateTime(mergeRequest.getCreatedDate());
        LocalDateTime pickUpDateTime =
                DateUtil.convertMillisToLocalDateTime(mergeRequest.getPickedForReviewOn());
        long timeToMergeSeconds = ChronoUnit.SECONDS.between(createdDateTime, pickUpDateTime);
        validationData.setPickupTime(
                (double) KpiHelperService.convertMilliSecondsToHours(timeToMergeSeconds * 1000.00));
        validationData.setMergeRequestUrl(mergeRequest.getMergeRequestUrl());
        LocalDateTime createdDateTimeUTC = DateUtil.localDateTimeToUTC(createdDateTime);
        LocalDateTime pickUpDateTimeUTC = DateUtil.localDateTimeToUTC(pickUpDateTime);
        validationData.setPrRaisedTime(String.valueOf(createdDateTimeUTC));
        validationData.setPrActivityTime(String.valueOf(pickUpDateTimeUTC));
        validationData.setPrStatus(mergeRequest.getState());
        return validationData;
    }
}
