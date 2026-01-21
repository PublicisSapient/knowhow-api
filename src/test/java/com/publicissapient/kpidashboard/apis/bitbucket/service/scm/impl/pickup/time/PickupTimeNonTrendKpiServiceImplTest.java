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

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.pickup.time;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.time.LocalDateTime;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.apis.model.IterationKpiValue;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;

@ExtendWith(MockitoExtension.class)
class PickupTimeNonTrendKpiServiceImplTest {

	@InjectMocks private PickupTimeNonTrendKpiServiceImpl service;

	private KpiRequest kpiRequest;
	private List<ScmMergeRequests> mergeRequests;
	private List<ScmCommits> commits;
	private List<Tool> scmTools;
	private Set<Assignee> assignees;
	private Tool tool;

	@BeforeEach
	void setUp() {
		kpiRequest = new KpiRequest();
		kpiRequest.setXAxisDataPoints(5);
		kpiRequest.setDuration("WEEKS");

		tool = new Tool();
		tool.setBranch("main");
		tool.setRepositoryName("test-repo");

		scmTools = List.of(tool);
		assignees = new HashSet<>();
		mergeRequests = new ArrayList<>();
		commits = new ArrayList<>();
	}

	@Test
	void testCalculateKpi_WithValidData() {
		try (MockedStatic<DeveloperKpiHelper> helperMock = mockStatic(DeveloperKpiHelper.class);
				MockedStatic<DateUtil> dateUtilMock = mockStatic(DateUtil.class)) {

			LocalDateTime now = LocalDateTime.of(2024, 1, 15, 0, 0);
			dateUtilMock.when(DateUtil::getTodayTime).thenReturn(now);
			helperMock.when(() -> DeveloperKpiHelper.isValidTool(any(Tool.class))).thenReturn(true);
			helperMock
					.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), any(String.class)))
					.thenReturn("main");
			helperMock
					.when(
							() ->
									DeveloperKpiHelper.filterMergeRequestsForBranch(any(List.class), any(Tool.class)))
					.thenReturn(mergeRequests);
			helperMock
					.when(() -> DeveloperKpiHelper.groupMergeRequestsByUser(any(List.class)))
					.thenReturn(Collections.emptyMap());

			List<RepoToolValidationData> validationDataList = new ArrayList<>();
			List<IterationKpiValue> result =
					service.calculateKpi(
							kpiRequest,
							mergeRequests,
							commits,
							scmTools,
							validationDataList,
							assignees,
							"TestProject");

			assertNotNull(result);
		}
	}

	@Test
	void testCalculateKpi_WithInvalidTool() {
		try (MockedStatic<DeveloperKpiHelper> helperMock = mockStatic(DeveloperKpiHelper.class)) {
			helperMock.when(() -> DeveloperKpiHelper.isValidTool(any(Tool.class))).thenReturn(false);

			List<RepoToolValidationData> validationDataList = new ArrayList<>();
			List<IterationKpiValue> result =
					service.calculateKpi(
							kpiRequest,
							mergeRequests,
							commits,
							scmTools,
							validationDataList,
							assignees,
							"TestProject");

			assertNotNull(result);
			assertTrue(result.isEmpty());
		}
	}

	@Test
	void testCalculateKpi_WithPickedRequests() {
		ScmMergeRequests mr =
				createMergeRequest(
						LocalDateTime.of(2024, 1, 10, 10, 0), LocalDateTime.of(2024, 1, 11, 14, 0));
		mergeRequests.add(mr);

		try (MockedStatic<DeveloperKpiHelper> helperMock = mockStatic(DeveloperKpiHelper.class);
				MockedStatic<DateUtil> dateUtilMock = mockStatic(DateUtil.class)) {

			LocalDateTime now = LocalDateTime.of(2024, 1, 15, 0, 0);
			dateUtilMock.when(DateUtil::getTodayTime).thenReturn(now);
			dateUtilMock
					.when(() -> DateUtil.convertMillisToLocalDateTime(any(Long.class)))
					.thenAnswer(
							inv ->
									LocalDateTime.ofEpochSecond(
											inv.getArgument(0, Long.class) / 1000, 0, java.time.ZoneOffset.UTC));
			dateUtilMock
					.when(
							() ->
									DateUtil.isWithinDateTimeRange(
											any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class)))
					.thenReturn(true);

			helperMock.when(() -> DeveloperKpiHelper.isValidTool(any(Tool.class))).thenReturn(true);
			helperMock
					.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), any(String.class)))
					.thenReturn("main");
			helperMock
					.when(
							() ->
									DeveloperKpiHelper.filterMergeRequestsForBranch(any(List.class), any(Tool.class)))
					.thenReturn(mergeRequests);
			helperMock
					.when(() -> DeveloperKpiHelper.groupMergeRequestsByUser(any(List.class)))
					.thenReturn(Collections.emptyMap());

			List<RepoToolValidationData> validationDataList = new ArrayList<>();
			List<IterationKpiValue> result =
					service.calculateKpi(
							kpiRequest,
							mergeRequests,
							commits,
							scmTools,
							validationDataList,
							assignees,
							"TestProject");

			assertNotNull(result);
			assertFalse(result.isEmpty());
		}
	}

	@Test
	void testGetStrategyType() {
		assertEquals("PICKUP_TIME_NON_TREND", service.getStrategyType());
	}

	private ScmMergeRequests createMergeRequest(
			LocalDateTime createdDate, LocalDateTime pickedForReviewOn) {
		ScmMergeRequests mr = new ScmMergeRequests();
		mr.setCreatedDate(
				createdDate.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
		mr.setPickedForReviewOn(
				pickedForReviewOn.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
		mr.setState("OPEN");
		mr.setMergeRequestUrl("https://example.com/mr/1");
		return mr;
	}
}
