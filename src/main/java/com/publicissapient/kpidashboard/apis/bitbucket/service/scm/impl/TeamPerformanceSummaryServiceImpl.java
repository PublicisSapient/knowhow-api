package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.bitbucket.model.MetricItem;
import com.publicissapient.kpidashboard.apis.bitbucket.model.PerformanceSummary;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Service implementation for generating team performance summaries based on SCM data.
 * Provides metrics including total PRs, lines of code, and average review time.
 * 
 * @author KnowHOW
 */
@Service
@Slf4j
public class TeamPerformanceSummaryServiceImpl {

	private static final String COMMIT_LIST = "commitList";
	private static final String MR_LIST = "mrList";
	private static final String HOURS_SUFFIX = " Hrs";
	private static final String METRIC_TOTAL_PRS = "Total PRs";
	private static final String METRIC_LINES_OF_CODE = "Lines of Code";
	private static final String METRIC_AVG_REVIEW_TIME = "Average Review Time";
	private static final int DURATION_INDEX = 0;
	private static final int PROJECT_KEY_INDEX = 1;

	private final ScmKpiHelperService scmKpiHelperService;
	private final KpiHelperService kpiHelperService;
	private final ConfigHelperService configHelperService;

	/**
	 * Constructor for dependency injection.
	 * 
	 * @param scmKpiHelperService service for SCM KPI operations
	 * @param kpiHelperService service for KPI helper operations
	 * @param configHelperService service for configuration operations
	 */
	public TeamPerformanceSummaryServiceImpl(ScmKpiHelperService scmKpiHelperService,
			KpiHelperService kpiHelperService, ConfigHelperService configHelperService) {
		this.scmKpiHelperService = scmKpiHelperService;
		this.kpiHelperService = kpiHelperService;
		this.configHelperService = configHelperService;
	}

	/**
	 * Retrieves team performance summary based on SCM data for the specified project and duration.
	 * 
	 * @param kpiRequest the KPI request containing project ID, duration, and date range
	 * @return list of performance summaries for each SCM tool/branch, or empty list if no tools found
	 */
	public List<PerformanceSummary> getTeamPerformanceSummary(KpiRequest kpiRequest) {
		String duration = kpiRequest.getSelectedMap().get(CommonConstant.DATE).get(DURATION_INDEX);
		int durationValue = Integer.parseInt(kpiRequest.getIds()[DURATION_INDEX]);
		String projectKey = kpiRequest.getIds()[PROJECT_KEY_INDEX];
		ObjectId basicProjectConfigId = new ObjectId(projectKey);

		List<Tool> scmTools = getScmTools(basicProjectConfigId);
		if (CollectionUtils.isEmpty(scmTools)) {
			log.error("[BITBUCKET-AGGREGATED-VALUE]. No SCM tools found for project {}", basicProjectConfigId);
			return Collections.emptyList();
		}

		CustomDateRange dateRange = getStartAndEndDate(durationValue, duration);
		Map<String, Object> scmDataMap = fetchFromDb(dateRange, basicProjectConfigId);
		List<ScmCommits> allCommits = (List<ScmCommits>) scmDataMap.get(COMMIT_LIST);
		List<ScmMergeRequests> allMergeRequests = (List<ScmMergeRequests>) scmDataMap.get(MR_LIST);

		ProjectBasicConfig projectBasicConfig = configHelperService.getProjectConfig(basicProjectConfigId.toString());
		List<PerformanceSummary> performanceSummaries = new ArrayList<>();

		scmTools.forEach(tool -> performanceSummaries
				.add(buildPerformanceSummary(tool, projectBasicConfig, allCommits, allMergeRequests, dateRange)));

		return performanceSummaries;
	}

	/**
	 * Retrieves SCM tools configured for the project.
	 * 
	 * @param basicProjectConfigId the project configuration ID
	 * @return list of SCM tools
	 */
	private List<Tool> getScmTools(ObjectId basicProjectConfigId) {
		Map<ObjectId, Map<String, List<Tool>>> toolMap = configHelperService.getToolItemMap();
		Map<String, List<Tool>> toolListMap = toolMap.getOrDefault(basicProjectConfigId, Map.of());
		return kpiHelperService.populateSCMToolsRepoList(toolListMap);
	}

