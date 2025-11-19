package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.mttm;

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
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MeanTimeToMergeTrendKpiServiceImpl extends AbstractKpiCalculationStrategy<Map<String, List<DataCount>>> {

	@Override
	public Map<String, List<DataCount>> calculateKpi(KpiRequest kpiRequest, List<ScmMergeRequests> mergeRequests, List<ScmCommits> commits,
			List<Tool> scmTools, List<RepoToolValidationData> validationDataList, Set<Assignee> assignees,
			String projectName) {
		Map<String, List<DataCount>> kpiTrendDataByGroup = new LinkedHashMap<>();
		int dataPoints = kpiRequest.getXAxisDataPoints();
		LocalDateTime currentDate = DateUtil.getTodayTime();
		String duration = kpiRequest.getDuration();
		for (int i = 0; i < dataPoints; i++) {
			CustomDateRange periodRange = KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration);
			String dateLabel = KpiHelperService.getDateRange(periodRange, duration);
			List<ScmMergeRequests> mergedRequestsInRange = mergeRequests.stream()
					.filter(request -> request.getMergedAt() != null)
					.filter(request -> DateUtil.isWithinDateTimeRange(request.getMergedAt(),
							periodRange.getStartDateTime(), periodRange.getEndDateTime()))
					.toList();

			scmTools.forEach(tool -> processToolData(tool, mergedRequestsInRange, assignees, kpiTrendDataByGroup,
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

		List<ScmMergeRequests> mergeRequestsForBranch = DeveloperKpiHelper.filterMergeRequestsForBranch(mergeRequests,
				tool);

		double meanTimeToMergeSeconds = calculateMeanTimeToMerge(mergeRequestsForBranch);
		long meanTimeToMergeHours = KpiHelperService.convertMilliSecondsToHours(meanTimeToMergeSeconds * 1000);

		DeveloperKpiHelper.setDataCount(projectName, dateLabel, overallKpiGroup, meanTimeToMergeHours, new HashMap<>(),
				kpiTrendDataByGroup);

		Map<String, List<ScmMergeRequests>> userWiseMergeRequests = DeveloperKpiHelper
				.groupMergeRequestsByUser(mergeRequestsForBranch);

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
			double userAverageSeconds = calculateMeanTimeToMerge(userMergeRequests);
			Long userAverageHrs = KpiHelperService.convertMilliSecondsToHours(userAverageSeconds * 1000);

			String userKpiGroup = DeveloperKpiHelper.getBranchSubFilter(tool, projectName) + "#" + developerName;

			DeveloperKpiHelper.setDataCount(projectName, dateLabel, userKpiGroup, userAverageHrs, new HashMap<>(),
					kpiTrendDataByGroup);

			List<RepoToolValidationData> userValidationData = new ArrayList<>();
			userMergeRequests.forEach(mr -> {
				if (mr.getCreatedDate() != null && mr.getMergedAt() != null) {
					userValidationData.add(createValidationData(projectName, tool, developerName, dateLabel, mr));
				}
			});
			return userValidationData;
		}).flatMap(List::stream).collect(Collectors.toList());
	}

	private RepoToolValidationData createValidationData(String projectName, Tool tool, String developerName,
			String dateLabel, ScmMergeRequests mergeRequest) {
		RepoToolValidationData validationData = new RepoToolValidationData();
		validationData.setProjectName(projectName);
		validationData.setBranchName(tool.getBranch());
		validationData.setRepoUrl(tool.getRepositoryName() != null ? tool.getRepositoryName() : tool.getRepoSlug());
		validationData.setDeveloperName(developerName);
		validationData.setDate(dateLabel);

		LocalDateTime createdDateTime = DateUtil.convertMillisToLocalDateTime(mergeRequest.getCreatedDate());
		long timeToMergeSeconds = ChronoUnit.SECONDS.between(createdDateTime, mergeRequest.getMergedAt());
		validationData.setMeanTimeToMerge(KpiHelperService.convertMilliSecondsToHours(timeToMergeSeconds * 1000.0));

		validationData.setMergeRequestUrl(mergeRequest.getMergeRequestUrl());
		LocalDateTime createdDateTimeUTC = DateUtil.localDateTimeToUTC(createdDateTime);
		LocalDateTime mergedAtUTC = DateUtil.localDateTimeToUTC(mergeRequest.getMergedAt());

		validationData.setPrRaisedTime(DateUtil.tranformUTCLocalTimeToZFormat(createdDateTimeUTC));
		validationData.setPrActivityTime(DateUtil.tranformUTCLocalTimeToZFormat(mergedAtUTC));

		return validationData;
	}

	@Override
	public String getStrategyType() {
		return "REPO_TOOL_MEAN_TIME_TO_MERGE_TREND";
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
				}).collect(Collectors.toList());

		if (CollectionUtils.isEmpty(timeToMergeList)) {
			return 0.0;
		}

		return timeToMergeList.stream().mapToLong(Long::longValue).average().orElse(0.0);
	}

}
