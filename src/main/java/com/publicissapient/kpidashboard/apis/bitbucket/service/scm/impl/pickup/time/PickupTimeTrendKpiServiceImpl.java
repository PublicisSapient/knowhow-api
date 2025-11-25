package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.pickup.time;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service implementation for calculating Pickup Time KPI in trend mode.
 * Tracks pickup time over multiple time periods to show trends.
 */
@Slf4j
@Component
public class PickupTimeTrendKpiServiceImpl extends AbstractKpiCalculationStrategy<Map<String, List<DataCount>>> {

	private static final String MR_COUNT = "No of PRs";
	private static final double MILLIS_PER_SECOND = 1000.0;

	/**
	 * Calculates Pickup Time KPI trend data across multiple time periods.
	 *
	 * @param kpiRequest         the KPI request containing filters and parameters
	 * @param mergeRequests      list of merge requests
	 * @param commits            list of SCM commits (unused in this implementation)
	 * @param scmTools           list of SCM tools to process
	 * @param validationDataList list to populate with validation data
	 * @param assignees          set of assignees for developer mapping
	 * @param projectName        name of the project
	 * @return map of KPI group to trend data
	 */
	@Override
	public Map<String, List<DataCount>> calculateKpi(KpiRequest kpiRequest, List<ScmMergeRequests> mergeRequests, List<ScmCommits> commits, List<Tool> scmTools,
													 List<RepoToolValidationData> validationDataList, Set<Assignee> assignees, String projectName) {
		LocalDateTime currentDate = DateUtil.getTodayTime();
		int dataPoints = kpiRequest.getXAxisDataPoints();
		String duration = kpiRequest.getDuration();

		Map<String, List<DataCount>> kpiTrendDataByGroup = new LinkedHashMap<>();

		for (int i = 0; i < dataPoints; i++) {
			CustomDateRange periodRange = KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration);
			String dateLabel = KpiHelperService.getDateRange(periodRange, duration);
			List<ScmMergeRequests> filteredMergeRequests = filterPickedRequestsByDateRange(mergeRequests, periodRange);

			scmTools.forEach(tool -> processToolData(tool, filteredMergeRequests, assignees, kpiTrendDataByGroup,
					validationDataList, dateLabel, projectName));

			currentDate = DeveloperKpiHelper.getNextRangeDate(duration, currentDate);
		}
		return kpiTrendDataByGroup;
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
	 * Processes data for a single SCM tool and calculates pickup times.
	 *
	 * @param tool                  the SCM tool to process
	 * @param mergeRequests         list of merge requests for the period
	 * @param assignees             set of assignees
	 * @param kpiTrendDataByGroup   map to populate with trend data
	 * @param validationDataList    list to populate with validation data
	 * @param dateLabel             label for the time period
	 * @param projectName           name of the project
	 */
	private void processToolData(Tool tool, List<ScmMergeRequests> mergeRequests, Set<Assignee> assignees,
			Map<String, List<DataCount>> kpiTrendDataByGroup, List<RepoToolValidationData> validationDataList,
			String dateLabel, String projectName) {
		if (!DeveloperKpiHelper.isValidTool(tool)) {
			return;
		}

		String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);
		List<ScmMergeRequests> matchingRequests = DeveloperKpiHelper.filterMergeRequestsForBranch(mergeRequests, tool);

		long averagePickUpTime = calculateAveragePickupTime(matchingRequests);
		long totalMergeRequests = matchingRequests.size();

		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;
		DeveloperKpiHelper.setDataCount(projectName, dateLabel, overallKpiGroup, averagePickUpTime,
				Map.of(MR_COUNT, totalMergeRequests), kpiTrendDataByGroup);

		Map<String, List<ScmMergeRequests>> userWiseMergeRequests = DeveloperKpiHelper.groupMergeRequestsByUser(matchingRequests);
		validationDataList.addAll(prepareUserValidationData(userWiseMergeRequests, assignees, tool, projectName,
				dateLabel, kpiTrendDataByGroup));
	}

	/**
	 * Calculates average pickup time for a list of merge requests.
	 *
	 * @param mergeRequests list of merge requests
	 * @return average pickup time in hours
	 */
	private long calculateAveragePickupTime(List<ScmMergeRequests> mergeRequests) {
		List<Long> pickUpTimes = mergeRequests.stream()
				.map(this::calculatePickupTimeHours)
				.toList();
		return pickUpTimes.isEmpty() ? 0 : (long) pickUpTimes.stream().mapToLong(Long::longValue).average().orElse(0);
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
	 * Prepares validation data for each user/developer.
	 *
	 * @param userWiseMergeRequests map of user email to their merge requests
	 * @param assignees             set of assignees
	 * @param tool                  the SCM tool
	 * @param projectName           name of the project
	 * @param dateLabel             label for the time period
	 * @param kpiTrendDataByGroup   map to populate with user-level trend data
	 * @return list of validation data for all users
	 */
	private List<RepoToolValidationData> prepareUserValidationData(
			Map<String, List<ScmMergeRequests>> userWiseMergeRequests, Set<Assignee> assignees, Tool tool,
			String projectName, String dateLabel, Map<String, List<DataCount>> kpiTrendDataByGroup) {
		String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);
		return userWiseMergeRequests.entrySet().stream().map(entry -> {
			String userEmail = entry.getKey();
			List<ScmMergeRequests> userMergeRequests = entry.getValue();
			String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);

			long totalPickUpTime = userMergeRequests.stream()
					.mapToLong(this::calculatePickupTimeHours)
					.sum();
			long userMrCount = userMergeRequests.size();

			String userKpiGroup = branchName + "#" + developerName;
			DeveloperKpiHelper.setDataCount(projectName, dateLabel, userKpiGroup, totalPickUpTime,
					Map.of(MR_COUNT, userMrCount), kpiTrendDataByGroup);

			return userMergeRequests.stream()
					.filter(mr -> mr.getCreatedDate() != null && mr.getPickedForReviewOn() != null)
					.map(mr -> createValidationData(projectName, tool, developerName, dateLabel, mr))
					.toList();
		}).flatMap(List::stream).toList();
	}

	/**
	 * Creates validation data object for a merge request.
	 *
	 * @param projectName   name of the project
	 * @param tool          the SCM tool
	 * @param developerName name of the developer
	 * @param dateLabel     label for the time period
	 * @param mergeRequest  the merge request
	 * @return populated validation data object
	 */
	private RepoToolValidationData createValidationData(String projectName, Tool tool, String developerName,
			String dateLabel, ScmMergeRequests mergeRequest) {
		RepoToolValidationData validationData = new RepoToolValidationData();
		validationData.setProjectName(projectName);
		validationData.setBranchName(tool.getBranch());
		validationData.setRepoUrl(tool.getRepositoryName() != null ? tool.getRepositoryName() : tool.getRepoSlug());
		validationData.setDeveloperName(developerName);
		validationData.setDate(dateLabel);
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
		return "PICKUP_TIME_TREND";
	}
}
