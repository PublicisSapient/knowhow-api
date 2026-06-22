package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.slingshot;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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

@Component
public class PRCycleTimeTrendKpiServiceImpl
		extends AbstractKpiCalculationStrategy<Map<String, List<DataCount>>> {

	private static final long MILLIS_PER_SECOND = 1000L;

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

	private List<ScmMergeRequests> filterMergedRequestsByDateRange(
			List<ScmMergeRequests> mergeRequests, CustomDateRange dateRange) {
		LocalDateTime start = dateRange.getStartDate().atStartOfDay();
		LocalDateTime end = dateRange.getEndDate().atTime(23, 59, 59);
		return mergeRequests.stream()
				.filter(request -> request.getMergedAt() != null)
				.filter(request -> DateUtil.isWithinDateTimeRange(request.getMergedAt(), start, end))
				.toList();
	}

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
		long mergedPrCount = countMergedPRs(mergeRequestsForBranch);

		DeveloperKpiHelper.setDataCount(
				projectName,
				dateLabel,
				branchName + "#" + Constant.AGGREGATED_VALUE,
				meanTimeToMergeHours,
				Map.of("No. of PRs", mergedPrCount),
				kpiTrendDataByGroup);

		Map<String, List<ScmMergeRequests>> userWiseMergeRequests =
				DeveloperKpiHelper.groupMergeRequestsByUser(mergeRequestsForBranch);
		validationDataList.addAll(
				prepareUserValidationData(
						userWiseMergeRequests, assignees, tool, projectName, dateLabel, kpiTrendDataByGroup));
	}

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
							long userMergedPrCount = countMergedPRs(userMergeRequests);

							DeveloperKpiHelper.setDataCount(
									projectName,
									dateLabel,
									branchName + "#" + developerName,
									userAverageHrs,
									Map.of("No. of PRs", userMergedPrCount),
									kpiTrendDataByGroup);

							return userMergeRequests.stream()
									.filter(mr -> mr.getCreatedDate() != null && mr.getMergedAt() != null)
									.map(mr -> createValidationData(projectName, tool, developerName, dateLabel, mr))
									.toList();
						})
				.flatMap(List::stream)
				.toList();
	}

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

		LocalDateTime startTime =
				mergeRequest.getFirstCommitDate() != null
						? mergeRequest.getFirstCommitDate()
						: DateUtil.convertMillisToLocalDateTime(mergeRequest.getCreatedDate());
		long timeToMergeSeconds = ChronoUnit.SECONDS.between(startTime, mergeRequest.getMergedAt());
		validationData.setMeanTimeToMerge(
				KpiHelperService.convertMilliSecondsToHours(timeToMergeSeconds * MILLIS_PER_SECOND));
		validationData.setMergeRequestUrl(mergeRequest.getMergeRequestUrl());

		LocalDateTime startTimeUTC = DateUtil.localDateTimeToUTC(startTime);
		LocalDateTime mergedAtUTC = DateUtil.localDateTimeToUTC(mergeRequest.getMergedAt());
		validationData.setPrRaisedTime(DateUtil.tranformUTCLocalTimeToZFormat(startTimeUTC));
		validationData.setPrActivityTime(DateUtil.tranformUTCLocalTimeToZFormat(mergedAtUTC));

		return validationData;
	}

	private long countMergedPRs(List<ScmMergeRequests> mergeRequests) {
		return mergeRequests.stream()
				.filter(mr -> ScmMergeRequests.MergeRequestState.MERGED.name().equalsIgnoreCase(mr.getState()))
				.count();
	}

	private double calculateMeanTimeToMerge(List<ScmMergeRequests> mergeRequests) {
		if (CollectionUtils.isEmpty(mergeRequests)) {
			return 0.0;
		}
		List<Long> timeToMergeList =
				mergeRequests.stream()
						.filter(mr -> mr.getMergedAt() != null)
						.filter(
								mr ->
										ScmMergeRequests.MergeRequestState.MERGED
												.name()
												.equalsIgnoreCase(mr.getState()))
						.filter(mr -> mr.getFirstCommitDate() != null || mr.getCreatedDate() != null)
						.map(
								mr -> {
									LocalDateTime startTime =
											mr.getFirstCommitDate() != null
													? mr.getFirstCommitDate()
													: DateUtil.convertMillisToLocalDateTime(mr.getCreatedDate());
									return ChronoUnit.SECONDS.between(startTime, mr.getMergedAt());
								})
						.filter(seconds -> seconds > 0)
						.toList();

		return CollectionUtils.isEmpty(timeToMergeList)
				? 0.0
				: timeToMergeList.stream().mapToLong(Long::longValue).average().orElse(0.0);
	}

	@Override
	public String getStrategyType() {
		return "PR_CYCLE_TIME_SLINGSHOT_TREND";
	}
}
