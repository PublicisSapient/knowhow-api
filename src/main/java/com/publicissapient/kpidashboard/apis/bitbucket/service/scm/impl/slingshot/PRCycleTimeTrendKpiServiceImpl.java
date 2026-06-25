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

		double sumTimeToMergeHours = calculateSumTimeToMerge(mergeRequestsForBranch);
		long mergedPrCount = countMergedPRs(mergeRequestsForBranch);

		DeveloperKpiHelper.setDataCount(
				projectName,
				dateLabel,
				branchName + "#" + Constant.AGGREGATED_VALUE,
				sumTimeToMergeHours,
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

							double userSumHrs = calculateSumTimeToMerge(userMergeRequests);
							long userMergedPrCount = countMergedPRs(userMergeRequests);

							DeveloperKpiHelper.setDataCount(
									projectName,
									dateLabel,
									branchName + "#" + developerName,
									userSumHrs,
									Map.of("No. of PRs", userMergedPrCount),
									kpiTrendDataByGroup);

							return userMergeRequests.stream()
									.filter(mr -> mr.getMergedAt() != null)
									.filter(
											mr ->
													ScmMergeRequests.MergeRequestState.MERGED
															.name()
															.equalsIgnoreCase(mr.getState()))
									.map(
											mr ->
													createValidationData(
															projectName, tool, developerName, userEmail, dateLabel, mr))
									.toList();
						})
				.flatMap(List::stream)
				.toList();
	}

	private RepoToolValidationData createValidationData(
			String projectName,
			Tool tool,
			String developerName,
			String developerEmail,
			String dateLabel,
			ScmMergeRequests mergeRequest) {
		RepoToolValidationData validationData = new RepoToolValidationData();
		validationData.setProjectName(projectName);
		validationData.setBranchName(tool.getBranch());
		validationData.setRepoUrl(
				tool.getRepositoryName() != null ? tool.getRepositoryName() : tool.getRepoSlug());
		validationData.setDeveloperName(developerName);
		validationData.setDeveloperEmail(developerEmail);
		validationData.setDate(dateLabel);

		LocalDateTime startTime = DateUtil.convertMillisToLocalDateTime(mergeRequest.getCreatedDate());
		long timeToMergeSeconds = ChronoUnit.SECONDS.between(startTime, mergeRequest.getMergedAt());
		validationData.setTotalTimeSpent(timeToMergeSeconds / 3600.0);
		validationData.setMergeRequestUrl(mergeRequest.getMergeRequestUrl());

		LocalDateTime startTimeUTC = DateUtil.localDateTimeToUTC(startTime);
		LocalDateTime mergedAtUTC = DateUtil.localDateTimeToUTC(mergeRequest.getMergedAt());
		validationData.setPrRaisedTime(DateUtil.tranformUTCLocalTimeToZFormat(startTimeUTC));
		validationData.setPrActivityTime(DateUtil.tranformUTCLocalTimeToZFormat(mergedAtUTC));

		return validationData;
	}

	private long countMergedPRs(List<ScmMergeRequests> mergeRequests) {
		return mergeRequests.stream()
				.filter(
						mr -> ScmMergeRequests.MergeRequestState.MERGED.name().equalsIgnoreCase(mr.getState()))
				.count();
	}

	private double calculateSumTimeToMerge(List<ScmMergeRequests> mergeRequests) {
		if (CollectionUtils.isEmpty(mergeRequests)) {
			return 0.0;
		}
		return mergeRequests.stream()
				.filter(mr -> mr.getMergedAt() != null)
				.filter(
						mr -> ScmMergeRequests.MergeRequestState.MERGED.name().equalsIgnoreCase(mr.getState()))
				.filter(mr -> mr.getCreatedDate() != null)
				.mapToDouble(
						mr -> {
							LocalDateTime startTime = DateUtil.convertMillisToLocalDateTime(mr.getCreatedDate());
							long seconds = ChronoUnit.SECONDS.between(startTime, mr.getMergedAt());
							return seconds > 0 ? seconds / 3600.0 : 0.0;
						})
				.sum();
	}

	@Override
	public String getStrategyType() {
		return "PR_CYCLE_TIME_SLINGSHOT_TREND";
	}
}
