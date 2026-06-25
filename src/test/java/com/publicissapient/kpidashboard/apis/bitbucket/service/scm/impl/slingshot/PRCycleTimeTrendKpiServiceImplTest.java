package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.slingshot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mockStatic;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import org.bson.types.ObjectId;
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
import com.publicissapient.kpidashboard.common.model.generic.ProcessorItem;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;

@ExtendWith(MockitoExtension.class)
class PRCycleTimeTrendKpiServiceImplTest {

	@InjectMocks private PRCycleTimeTrendKpiServiceImpl service;

	private Tool tool;
	private Set<Assignee> assignees;
	private Map<String, List<ScmMergeRequests>> userWiseMergeRequests;
	private Map<String, List<DataCount>> kpiTrendDataByGroup;
	private ObjectId processorItemId;

	@BeforeEach
	void setUp() {
		processorItemId = new ObjectId();
		tool = new Tool();
		tool.setBranch("main");
		tool.setRepositoryName("test-repo");

		ProcessorItem processorItem = new ProcessorItem();
		processorItem.setId(processorItemId);
		tool.setProcessorItemList(List.of(processorItem));

		assignees = new HashSet<>();
		userWiseMergeRequests = new HashMap<>();
		kpiTrendDataByGroup = new LinkedHashMap<>();
	}

	@Test
	void testGetStrategyType() {
		assertEquals("PR_CYCLE_TIME_SLINGSHOT_TREND", service.getStrategyType());
	}

	@Test
	void testCalculateKpi_emptyMergeRequestsAndTools() {
		KpiRequest kpiRequest = buildKpiRequest(2, "WEEK");
		List<RepoToolValidationData> validationDataList = new ArrayList<>();

		try (MockedStatic<DateUtil> dateUtilMock = mockStatic(DateUtil.class);
				MockedStatic<KpiDataHelper> kpiDataHelperMock = mockStatic(KpiDataHelper.class);
				MockedStatic<KpiHelperService> kpiHelperMock = mockStatic(KpiHelperService.class);
				MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class)) {

			LocalDateTime today = LocalDateTime.of(2024, 6, 10, 0, 0);
			dateUtilMock.when(DateUtil::getTodayTime).thenReturn(today);

			CustomDateRange range = buildDateRange(LocalDate.of(2024, 6, 3), LocalDate.of(2024, 6, 9));
			kpiDataHelperMock
					.when(() -> KpiDataHelper.getStartAndEndDateTimeForDataFiltering(any(), anyString()))
					.thenReturn(range);
			kpiHelperMock
					.when(() -> KpiHelperService.getDateRange(any(), anyString()))
					.thenReturn("2024-06-03 to 2024-06-09");
			dateUtilMock
					.when(() -> DateUtil.isWithinDateTimeRange(any(), any(), any()))
					.thenReturn(false);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getNextRangeDate(anyString(), any()))
					.thenReturn(today.minusWeeks(1));

			Map<String, List<DataCount>> result =
					service.calculateKpi(
							kpiRequest,
							Collections.emptyList(),
							Collections.emptyList(),
							Collections.emptyList(),
							validationDataList,
							assignees,
							"TestProject");

