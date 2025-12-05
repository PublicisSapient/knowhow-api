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

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.TeamPerformanceSummaryService;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.ProjectFilter;
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
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Service implementation for generating team performance summaries based on SCM
 * data. Provides metrics including total PRs, lines of code, and average review
 * time.
 * 
 * @author KnowHOW
 */
@Service
@Slf4j
public class TeamPerformanceSummaryServiceImpl implements TeamPerformanceSummaryService {

	private static final String COMMIT_LIST = "commitList";
	private static final String MR_LIST = "mrList";
	private static final String HOURS_SUFFIX = " Hrs";
	private static final String METRIC_TOTAL_PRS = "Total PRs";
	private static final String METRIC_LINES_OF_CODE = "Lines of Code";
	private static final String METRIC_AVG_REVIEW_TIME = "Average Review Time";
	private static final int DURATION_INDEX = 0;

	private final ScmKpiHelperService scmKpiHelperService;
	private final KpiHelperService kpiHelperService;
	private final ConfigHelperService configHelperService;
	private final FilterHelperService filterHelperService;

	public TeamPerformanceSummaryServiceImpl(ScmKpiHelperService scmKpiHelperService, KpiHelperService kpiHelperService,
			ConfigHelperService configHelperService, FilterHelperService filterHelperService) {
		this.scmKpiHelperService = scmKpiHelperService;
		this.kpiHelperService = kpiHelperService;
		this.configHelperService = configHelperService;
		this.filterHelperService = filterHelperService;
	}

	/**
	 * Retrieves team performance summary based on SCM data for the specified
	 * project and duration.
	 * 
	 * @param kpiRequest
	 *            the KPI request containing project ID, duration, and date range
	 * @return list of performance summaries for each SCM tool/branch, or empty list
	 *         if no tools found
	 */
	public List<PerformanceSummary> getTeamPerformanceSummary(KpiRequest kpiRequest) {
		String duration = kpiRequest.getSelectedMap().get(CommonConstant.DATE).get(DURATION_INDEX);
		int durationValue = Integer.parseInt(kpiRequest.getIds()[DURATION_INDEX]);

		List<PerformanceSummary> performanceSummaries = new ArrayList<>();

		Node projectNode = getProjectNodeByNodeId(kpiRequest);

		if (projectNode != null) {

			List<Tool> scmTools = DeveloperKpiHelper.getScmToolsForProject(projectNode, configHelperService,
					kpiHelperService);
			if (CollectionUtils.isEmpty(scmTools)) {
				log.error("[BITBUCKET-AGGREGATED-VALUE]. No SCM tools found for project {}",
						projectNode.getProjectFilter().getBasicProjectConfigId());
				return Collections.emptyList();
			}

			CustomDateRange dateRange = getStartAndEndDate(durationValue, duration);
			Map<String, Object> scmDataMap = fetchFromDb(dateRange,
					projectNode.getProjectFilter().getBasicProjectConfigId());
			List<ScmCommits> allCommits = (List<ScmCommits>) scmDataMap.get(COMMIT_LIST);
			List<ScmMergeRequests> allMergeRequests = (List<ScmMergeRequests>) scmDataMap.get(MR_LIST);

			scmTools.forEach(tool -> performanceSummaries.add(buildPerformanceSummary(tool,
					projectNode.getProjectFilter().getName(), allCommits, allMergeRequests, dateRange)));
		} else {
            log.error("[BITBUCKET-AGGREGATED-VALUE]. No project found for project ID: {}", kpiRequest.getIds()[0]);
        }

		return performanceSummaries;
	}

	private Node getProjectNodeByNodeId(KpiRequest kpiRequest) {
		String groupName = filterHelperService.getHierarachyLevelId(kpiRequest.getLevel(), kpiRequest.getLabel(),
				false);
		if (null != groupName) {
			kpiRequest.setLabel(groupName.toUpperCase());
		}
		List<AccountHierarchyData> filteredAccountDataList = filterHelperService.getFilteredBuilds(kpiRequest,
				groupName);
		if (CollectionUtils.isEmpty(filteredAccountDataList)) {
			return null;
		}
		filteredAccountDataList = kpiHelperService.getAuthorizedFilteredList(kpiRequest, filteredAccountDataList,
				false);
		if (filteredAccountDataList.isEmpty()) {
			return null;
		}

		return getRequiredProjectNodeFromHierarcyList(kpiRequest, filteredAccountDataList);
	}