	/**
	 * Builds performance summary for a specific SCM tool/branch.
	 * 
	 * @param tool the SCM tool
	 * @param projectBasicConfig the project configuration
	 * @param allCommits all commits for the project
	 * @param allMergeRequests all merge requests for the project
	 * @param dateRange the date range for filtering
	 * @return performance summary with metrics
	 */
	private PerformanceSummary buildPerformanceSummary(Tool tool, ProjectBasicConfig projectBasicConfig,
			List<ScmCommits> allCommits, List<ScmMergeRequests> allMergeRequests, CustomDateRange dateRange) {
		String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectBasicConfig.getProjectDisplayName());
		List<ScmCommits> commitsForBranch = DeveloperKpiHelper.filterCommitsForBranch(allCommits, tool);
		List<ScmMergeRequests> mergeRequestsForBranch = DeveloperKpiHelper.filterMergeRequestsForBranch(allMergeRequests, tool);

		long linesOfCode = calculateLinesOfCode(commitsForBranch);
		long averageReviewTime = calculateAverageReviewTime(mergeRequestsForBranch, dateRange);

		return new PerformanceSummary(branchName,
				List.of(new MetricItem(METRIC_TOTAL_PRS, String.valueOf(mergeRequestsForBranch.size())),
						new MetricItem(METRIC_LINES_OF_CODE, String.valueOf(linesOfCode)),
						new MetricItem(METRIC_AVG_REVIEW_TIME, averageReviewTime + HOURS_SUFFIX)));
	}

	/**
	 * Calculates total lines of code from non-merge commits.
	 * 
	 * @param commits list of commits
	 * @return total lines of code changed
	 */
	private long calculateLinesOfCode(List<ScmCommits> commits) {
		return commits.stream()
				.filter(commit -> !commit.getIsMergeCommit())
				.mapToLong(ScmCommits::getChangedLines)
				.sum();
	}

	/**
	 * Calculates average review time for merge requests within the date range.
	 * 
	 * @param mergeRequests list of merge requests
	 * @param dateRange the date range for filtering
	 * @return average review time in hours
	 */
	private long calculateAverageReviewTime(List<ScmMergeRequests> mergeRequests, CustomDateRange dateRange) {
		List<Long> reviewTimeList = mergeRequests.stream()
				.filter(request -> request.getPickedForReviewOn() != null)
				.filter(request -> DateUtil.isWithinDateTimeRange(
						DateUtil.convertMillisToLocalDateTime(request.getPickedForReviewOn()),
						dateRange.getStartDateTime(), dateRange.getEndDateTime()))
				.map(this::calculateReviewDuration)
				.toList();

		return reviewTimeList.isEmpty() ? 0
				: reviewTimeList.stream().mapToLong(Long::longValue).sum() / reviewTimeList.size();
	}

	/**
	 * Calculates review duration for a merge request.
	 * 
	 * @param mergeRequest the merge request
	 * @return review duration in hours
	 */
	private long calculateReviewDuration(ScmMergeRequests mergeRequest) {
		LocalDateTime pickedForReviewOn = DateUtil.convertMillisToLocalDateTime(mergeRequest.getPickedForReviewOn());
		LocalDateTime createdDate = DateUtil.convertMillisToLocalDateTime(mergeRequest.getCreatedDate());
		return Duration.between(createdDate, pickedForReviewOn).toHours();
	}

	/**
	 * Fetches SCM data from database for the specified date range.
	 * 
	 * @param dateRange the date range for filtering
	 * @param basicProjectConfigId the project configuration ID
	 * @return map containing merge requests and commits
	 */
	private Map<String, Object> fetchFromDb(CustomDateRange dateRange, ObjectId basicProjectConfigId) {
		List<ScmMergeRequests> mergeRequestsList = scmKpiHelperService.getMergeRequests(basicProjectConfigId, dateRange);
		List<ScmCommits> commitsList = scmKpiHelperService.getCommitDetails(basicProjectConfigId, dateRange);
		return Map.of(MR_LIST, mergeRequestsList, COMMIT_LIST, commitsList);
	}

	/**
	 * Calculates start and end date based on duration type and value.
	 * 
	 * @param dataPoint the duration value
	 * @param duration the duration type (WEEK or DAYS)
	 * @return custom date range with start and end dates
	 */
	private CustomDateRange getStartAndEndDate(int dataPoint, String duration) {
		CustomDateRange dateRange = new CustomDateRange();
		LocalDate endDate = DateUtil.getTodayDate();
		LocalDate startDate = CommonConstant.WEEK.equalsIgnoreCase(duration)
				? endDate.minusWeeks(dataPoint)
				: endDate.minusDays(dataPoint);

		dateRange.setStartDate(startDate);
		dateRange.setEndDate(endDate);
		return dateRange;
	}
}