			assertNotNull(result);
			assertTrue(result.isEmpty());
		}
	}

	@Test
	void testCalculateKpi_withValidToolAndMergedPR() {
		KpiRequest kpiRequest = buildKpiRequest(1, "WEEK");
		List<RepoToolValidationData> validationDataList = new ArrayList<>();

		ScmMergeRequests mr =
				buildMergeRequest(
						LocalDateTime.of(2024, 6, 3, 9, 0),
						LocalDateTime.of(2024, 6, 5, 17, 0),
						"MERGED",
						processorItemId);

		try (MockedStatic<DateUtil> dateUtilMock = mockStatic(DateUtil.class);
				MockedStatic<KpiDataHelper> kpiDataHelperMock = mockStatic(KpiDataHelper.class);
				MockedStatic<KpiHelperService> kpiHelperMock = mockStatic(KpiHelperService.class);
				MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class)) {

			LocalDateTime today = LocalDateTime.of(2024, 6, 10, 0, 0);
			dateUtilMock.when(DateUtil::getTodayTime).thenReturn(today);

			CustomDateRange range = buildDateRange(LocalDate.of(2024, 6, 3), LocalDate.of(2024, 6, 9));
			kpiDataHelperMock
					.when(() -> KpiDataHelper.getStartAndEndDateTimeForDataFiltering(any(), anyString()))
					.thenReturn(range);
			kpiHelperMock
					.when(() -> KpiHelperService.getDateRange(any(), anyString()))
					.thenReturn("2024-06-03 to 2024-06-09");
			dateUtilMock.when(() -> DateUtil.isWithinDateTimeRange(any(), any(), any())).thenReturn(true);
			dateUtilMock
					.when(() -> DateUtil.convertMillisToLocalDateTime(anyLong()))
					.thenReturn(LocalDateTime.of(2024, 6, 3, 9, 0));
			devHelperMock
					.when(() -> DeveloperKpiHelper.getNextRangeDate(anyString(), any()))
					.thenReturn(today.minusWeeks(1));
			devHelperMock.when(() -> DeveloperKpiHelper.isValidTool(any(Tool.class))).thenReturn(true);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), anyString()))
					.thenReturn("main -> test-repo -> TestProject");
			devHelperMock
					.when(() -> DeveloperKpiHelper.filterMergeRequestsForBranch(any(), any(Tool.class)))
					.thenReturn(List.of(mr));
			devHelperMock
					.when(() -> DeveloperKpiHelper.groupMergeRequestsByUser(any()))
					.thenReturn(Collections.emptyMap());
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.setDataCount(
											anyString(),
											anyString(),
											anyString(),
											any(Number.class),
											any(Map.class),
											any(Map.class)))
					.thenAnswer(inv -> null);

			Map<String, List<DataCount>> result =
					service.calculateKpi(
							kpiRequest,
							List.of(mr),
							Collections.emptyList(),
							List.of(tool),
							validationDataList,
							assignees,
							"TestProject");

			assertNotNull(result);
		}
	}

	@Test
	void testPrepareUserValidationData_singleUserWithMergedPR() throws Exception {
		ScmMergeRequests mr =
				buildMergeRequest(
						LocalDateTime.of(2024, 1, 10, 10, 0),
						LocalDateTime.of(2024, 1, 12, 14, 0),
						"MERGED",
						processorItemId);
		userWiseMergeRequests.put("user1@test.com", List.of(mr));

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class);
				MockedStatic<DateUtil> dateUtilMock = mockStatic(DateUtil.class)) {

			devHelperMock
					.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), anyString()))
					.thenReturn("main");
			devHelperMock
					.when(() -> DeveloperKpiHelper.getDeveloperName(anyString(), any(Set.class)))
					.thenReturn("User One");
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.setDataCount(
											anyString(),
											anyString(),
											anyString(),
											any(Number.class),
											any(Map.class),
											any(Map.class)))
					.thenAnswer(inv -> null);
			dateUtilMock
					.when(() -> DateUtil.convertMillisToLocalDateTime(anyLong()))
					.thenReturn(LocalDateTime.of(2024, 1, 10, 10, 0));
			dateUtilMock
					.when(() -> DateUtil.localDateTimeToUTC(any(LocalDateTime.class)))
					.thenReturn(LocalDateTime.of(2024, 1, 12, 14, 0));
			dateUtilMock
					.when(() -> DateUtil.tranformUTCLocalTimeToZFormat(any(LocalDateTime.class)))
					.thenReturn("2024-01-12T14:00:00Z");

			List<RepoToolValidationData> result =
					invokePrepareUserValidationData("TestProject", "2024-01-08 to 2024-01-14");

			assertNotNull(result);
			assertEquals(1, result.size());
			assertEquals("User One", result.get(0).getDeveloperName());
			assertEquals("TestProject", result.get(0).getProjectName());
		}
	}

	@Test
	void testPrepareUserValidationData_multipleUsers() throws Exception {
		ScmMergeRequests mr1 =
				buildMergeRequest(
						LocalDateTime.of(2024, 1, 10, 10, 0),
						LocalDateTime.of(2024, 1, 12, 14, 0),
						"MERGED",
						processorItemId);
		ScmMergeRequests mr2 =
				buildMergeRequest(
						LocalDateTime.of(2024, 1, 11, 9, 0),
						LocalDateTime.of(2024, 1, 13, 15, 0),
						"MERGED",
						processorItemId);
		userWiseMergeRequests.put("user1@test.com", List.of(mr1));
		userWiseMergeRequests.put("user2@test.com", List.of(mr2));

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class);
				MockedStatic<DateUtil> dateUtilMock = mockStatic(DateUtil.class)) {

			devHelperMock
					.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), anyString()))
					.thenReturn("main");
			devHelperMock
					.when(() -> DeveloperKpiHelper.getDeveloperName(anyString(), any(Set.class)))
					.thenReturn("User One", "User Two");
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.setDataCount(
											anyString(),
											anyString(),
											anyString(),
											any(Number.class),
											any(Map.class),
											any(Map.class)))
					.thenAnswer(inv -> null);
			dateUtilMock
					.when(() -> DateUtil.convertMillisToLocalDateTime(anyLong()))
					.thenReturn(LocalDateTime.of(2024, 1, 10, 10, 0));
			dateUtilMock
					.when(() -> DateUtil.localDateTimeToUTC(any(LocalDateTime.class)))
					.thenReturn(LocalDateTime.of(2024, 1, 12, 14, 0));
			dateUtilMock
					.when(() -> DateUtil.tranformUTCLocalTimeToZFormat(any(LocalDateTime.class)))
					.thenReturn("2024-01-12T14:00:00Z");

			List<RepoToolValidationData> result =
					invokePrepareUserValidationData("TestProject", "2024-01-08 to 2024-01-14");

			assertNotNull(result);
			assertEquals(2, result.size());
		}
	}

	@Test
	void testPrepareUserValidationData_mrWithNullCreatedAndMergedDate_excluded() throws Exception {
		ScmMergeRequests mr = new ScmMergeRequests();
		mr.setCreatedDate(null);
		mr.setMergedAt(null);
		mr.setState("MERGED");
		userWiseMergeRequests.put("user1@test.com", List.of(mr));

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class)) {

			devHelperMock
					.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), anyString()))
					.thenReturn("main");
			devHelperMock
					.when(() -> DeveloperKpiHelper.getDeveloperName(anyString(), any(Set.class)))
					.thenReturn("User One");
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.setDataCount(
											anyString(),
											anyString(),
											anyString(),
											any(Number.class),
											any(Map.class),
											any(Map.class)))
					.thenAnswer(inv -> null);

			List<RepoToolValidationData> result =
					invokePrepareUserValidationData("TestProject", "2024-01-08 to 2024-01-14");

			assertNotNull(result);
			assertTrue(result.isEmpty());
		}
	}

	@Test
	void testPrepareUserValidationData_mrWithFirstCommitDate() throws Exception {
		ScmMergeRequests mr =
				buildMergeRequest(
						LocalDateTime.of(2024, 1, 10, 10, 0),
						LocalDateTime.of(2024, 1, 12, 14, 0),
						"MERGED",
						processorItemId);
		mr.setFirstCommitDate(LocalDateTime.of(2024, 1, 9, 8, 0));
		userWiseMergeRequests.put("user1@test.com", List.of(mr));

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class);
				MockedStatic<DateUtil> dateUtilMock = mockStatic(DateUtil.class)) {

			devHelperMock
					.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), anyString()))
					.thenReturn("main");
			devHelperMock
					.when(() -> DeveloperKpiHelper.getDeveloperName(anyString(), any(Set.class)))
					.thenReturn("User One");
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.setDataCount(
											anyString(),
											anyString(),
											anyString(),
											any(Number.class),
											any(Map.class),
											any(Map.class)))
					.thenAnswer(inv -> null);
			dateUtilMock
					.when(() -> DateUtil.localDateTimeToUTC(any(LocalDateTime.class)))
					.thenReturn(LocalDateTime.of(2024, 1, 12, 14, 0));
			dateUtilMock
					.when(() -> DateUtil.tranformUTCLocalTimeToZFormat(any(LocalDateTime.class)))
					.thenReturn("2024-01-09T08:00:00Z");
			dateUtilMock
					.when(() -> DateUtil.convertMillisToLocalDateTime(anyLong()))
					.thenReturn(LocalDateTime.of(2024, 1, 10, 10, 0));

			List<RepoToolValidationData> result =
					invokePrepareUserValidationData("TestProject", "2024-01-08 to 2024-01-14");

			assertNotNull(result);
			assertEquals(1, result.size());
		}
	}

	@Test
	void testPrepareUserValidationData_repoSlugUsedWhenRepositoryNameIsNull() throws Exception {
		tool.setRepositoryName(null);
		tool.setRepoSlug("my-slug");

		ScmMergeRequests mr =
				buildMergeRequest(
						LocalDateTime.of(2024, 1, 10, 10, 0),
						LocalDateTime.of(2024, 1, 12, 14, 0),
						"MERGED",
						processorItemId);
		userWiseMergeRequests.put("user1@test.com", List.of(mr));

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class);
				MockedStatic<DateUtil> dateUtilMock = mockStatic(DateUtil.class)) {

			devHelperMock
					.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), anyString()))
					.thenReturn("main");
			devHelperMock
					.when(() -> DeveloperKpiHelper.getDeveloperName(anyString(), any(Set.class)))
					.thenReturn("User One");
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.setDataCount(
											anyString(),
											anyString(),
											anyString(),
											any(Number.class),
											any(Map.class),
											any(Map.class)))
					.thenAnswer(inv -> null);
			dateUtilMock
					.when(() -> DateUtil.convertMillisToLocalDateTime(anyLong()))
					.thenReturn(LocalDateTime.of(2024, 1, 10, 10, 0));
			dateUtilMock
					.when(() -> DateUtil.localDateTimeToUTC(any(LocalDateTime.class)))
					.thenReturn(LocalDateTime.of(2024, 1, 12, 14, 0));
			dateUtilMock
					.when(() -> DateUtil.tranformUTCLocalTimeToZFormat(any(LocalDateTime.class)))
					.thenReturn("2024-01-12T14:00:00Z");

			List<RepoToolValidationData> result =
					invokePrepareUserValidationData("TestProject", "2024-01-08 to 2024-01-14");

			assertNotNull(result);
			assertEquals(1, result.size());
			assertEquals("my-slug", result.get(0).getRepoUrl());
		}
	}

	@Test
	void testPrepareUserValidationData_subHourMergeTimePreservesDecimalPrecision() throws Exception {
		LocalDateTime created = LocalDateTime.of(2024, 1, 10, 10, 0);
		LocalDateTime merged = LocalDateTime.of(2024, 1, 10, 10, 28);
		ScmMergeRequests mr = buildMergeRequest(created, merged, "MERGED", processorItemId);
		userWiseMergeRequests.put("user1@test.com", List.of(mr));

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class);
				MockedStatic<DateUtil> dateUtilMock = mockStatic(DateUtil.class)) {

			devHelperMock
					.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), anyString()))
					.thenReturn("main");
			devHelperMock
					.when(() -> DeveloperKpiHelper.getDeveloperName(anyString(), any(Set.class)))
					.thenReturn("User One");
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.setDataCount(
											anyString(),
											anyString(),
											anyString(),
											any(Number.class),
											any(Map.class),
											any(Map.class)))
					.thenAnswer(inv -> null);
			dateUtilMock.when(() -> DateUtil.convertMillisToLocalDateTime(anyLong())).thenReturn(created);
			dateUtilMock
					.when(() -> DateUtil.localDateTimeToUTC(any(LocalDateTime.class)))
					.thenReturn(merged);
			dateUtilMock
					.when(() -> DateUtil.tranformUTCLocalTimeToZFormat(any(LocalDateTime.class)))
					.thenReturn("2024-01-10T10:28:00Z");

			List<RepoToolValidationData> result =
					invokePrepareUserValidationData("TestProject", "2024-01-08 to 2024-01-14");

			assertNotNull(result);
			assertEquals(1, result.size());
			double totalTimeSpent = result.get(0).getTotalTimeSpent();
			assertTrue(totalTimeSpent > 0.0, "Sub-hour merge time must be > 0.0 hours");
			assertTrue(totalTimeSpent < 1.0, "28-minute merge time must be < 1.0 hours");
		}
	}

	private ScmMergeRequests buildMergeRequest(
			LocalDateTime createdDate, LocalDateTime mergedAt, String state, ObjectId processorItemId) {
		ScmMergeRequests mr = new ScmMergeRequests();
		if (createdDate != null) {
			mr.setCreatedDate(
					createdDate.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
		}
		mr.setMergedAt(mergedAt);
		mr.setState(state);
		mr.setProcessorItemId(processorItemId);
		mr.setMergeRequestUrl("https://example.com/mr/1");
		return mr;
	}

	private KpiRequest buildKpiRequest(int dataPoints, String duration) {
		KpiRequest req = new KpiRequest();
		req.setXAxisDataPoints(dataPoints);
		req.setDuration(duration);
		return req;
	}

	private CustomDateRange buildDateRange(LocalDate start, LocalDate end) {
		CustomDateRange range = new CustomDateRange();
		range.setStartDate(start);
		range.setEndDate(end);
		return range;
	}

	@SuppressWarnings("unchecked")
	private List<RepoToolValidationData> invokePrepareUserValidationData(
			String projectName, String dateLabel) throws Exception {
		Method method =
				PRCycleTimeTrendKpiServiceImpl.class.getDeclaredMethod(
						"prepareUserValidationData",
						Map.class,
						Set.class,
						Tool.class,
						String.class,
						String.class,
						Map.class);
		method.setAccessible(true);
		return (List<RepoToolValidationData>)
				method.invoke(
						service,
						userWiseMergeRequests,
						assignees,
						tool,
						projectName,
						dateLabel,
						kpiTrendDataByGroup);
	}
}
