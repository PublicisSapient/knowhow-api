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

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.jira.AssigneeDetails;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.repository.jira.AssigneeDetailsRepository;
import com.publicissapient.kpidashboard.common.repository.scm.ScmCommitsRepository;
import com.publicissapient.kpidashboard.common.repository.scm.ScmMergeRequestsRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@AllArgsConstructor
public class ScmKpiHelperServiceImpl implements ScmKpiHelperService {

	private final KpiHelperService kpiHelperService;
	private final ConfigHelperService configHelperService;
	private final ScmCommitsRepository scmCommitsRepository;
	private final AssigneeDetailsRepository assigneeDetailsRepository;
	private final ScmMergeRequestsRepository scmMergeRequestsRepository;

	@Override
	public List<ScmCommits> getCommitDetails(ObjectId projectBasicConfigId, CustomDateRange dateRange) {
		validateInputs(projectBasicConfigId, dateRange);

		BasicDBList commitFilter = buildProcessorItemFilter(projectBasicConfigId);

		long startMillis = convertToEpochMillis(dateRange.getStartDate().atStartOfDay());
		long endMillis = convertToEpochMillis(dateRange.getEndDate().atStartOfDay().plusDays(1));

		return scmCommitsRepository.findCommitList(startMillis, endMillis, commitFilter);
	}

	@Override
	public List<ScmMergeRequests> getMergeRequests(ObjectId projectBasicConfigId, CustomDateRange dateRange) {
		validateInputs(projectBasicConfigId, dateRange);

		BasicDBList mergeFilter = buildProcessorItemFilter(projectBasicConfigId);

		long startMillis = convertToEpochMillis(dateRange.getStartDate().atStartOfDay());
		long endMillis = convertToEpochMillis(dateRange.getEndDate().atStartOfDay().plusDays(1));

		return scmMergeRequestsRepository.findMergeList(startMillis, endMillis, mergeFilter);
	}

	@Override
	public List<Assignee> getScmUsers(ObjectId projectBasicConfigId) {
		if (projectBasicConfigId == null) {
			throw new IllegalArgumentException("projectBasicConfigId cannot be null");
		}

		AssigneeDetails assigneeDetails = assigneeDetailsRepository
				.findByBasicProjectConfigId(projectBasicConfigId.toString());

		if (assigneeDetails == null || assigneeDetails.getAssignee() == null) {
			log.debug("No assignee details found for project: {}", projectBasicConfigId);
			return List.of();
		}

		return List.copyOf(assigneeDetails.getAssignee());
	}

	/**
	 * Builds a MongoDB filter for processor items based on SCM tools configured for
	 * the project.
	 *
	 * @param projectBasicConfigId
	 *            Project configuration ID
	 * @return BasicDBList filter for processor items
	 */
	private BasicDBList buildProcessorItemFilter(ObjectId projectBasicConfigId) {
		Map<ObjectId, Map<String, List<Tool>>> toolMap = configHelperService.getToolItemMap();
		Map<String, List<Tool>> toolListMap = toolMap.getOrDefault(projectBasicConfigId, Map.of());
		List<Tool> scmTools = kpiHelperService.populateSCMToolsRepoList(toolListMap);

		if (scmTools.isEmpty()) {
			log.warn("No SCM tools found for project: {}", projectBasicConfigId);
		}

		BasicDBList filter = new BasicDBList();
		for (Tool tool : scmTools) {
			if (CollectionUtils.isNotEmpty(tool.getProcessorItemList())) {
				ObjectId processorItemId = tool.getProcessorItemList().get(0).getId();
				filter.add(new BasicDBObject("processorItemId", processorItemId));
			} else {
				log.debug("Tool has no processor items: {}", tool);
			}
		}
		return filter;
	}

	/**
	 * Converts the given LocalDateTime to epoch milliseconds in UTC.
	 *
	 * @param dateTime
	 *            the LocalDateTime to convert
	 * @return the epoch milliseconds representation in UTC
	 */
	private long convertToEpochMillis(LocalDateTime dateTime) {
		return DateUtil.localDateTimeToUTC(dateTime).toInstant(ZoneOffset.UTC).toEpochMilli();
	}

	/**
	 * Validates the input parameters.
	 *
	 * @param projectBasicConfigId
	 *            the project configuration ID to validate; must not be null
	 * @param dateRange
	 *            the custom date range to validate; must not be null and must have
	 *            valid start and end dates
	 * @throws IllegalArgumentException
	 *             if any input is null or if the start date is after the end date
	 */
	private void validateInputs(ObjectId projectBasicConfigId, CustomDateRange dateRange) {
		if (projectBasicConfigId == null) {
			throw new IllegalArgumentException("projectBasicConfigId cannot be null");
		}
		if (dateRange == null || dateRange.getStartDate() == null || dateRange.getEndDate() == null) {
			throw new IllegalArgumentException("dateRange and its dates cannot be null");
		}
		if (dateRange.getStartDate().isAfter(dateRange.getEndDate())) {
			throw new IllegalArgumentException("Start date cannot be after end date");
		}
	}
}
