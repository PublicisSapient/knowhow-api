package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.slingshot;

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

@Component
public class PRCycleTimeNonTrendKpiServiceImpl
		extends AbstractKpiCalculationStrategy<List<IterationKpiValue>> {

	private static final int SECONDS_PER_HOUR = 3600;
	private static final long MILLIS_PER_SECOND = 1000L;
	private static final int DECIMAL_SCALE = 2;
	private static final String LABEL_INFO = "pi-clock";

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
		double currentMeanSeconds = calculateMeanTimeToMerge(currentMergedRequests);

		double meanTimeToMergeHours =
				BigDecimal.valueOf(currentMeanSeconds / SECONDS_PER_HOUR)
						.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP)
						.doubleValue();
		double overallPrCount = countMergedPRs(currentMergedRequests);

		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;
		iterationKpiValueList.add(
				new IterationKpiValue(
						branchName,
						Constant.AGGREGATED_VALUE,
						List.of(
								IterationKpiData.builder()
										.label(overallKpiGroup)
										.value(meanTimeToMergeHours)
										.value1(overallPrCount)
										.labelInfo(LABEL_INFO)
										.unit("hrs")
										.unit1("PRs")
										.build())));

		Map<String, List<ScmMergeRequests>> userWiseMergeRequests =
				DeveloperKpiHelper.groupMergeRequestsByUser(mergeRequestsForBranch);
		validationDataList.addAll(
				prepareUserValidationData(
						currentPeriodRange,
						userWiseMergeRequests,
						assignees,
						tool,
						projectName,
						iterationKpiValueList));
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

	private List<RepoToolValidationData> prepareUserValidationData(
			CustomDateRange currentPeriodRange,
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

							double currentMeanSeconds =
									calculateMeanTimeToMerge(
											filterMergedRequestsByDateRange(userMergeRequests, currentPeriodRange));
							double userAverageHrs =
									BigDecimal.valueOf(currentMeanSeconds / SECONDS_PER_HOUR)
											.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP)
											.doubleValue();
							double userPrCount = countMergedPRs(
									filterMergedRequestsByDateRange(userMergeRequests, currentPeriodRange));

							String userKpiGroup = branchName + "#" + developerName;
							iterationKpiValueList.add(
									new IterationKpiValue(
											branchName,
											developerName,
											List.of(
													IterationKpiData.builder()
															.label(userKpiGroup)
															.value(userAverageHrs)
															.value1(userPrCount)
															.labelInfo(LABEL_INFO)
															.unit("hrs")
															.unit1("PRs")
															.build())));

							return userMergeRequests.stream()
									.filter(mr -> mr.getCreatedDate() != null && mr.getMergedAt() != null)
									.map(mr -> createValidationData(projectName, tool, developerName, null, mr))
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

	private double countMergedPRs(List<ScmMergeRequests> mergeRequests) {
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
						.filter(mr -> mr.getCreatedDate() != null && mr.getMergedAt() != null)
						.filter(
								mr ->
										ScmMergeRequests.MergeRequestState.MERGED
												.name()
												.equalsIgnoreCase(mr.getState()))
						.map(
								mr ->
										ChronoUnit.SECONDS.between(
												DateUtil.convertMillisToLocalDateTime(mr.getCreatedDate()),
												mr.getMergedAt()))
						.toList();

		return CollectionUtils.isEmpty(timeToMergeList)
				? 0.0
				: timeToMergeList.stream().mapToLong(Long::longValue).average().orElse(0.0);
	}

	@Override
	public String getStrategyType() {
		return "PR_CYCLE_TIME_SLINGSHOT_NON_TREND";
	}
}
