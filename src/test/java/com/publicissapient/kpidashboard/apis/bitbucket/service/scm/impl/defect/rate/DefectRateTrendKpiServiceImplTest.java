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

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.defect.rate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;

@ExtendWith(MockitoExtension.class)
class DefectRateTrendKpiServiceImplTest {

	@InjectMocks private DefectRateTrendKpiServiceImpl service;

	private KpiRequest kpiRequest;
	private List<ScmMergeRequests> mergeRequests;
	private List<Tool> scmTools;
	private Set<Assignee> assignees;
	private Tool tool;

	@BeforeEach
	void setUp() {
		kpiRequest = new KpiRequest();
		kpiRequest.setXAxisDataPoints(3);
		kpiRequest.setDuration("WEEKS");

		tool = new Tool();
		tool.setBranch("main");
		tool.setRepositoryName("test-repo");

		scmTools = List.of(tool);
		assignees = new HashSet<>();
		mergeRequests = new ArrayList<>();
	}

	@Test
	void testCalculateKpi_WithValidData() {
		try (MockedStatic<DeveloperKpiHelper> helperMock = mockStatic(DeveloperKpiHelper.class);
				MockedStatic<DateUtil> dateUtilMock = mockStatic(DateUtil.class);
				MockedStatic<KpiDataHelper> kpiDataHelperMock = mockStatic(KpiDataHelper.class);
				MockedStatic<KpiHelperService> kpiHelperServiceMock = mockStatic(KpiHelperService.class)) {

			LocalDateTime now = LocalDateTime.of(2024, 1, 15, 0, 0);
			CustomDateRange dateRange = new CustomDateRange();
			dateRange.setStartDateTime(now.minusDays(7));
			dateRange.setEndDateTime(now);

			dateUtilMock.when(DateUtil::getTodayTime).thenReturn(now);
			dateUtilMock.when(() -> DateUtil.convertMillisToLocalDateTime(anyLong())).thenReturn(now);
			dateUtilMock.when(() -> DateUtil.isWithinDateTimeRange(any(), any(), any())).thenReturn(true);

			kpiDataHelperMock
					.when(() -> KpiDataHelper.getStartAndEndDateTimeForDataFiltering(any(), anyString()))
					.thenReturn(dateRange);
			kpiHelperServiceMock
					.when(() -> KpiHelperService.getDateRange(any(), anyString()))
					.thenReturn("Week 1");

			helperMock.when(() -> DeveloperKpiHelper.isValidTool(any())).thenReturn(true);
			helperMock.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(), any())).thenReturn("main");
			helperMock
					.when(() -> DeveloperKpiHelper.filterMergeRequestsForBranch(any(), any()))
					.thenReturn(mergeRequests);
			helperMock
					.when(() -> DeveloperKpiHelper.groupMergeRequestsByUser(any()))
					.thenReturn(Collections.emptyMap());
			helperMock
					.when(() -> DeveloperKpiHelper.getNextRangeDate(anyString(), any()))
					.thenReturn(now.minusDays(7));
			helperMock
					.when(
							() -> DeveloperKpiHelper.setDataCount(any(), any(), any(), anyDouble(), any(), any()))
					.thenAnswer(invocation -> null);

			List<RepoToolValidationData> validationDataList = new ArrayList<>();
			Map<String, List<DataCount>> result =
					service.calculateKpi(
							kpiRequest,
							mergeRequests,
							new ArrayList<>(),
							scmTools,
							validationDataList,
							assignees,
							"TestProject");