	private Node getRequiredProjectNodeFromHierarcyList(KpiRequest kpiRequest,
			List<AccountHierarchyData> filteredAccountDataList) {
		Node filteredNode = filteredAccountDataList.get(0).getNode().get(kpiRequest.getLevel() - 1);

		if (null != filteredNode.getProjectHierarchy()) {
			filteredNode.setProjectFilter(new ProjectFilter(filteredNode.getId(), filteredNode.getName(),
					filteredNode.getProjectHierarchy().getBasicProjectConfigId()));
		}

		return filteredNode;
	}

	/**
	 * Builds performance summary for a specific SCM tool/branch.
	 * 
	 * @param tool
	 *            the SCM tool
	 * @param projectName
	 *            the project name
	 * @param allCommits
	 *            all commits for the project
	 * @param allMergeRequests
	 *            all merge requests for the project
	 * @param dateRange
	 *            the date range for filtering
	 * @return performance summary with metrics
	 */
	private PerformanceSummary buildPerformanceSummary(Tool tool, String projectName, List<ScmCommits> allCommits,
			List<ScmMergeRequests> allMergeRequests, CustomDateRange dateRange) {
        if (!DeveloperKpiHelper.isValidTool(tool)) {
            log.error("[BITBUCKET-AGGREGATED-VALUE]. Invalid tool config");
            return null;
        }
		String branchName = DeveloperKpiHelper.getBranchSubFilter(tool, projectName);
		List<ScmCommits> commitsForBranch = DeveloperKpiHelper.filterCommitsForBranch(allCommits, tool);
		List<ScmMergeRequests> mergeRequestsForBranch = DeveloperKpiHelper
				.filterMergeRequestsForBranch(allMergeRequests, tool);

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
	 * @param commits
	 *            list of commits
	 * @return total lines of code changed
	 */
	private long calculateLinesOfCode(List<ScmCommits> commits) {
		return commits.stream().filter(commit -> !commit.getIsMergeCommit()).mapToLong(ScmCommits::getChangedLines)
				.sum();
	}

	/**
	 * Calculates average review time for merge requests within the date range.
	 * 
	 * @param mergeRequests
	 *            list of merge requests
	 * @param dateRange
	 *            the date range for filtering
	 * @return average review time in hours
	 */
	private long calculateAverageReviewTime(List<ScmMergeRequests> mergeRequests, CustomDateRange dateRange) {
		List<Long> reviewTimeList = mergeRequests.stream().filter(request -> request.getPickedForReviewOn() != null)
				.filter(request -> DateUtil.isWithinDateTimeRange(
						DateUtil.convertMillisToLocalDateTime(request.getPickedForReviewOn()),
						dateRange.getStartDateTime(), dateRange.getEndDateTime()))
				.map(this::calculateReviewDuration).toList();

		return reviewTimeList.isEmpty() ? 0
				: reviewTimeList.stream().mapToLong(Long::longValue).sum() / reviewTimeList.size();
	}

	/**
	 * Calculates review duration for a merge request.
	 * 
	 * @param mergeRequest
	 *            the merge request
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
	 * @param dateRange
	 *            the date range for filtering
	 * @param basicProjectConfigId
	 *            the project configuration ID
	 * @return map containing merge requests and commits
	 */
	private Map<String, Object> fetchFromDb(CustomDateRange dateRange, ObjectId basicProjectConfigId) {
		List<ScmMergeRequests> mergeRequestsList = scmKpiHelperService.getMergeRequests(basicProjectConfigId,
				dateRange);
		List<ScmCommits> commitsList = scmKpiHelperService.getCommitDetails(basicProjectConfigId, dateRange);
		return Map.of(MR_LIST, mergeRequestsList, COMMIT_LIST, commitsList);
	}

	/**
	 * Calculates start and end date based on duration type and value.
	 * 
	 * @param dataPoint
	 *            the duration value
	 * @param duration
	 *            the duration type (WEEK or DAYS)
	 * @return custom date range with start and end dates
	 */
	private CustomDateRange getStartAndEndDate(int dataPoint, String duration) {
		CustomDateRange dateRange = new CustomDateRange();
		LocalDateTime endDate = DateUtil.getTodayTime();
		LocalDateTime startDate = CommonConstant.WEEK.equalsIgnoreCase(duration) ? endDate.minusWeeks(dataPoint)
				: endDate.minusDays(dataPoint);
		dateRange.setStartDateTime(startDate);
		dateRange.setEndDateTime(endDate);
		dateRange.setEndDate(DateUtil.getTodayDate());
		dateRange.setStartDate(
				CommonConstant.WEEK.equalsIgnoreCase(duration) ? DateUtil.getTodayDate().minusWeeks(dataPoint)
						: DateUtil.getTodayDate().minusDays(dataPoint));
		return dateRange;
	}
}
