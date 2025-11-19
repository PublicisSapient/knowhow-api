/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.util;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.ProjectFilter;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ObjectUtils;
import org.bson.types.ObjectId;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The class contains all required common methods for developer kpis
 *
 * @author shunaray
 */
@Slf4j
public final class DeveloperKpiHelper {

    private static final String CONNECTOR = " -> ";

	private DeveloperKpiHelper() {}

	/**
	 * Transforms a map of trend values into structured DataCountGroup objects. Splits the map key by
	 * "#" to extract filter values.
	 *
	 * @param trendValuesMap Map with keys in format "filter1#filter2"
	 * @return List of DataCountGroup objects with separated filters
	 */
	public static List<DataCountGroup> prepareDataCountGroups(
			Map<String, List<DataCount>> trendValuesMap) {
		return trendValuesMap.entrySet().stream()
				.map(
						entry -> {
							String[] filters = entry.getKey().split("#");
							DataCountGroup group = new DataCountGroup();
							group.setFilter1(filters[0]);
							group.setFilter2(filters[1]);
							group.setValue(entry.getValue());
							return group;
						})
				.collect(Collectors.toList());
	}

	/**
	 * Returns the next range date based on the duration and current date.
	 *
	 * @param duration the duration (e.g., "WEEK" or "DAY")
	 * @param currentDate the current date
	 * @return the next range date
	 */
	public static LocalDateTime getNextRangeDate(String duration, LocalDateTime currentDate) {
		if (CommonConstant.WEEK.equalsIgnoreCase(duration)) {
			return currentDate.minusWeeks(1);
		}
		LocalDateTime nextDate = currentDate.minusDays(1);
		DayOfWeek day = nextDate.getDayOfWeek();
		if (day == DayOfWeek.SATURDAY) {
			return nextDate.minusDays(1);
		}
		if (day == DayOfWeek.SUNDAY) {
			return nextDate.minusDays(2);
		}
		return nextDate;
	}

	/**
	 * Filters SCM commits within a specified date range. Converts commit timestamps from milliseconds
	 * to LocalDateTime.
	 *
	 * @param commits List of SCM commits
	 * @param dateRange Date range for filtering
	 * @return Filtered list of commits within the date range
	 */
	public static List<ScmCommits> filterCommitsByCommitTimeStamp(
			List<ScmCommits> commits, CustomDateRange dateRange) {
		return commits.stream()
				.filter(
						commit ->
								DateUtil.isWithinDateTimeRange(
										DateUtil.convertMillisToLocalDateTime(commit.getCommitTimestamp()),
										dateRange.getStartDateTime(),
										dateRange.getEndDateTime()))
				.toList();
	}

	/**
	 * Filters merge requests by updated date within a date range.
	 *
	 * @param mergeRequests List of merge requests
	 * @param dateRange Date range for filtering
	 * @return Filtered merge requests with non-null updated dates
	 */
	public static List<ScmMergeRequests> filterMergeRequestsByUpdateDate(
			List<ScmMergeRequests> mergeRequests, CustomDateRange dateRange) {
		return mergeRequests.stream()
				.filter(request -> request.getUpdatedDate() != null)
				.filter(
						request -> {
							LocalDateTime updatedDateTime =
									DateUtil.convertMillisToLocalDateTime(request.getUpdatedDate());
							return DateUtil.isWithinDateTimeRange(
									updatedDateTime, dateRange.getStartDateTime(), dateRange.getEndDateTime());
						})
				.toList();
	}

	/**
	 * Retrieves SCM tools configured for a specific project. Looks up tools by project configuration
	 * ID.
	 *
	 * @param projectNode Project node containing project filter
	 * @param configHelperService Service for configuration management
	 * @param kpiHelperService Service for KPI operations
	 * @return List of SCM tools for the project
	 */
	public static List<Tool> getScmToolsForProject(
			Node projectNode,
			ConfigHelperService configHelperService,
			KpiHelperService kpiHelperService) {
		Map<ObjectId, Map<String, List<Tool>>> toolMap = configHelperService.getToolItemMap();
		ObjectId projectConfigId =
				Optional.ofNullable(projectNode.getProjectFilter())
						.map(ProjectFilter::getBasicProjectConfigId)
						.orElse(null);

		Map<String, List<Tool>> toolListMap = toolMap.getOrDefault(projectConfigId, Map.of());
		return kpiHelperService.populateSCMToolsRepoList(toolListMap);
	}

	/**
	 * Filters merge requests for a specific tool/branch. Matches by processor item ID.
	 *
	 * @param mergeRequests List of merge requests
	 * @param tool Tool containing processor item information
	 * @return Filtered merge requests for the tool
	 */
	public static List<ScmMergeRequests> filterMergeRequestsForBranch(
			List<ScmMergeRequests> mergeRequests, Tool tool) {
		return mergeRequests.stream()
				.filter(
						request ->
								request.getProcessorItemId().equals(tool.getProcessorItemList().get(0).getId()))
				.toList();
	}

	public static List<ScmCommits> filterCommitsForBranch(List<ScmCommits> commitsList, Tool tool) {
		return commitsList.stream()
				.filter(
						commits ->
								commits.getProcessorItemId().equals(tool.getProcessorItemList().get(0).getId()))
				.toList();
	}