			assertNotNull(result);
		}
	}

	@Test
	void testCalculateKpi_WithInvalidTool() {
		try (MockedStatic<DeveloperKpiHelper> helperMock = mockStatic(DeveloperKpiHelper.class);
				MockedStatic<DateUtil> dateUtilMock = mockStatic(DateUtil.class);
				MockedStatic<KpiDataHelper> kpiDataHelperMock = mockStatic(KpiDataHelper.class);
				MockedStatic<KpiHelperService> kpiHelperServiceMock = mockStatic(KpiHelperService.class)) {

			LocalDateTime now = LocalDateTime.of(2024, 1, 15, 0, 0);
			CustomDateRange dateRange = new CustomDateRange();

			dateUtilMock.when(DateUtil::getTodayTime).thenReturn(now);
			kpiDataHelperMock
					.when(() -> KpiDataHelper.getStartAndEndDateTimeForDataFiltering(any(), anyString()))
					.thenReturn(dateRange);
			kpiHelperServiceMock
					.when(() -> KpiHelperService.getDateRange(any(), anyString()))
					.thenReturn("Week 1");
			helperMock.when(() -> DeveloperKpiHelper.isValidTool(any())).thenReturn(false);
			helperMock
					.when(() -> DeveloperKpiHelper.getNextRangeDate(anyString(), any()))
					.thenReturn(now.minusDays(7));

			List<RepoToolValidationData> validationDataList = new ArrayList<>();
			Map<String, List<DataCount>> result =
					service.calculateKpi(
							kpiRequest,
							mergeRequests,
							new ArrayList<>(),
							scmTools,
							validationDataList,
							assignees,
							"TestProject");

			assertNotNull(result);
		}
	}

	@Test
	void testCalculateKpi_WithDefectMergeRequests() {
		ScmMergeRequests defectMr = createMergeRequest("Fix bug in login", System.currentTimeMillis());
		ScmMergeRequests normalMr = createMergeRequest("Add feature", System.currentTimeMillis());
		mergeRequests.add(defectMr);
		mergeRequests.add(normalMr);

		try (MockedStatic<DeveloperKpiHelper> helperMock = mockStatic(DeveloperKpiHelper.class);
				MockedStatic<DateUtil> dateUtilMock = mockStatic(DateUtil.class);
				MockedStatic<KpiDataHelper> kpiDataHelperMock = mockStatic(KpiDataHelper.class);
				MockedStatic<KpiHelperService> kpiHelperServiceMock = mockStatic(KpiHelperService.class)) {

			LocalDateTime now = LocalDateTime.of(2024, 1, 15, 0, 0);
			CustomDateRange dateRange = new CustomDateRange();
			dateRange.setStartDateTime(now.minusDays(7));
			dateRange.setEndDateTime(now);

			dateUtilMock.when(DateUtil::getTodayTime).thenReturn(now);
			dateUtilMock.when(() -> DateUtil.convertMillisToLocalDateTime(anyLong())).thenReturn(now);
			dateUtilMock.when(() -> DateUtil.isWithinDateTimeRange(any(), any(), any())).thenReturn(true);

			kpiDataHelperMock
					.when(() -> KpiDataHelper.getStartAndEndDateTimeForDataFiltering(any(), anyString()))
					.thenReturn(dateRange);
			kpiHelperServiceMock
					.when(() -> KpiHelperService.getDateRange(any(), anyString()))
					.thenReturn("Week 1");

			helperMock.when(() -> DeveloperKpiHelper.isValidTool(any())).thenReturn(true);
			helperMock.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(), any())).thenReturn("main");
			helperMock
					.when(() -> DeveloperKpiHelper.filterMergeRequestsForBranch(any(), any()))
					.thenReturn(mergeRequests);
			helperMock
					.when(() -> DeveloperKpiHelper.groupMergeRequestsByUser(any()))
					.thenReturn(Collections.emptyMap());
			helperMock
					.when(() -> DeveloperKpiHelper.getNextRangeDate(anyString(), any()))
					.thenReturn(now.minusDays(7));
			helperMock
					.when(
							() -> DeveloperKpiHelper.setDataCount(any(), any(), any(), anyDouble(), any(), any()))
					.thenAnswer(invocation -> null);

			List<RepoToolValidationData> validationDataList = new ArrayList<>();
			Map<String, List<DataCount>> result =
					service.calculateKpi(
							kpiRequest,
							mergeRequests,
							new ArrayList<>(),
							scmTools,
							validationDataList,
							assignees,
							"TestProject");

			assertNotNull(result);
		}
	}

	@Test
	void testCalculateKpi_WithUserLevelData() {
		ScmMergeRequests mr = createMergeRequest("Repair defect", System.currentTimeMillis());
		mr.setAuthorUserId("user@test.com");
		mergeRequests.add(mr);

		Map<String, List<ScmMergeRequests>> userWiseMap = Map.of("user@test.com", List.of(mr));

		try (MockedStatic<DeveloperKpiHelper> helperMock = mockStatic(DeveloperKpiHelper.class);
				MockedStatic<DateUtil> dateUtilMock = mockStatic(DateUtil.class);
				MockedStatic<KpiDataHelper> kpiDataHelperMock = mockStatic(KpiDataHelper.class);
				MockedStatic<KpiHelperService> kpiHelperServiceMock = mockStatic(KpiHelperService.class)) {

			LocalDateTime now = LocalDateTime.of(2024, 1, 15, 0, 0);
			CustomDateRange dateRange = new CustomDateRange();
			dateRange.setStartDateTime(now.minusDays(7));
			dateRange.setEndDateTime(now);

			dateUtilMock.when(DateUtil::getTodayTime).thenReturn(now);
			dateUtilMock.when(() -> DateUtil.convertMillisToLocalDateTime(anyLong())).thenReturn(now);
			dateUtilMock.when(() -> DateUtil.isWithinDateTimeRange(any(), any(), any())).thenReturn(true);

			kpiDataHelperMock
					.when(() -> KpiDataHelper.getStartAndEndDateTimeForDataFiltering(any(), anyString()))
					.thenReturn(dateRange);
			kpiHelperServiceMock
					.when(() -> KpiHelperService.getDateRange(any(), anyString()))
					.thenReturn("Week 1");

			helperMock.when(() -> DeveloperKpiHelper.isValidTool(any())).thenReturn(true);
			helperMock.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(), any())).thenReturn("main");
			helperMock
					.when(() -> DeveloperKpiHelper.filterMergeRequestsForBranch(any(), any()))
					.thenReturn(mergeRequests);
			helperMock
					.when(() -> DeveloperKpiHelper.groupMergeRequestsByUser(any()))
					.thenReturn(userWiseMap);
			helperMock
					.when(() -> DeveloperKpiHelper.getDeveloperName(anyString(), any()))
					.thenReturn("Test User");
			helperMock
					.when(() -> DeveloperKpiHelper.getNextRangeDate(anyString(), any()))
					.thenReturn(now.minusDays(7));
			helperMock
					.when(
							() -> DeveloperKpiHelper.setDataCount(any(), any(), any(), anyDouble(), any(), any()))
					.thenAnswer(invocation -> null);

			List<RepoToolValidationData> validationDataList = new ArrayList<>();
			Map<String, List<DataCount>> result =
					service.calculateKpi(
							kpiRequest,
							mergeRequests,
							new ArrayList<>(),
							scmTools,
							validationDataList,
							assignees,
							"TestProject");

			assertNotNull(result);
			assertFalse(validationDataList.isEmpty());
		}
	}

	@Test
	void testGetStrategyType() {
		assertEquals("DEFECT_RATE_TREND", service.getStrategyType());
	}

	private ScmMergeRequests createMergeRequest(String title, long updatedDate) {
		ScmMergeRequests mr = new ScmMergeRequests();
		mr.setTitle(title);
		mr.setUpdatedDate(updatedDate);
		return mr;
	}
}
