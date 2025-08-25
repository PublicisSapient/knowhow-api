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

import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
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

import java.time.DayOfWeek;
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
 * @author shi6
 */
@Slf4j
public final class DeveloperKpiHelper {

	private DeveloperKpiHelper() {
	}

	public static final int MILLISECONDS_IN_A_DAY = 86_399_999;

	public static List<DataCountGroup> prepareDataCountGroups(Map<String, List<DataCount>> trendValuesMap) {
		return trendValuesMap.entrySet().stream().map(entry -> {
			String[] filters = entry.getKey().split("#");
			DataCountGroup group = new DataCountGroup();
			group.setFilter1(filters[0]);
			group.setFilter2(filters[1]);
			group.setValue(entry.getValue());
			return group;
		}).collect(Collectors.toList());
	}

	/**
	 * Returns the next range date based on the duration and current date.
	 *
	 * @param duration
	 *            the duration (e.g., "WEEK" or "DAY")
	 * @param currentDate
	 *            the current date
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

	public static List<ScmCommits> filterCommitsByDate(List<ScmCommits> commits, CustomDateRange dateRange) {
		return commits.stream()
				.filter(commit -> DateUtil.isWithinDateTimeRange(
						DateUtil.convertMillisToLocalDateTime(commit.getCommitTimestamp()),
						dateRange.getStartDateTime(), dateRange.getEndDateTime()))
				.toList();
	}

	public static List<ScmMergeRequests> filterMergeRequestsByDate(List<ScmMergeRequests> mergeRequests,
			CustomDateRange dateRange) {
		return mergeRequests.stream().filter(request -> request.getMergedAt() != null).filter(request -> DateUtil
				.isWithinDateTimeRange(request.getMergedAt(), dateRange.getStartDateTime(), dateRange.getEndDateTime()))
				.toList();
	}

	public static List<ScmMergeRequests> filterMergeRequestsForBranch(List<ScmMergeRequests> mergeRequests, Tool tool) {
		return mergeRequests.stream().filter(request -> request.getRepositoryName().equals(tool.getRepositoryName()))
				.filter(request -> request.getToBranch().equals(tool.getBranch())).toList();
	}

	public static Map<String, List<ScmMergeRequests>> groupMergeRequestsByUser(List<ScmMergeRequests> mergeRequests) {
		return mergeRequests.stream().filter(req -> req.getAuthorId() != null && req.getAuthorId().getEmail() != null)
				.collect(Collectors.groupingBy(request -> request.getAuthorId().getEmail()));
	}

	public static String getDeveloperName(String userEmail, Set<Assignee> assignees) {
		Optional<Assignee> assignee = assignees.stream()
				.filter(a -> CollectionUtils.isNotEmpty(a.getEmail()) && a.getEmail().contains(userEmail)).findFirst();
		return assignee.map(Assignee::getAssigneeName).orElse(userEmail);
	}

	public static boolean isValidTool(Tool tool) {
		return !CollectionUtils.isEmpty(tool.getProcessorItemList())
				&& tool.getProcessorItemList().get(0).getId() != null;
	}

	public static void setDataCount(String projectName, String dateLabel, String kpiGroup, double value,
			Map<String, Object> hoverValue, Map<String, List<DataCount>> dataCountMap) {
		List<DataCount> dataCounts = dataCountMap.computeIfAbsent(kpiGroup, k -> new ArrayList<>());
		Optional<DataCount> existingDataCount = dataCounts.stream()
				.filter(dataCount -> dataCount.getDate().equals(dateLabel)).findFirst();

		if (existingDataCount.isPresent()) {
			DataCount updatedDataCount = existingDataCount.get();
			Number currentValue = (Number) updatedDataCount.getValue();
			updatedDataCount.setValue(currentValue.doubleValue() + value);
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

	public static void setDataCount(String projectName, String dateLabel, String kpiGroup, long value,
			Map<String, Object> hoverValue, Map<String, List<DataCount>> dataCountMap) {
		setDataCount(projectName, dateLabel, kpiGroup, (double) value, hoverValue, dataCountMap);
	}
}