	/**
	 * Groups merge requests by author email. Filters out requests with null author or email.
	 *
	 * @param mergeRequests List of merge requests
	 * @return Map of email to list of merge requests
	 */
	public static Map<String, List<ScmMergeRequests>> groupMergeRequestsByUser(
			List<ScmMergeRequests> mergeRequests) {
		return mergeRequests.stream()
				.filter(
						req ->
								req.getAuthorId() != null
										&& (req.getAuthorId().getEmail() != null
												|| req.getAuthorId().getUsername() != null))
				.collect(
						Collectors.groupingBy(
								request ->
										request.getAuthorId().getEmail() != null
												? request.getAuthorId().getEmail()
												: request.getAuthorId().getUsername()));
	}

	public static Map<String, List<ScmCommits>> groupCommitsByUser(List<ScmCommits> commits) {
		return commits.stream()
				.filter(
						commit ->
								commit.getCommitAuthor() != null
										&& (commit.getCommitAuthor().getEmail() != null
												|| commit.getCommitAuthor().getUsername() != null))
				.collect(
						Collectors.groupingBy(
								commit ->
										commit.getCommitAuthor().getEmail() != null
												? commit.getCommitAuthor().getEmail()
												: commit.getCommitAuthor().getUsername()));
	}

	/**
	 * Resolves developer name from email using assignee mapping. Falls back to email if no assignee
	 * found.
	 *
	 * @param userEmail Developer's email address
	 * @param assignees Set of assignee mappings
	 * @return Developer name or email as fallback
	 */
	public static String getDeveloperName(String userEmail, Set<Assignee> assignees) {
		Optional<Assignee> assignee =
				assignees.stream()
						.filter(
								a -> CollectionUtils.isNotEmpty(a.getEmail()) && a.getEmail().contains(userEmail))
						.findFirst();
		return assignee.map(Assignee::getAssigneeName).orElse(userEmail);
	}

	/**
	 * Validates if a tool has proper processor configuration. Checks for non-empty processor list
	 * with valid ID.
	 *
	 * @param tool Tool to validate
	 * @return true if tool has valid processor configuration
	 */
	public static boolean isValidTool(Tool tool) {
		return !CollectionUtils.isEmpty(tool.getProcessorItemList())
				&& tool.getProcessorItemList().get(0).getId() != null;
	}

	/**
	 * Creates or updates data count entries for KPI metrics. Supports both Long and Double value
	 * types with proper aggregation.
	 *
	 * @param projectName Project name for the data
	 * @param dateLabel Date label for the data point
	 * @param kpiGroup KPI group identifier
	 * @param value Numeric value (Long or Double)
	 * @param hoverValue Additional hover information
	 * @param dataCountMap Map to store/update data counts
	 */
	public static void setDataCount(String projectName, String dateLabel, String kpiGroup, Number value,
			Map<String, Object> hoverValue, Map<String, List<DataCount>> dataCountMap) {
		List<DataCount> dataCounts = dataCountMap.computeIfAbsent(kpiGroup, k -> new ArrayList<>());
		Optional<DataCount> existingDataCount = dataCounts.stream()
				.filter(dataCount -> dataCount.getDate().equals(dateLabel)).findFirst();

		if (existingDataCount.isPresent()) {
			DataCount updatedDataCount = existingDataCount.get();
			if (value instanceof Long) {
				updatedDataCount.setValue(((Number) updatedDataCount.getValue()).longValue() + value.longValue());
			} else if (value instanceof Double) {
				updatedDataCount.setValue(((Number) updatedDataCount.getValue()).doubleValue() + value.doubleValue());
			}
		} else {
			DataCount newDataCount = new DataCount();
			newDataCount.setData(String.valueOf(value));
			newDataCount.setSProjectName(projectName);
			newDataCount.setDate(dateLabel);
			newDataCount.setValue(value);
			newDataCount.setKpiGroup(kpiGroup);
			newDataCount.setHoverValue(hoverValue);
			dataCounts.add(newDataCount);
		}
	}

	public static LocalDate convertStringToDate(String dateString) {
		return LocalDate.parse(dateString.split("T")[0]);
	}

	public static CustomDateRange getStartAndEndDate(KpiRequest kpiRequest) {
		int dataPoint = (int) ObjectUtils.defaultIfNull(kpiRequest.getXAxisDataPoints(), 5) + 1;
		CustomDateRange cdr = new CustomDateRange();
		cdr.setEndDate(DateUtil.getTodayDate());
		LocalDate startDate = null;
		if (kpiRequest.getDuration().equalsIgnoreCase(CommonConstant.WEEK)) {
			startDate = DateUtil.getTodayDate().minusWeeks(dataPoint * 2L);
		} else {
			startDate = DateUtil.getTodayDate().minusDays(dataPoint * 2L);
		}
		cdr.setStartDate(startDate);
		return cdr;
	}

    /**
     * This method creates branch filters for kpis
     *
     * @param repo tool repo
     * @param projectName projectName
     * @return branch filter
     */
    public static String getBranchSubFilter(Tool repo, String projectName) {
        String subfilter = "";
        if (null != repo.getRepoSlug()) {
            subfilter = repo.getBranch() + CONNECTOR + repo.getRepoSlug() + CONNECTOR + projectName;
        } else if (null != repo.getRepositoryName()) {
            subfilter = repo.getBranch() + CONNECTOR + repo.getRepositoryName() + CONNECTOR + projectName;
        } else {
            subfilter = repo.getBranch() + CONNECTOR + projectName;
        }
        return subfilter;
    }
}
