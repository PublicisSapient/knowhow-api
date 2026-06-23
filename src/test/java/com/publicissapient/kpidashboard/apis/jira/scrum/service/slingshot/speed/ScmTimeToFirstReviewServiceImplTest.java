package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.speed;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.BitBucketKPIService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.CommonService;
import com.publicissapient.kpidashboard.apis.common.service.ToolsKPIService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.ProjectFilter;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.KpiMaster;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.generic.ProcessorItem;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScmTimeToFirstReviewServiceImplTest {

	@Mock private ConfigHelperService configHelperService;
	@Mock private KpiHelperService kpiHelperService;
	@Mock private ScmKpiHelperService scmKpiHelperService;
	@Mock private CacheService cacheService;
	@Mock private CommonService commonService;
	@Mock private CustomApiConfig customApiConfig;

	private ScmTimeToFirstReviewServiceImpl service;

	private KpiRequest kpiRequest;
	private KpiElement kpiElement;
	private Node projectNode;
	private ObjectId projectConfigId;
	private ObjectId processorItemId;
	private ProcessorItem processorItem;
	private Tool tool;

	@BeforeEach
	void setUp() {
		service =
				new ScmTimeToFirstReviewServiceImpl(
						configHelperService, kpiHelperService, scmKpiHelperService);

		// Inject parent class fields via reflection
		Class<?> parentClass = ToolsKPIService.class;
		ReflectionTestUtils.setField(
				service, parentClass, "cacheService", cacheService, CacheService.class);
		ReflectionTestUtils.setField(
				service, parentClass, "commonService", commonService, CommonService.class);
		ReflectionTestUtils.setField(
				service,
				parentClass,
				"configHelperService",
				configHelperService,
				ConfigHelperService.class);
		ReflectionTestUtils.setField(
				service, parentClass, "customApiConfig", customApiConfig, CustomApiConfig.class);

		Class<?> bitbucketParentClass = BitBucketKPIService.class;
		ReflectionTestUtils.setField(
				service, bitbucketParentClass, "cacheService", cacheService, CacheService.class);
		ReflectionTestUtils.setField(
				service, bitbucketParentClass, "commonService", commonService, CommonService.class);

		// Common mock setup
		Map<String, String> aggregationCriteria = new HashMap<>();
		aggregationCriteria.put(KPICode.TIME_TO_FIRST_REVIEW.getKpiId(), Constant.AVERAGE);
		when(configHelperService.calculateCriteria()).thenReturn(aggregationCriteria);
		when(configHelperService.getFieldMappingMap()).thenReturn(new HashMap<>());
		when(cacheService.getKpiBenchmarkTargets()).thenReturn(new HashMap<>());
		when(cacheService.getAdditionalFilterHierarchyLevel()).thenReturn(new HashMap<>());
		when(cacheService.cacheProjectConfigMapData()).thenReturn(new HashMap<>());
		when(commonService.sortTrendValueMap(anyMap())).thenAnswer(inv -> inv.getArgument(0));

		projectConfigId = new ObjectId("507f1f77bcf86cd799439011");
		processorItemId = new ObjectId("63316e5667446e5ec838b67e");

		processorItem = new ProcessorItem();
		processorItem.setId(processorItemId);
		processorItem.setProcessorId(new ObjectId("63242d00aaf87a5b01de7ad6"));

		tool = new Tool();
		tool.setBranch("main");
		tool.setRepoSlug("test-repo");
		tool.setProcessorItemList(List.of(processorItem));

		kpiRequest = new KpiRequest();
		kpiRequest.setLabel("PROJECT");
		kpiRequest.setIds(new String[] {"project1"});
		kpiRequest.setSelectedMap(new HashMap<>());
		kpiRequest.setXAxisDataPoints(2);
		kpiRequest.setDuration("WEEK");

		kpiElement = new KpiElement();
		kpiElement.setKpiId(KPICode.TIME_TO_FIRST_REVIEW.getKpiId());

		projectNode = new Node();
		projectNode.setId("project1");
		projectNode.setGroupName("project");
		projectNode.setName("TestProject");
		projectNode.setProjectFilter(new ProjectFilter("project1", "TestProject", projectConfigId));
	}

	// ── getQualifierType ─────────────────────────────────────────────────────

	@Test
	void testGetQualifierType() {
		assertEquals(KPICode.TIME_TO_FIRST_REVIEW.name(), service.getQualifierType());
	}

	// ── calculateKPIMetrics ──────────────────────────────────────────────────

	@Test
	void testCalculateKPIMetrics_returnsNull() {
		assertNull(service.calculateKPIMetrics(new HashMap<>()));
	}

	// ── calculateKpiValue ────────────────────────────────────────────────────

	@Test
	void testCalculateKpiValue_nonEmptyList() {
		Long result =
				service.calculateKpiValue(List.of(10L, 20L), KPICode.TIME_TO_FIRST_REVIEW.getKpiId());
		assertNotNull(result);
	}

	@Test
	void testCalculateKpiValue_singleElement() {
		Long result = service.calculateKpiValue(List.of(5L), KPICode.TIME_TO_FIRST_REVIEW.getKpiId());
		assertNotNull(result);
	}

	// ── calculateThresholdValue ──────────────────────────────────────────────

	@Test
	void testCalculateThresholdValue_withValue() {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setThresholdValueKPI157("5");
		Double result = service.calculateThresholdValue(fieldMapping);
		assertNotNull(result);
		assertEquals(5.0, result);
	}

	@Test
	void testCalculateThresholdValue_nullValue_fallsBackToKpiMaster() {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setThresholdValueKPI157(null);

		KpiMaster kpiMaster = new KpiMaster();
		kpiMaster.setKpiId(KPICode.TIME_TO_FIRST_REVIEW.getKpiId());
		kpiMaster.setThresholdValue(8.0);
		when(configHelperService.loadKpiMaster()).thenReturn(List.of(kpiMaster));

		Double result = service.calculateThresholdValue(fieldMapping);
		assertEquals(8.0, result);
	}

	@Test
	void testCalculateThresholdValue_noThreshold() {
		FieldMapping fieldMapping = new FieldMapping();
		Double result = service.calculateThresholdValue(fieldMapping);
		assertNotNull(result);
	}

	// ── fetchKPIDataFromDb ───────────────────────────────────────────────────

	@Test
	void testFetchKPIDataFromDb_populatesDataMap() {
		ScmMergeRequests mr = new ScmMergeRequests();
		Assignee assignee = new Assignee("u1", "User One", Collections.emptySet());

		when(scmKpiHelperService.getMergeRequests(any(ObjectId.class), any())).thenReturn(List.of(mr));
		when(scmKpiHelperService.getJiraAssigneeForScmUsers(any(ObjectId.class)))
				.thenReturn(List.of(assignee));

		Map<String, Object> result =
				service.fetchKPIDataFromDb(List.of(projectNode), null, null, kpiRequest);

		assertNotNull(result);
		assertTrue(result.containsKey("assigneeSet"));
		assertTrue(result.containsKey("mrsList"));
		assertEquals(1, ((List<?>) result.get("mrsList")).size());
		assertEquals(1, ((List<?>) result.get("assigneeSet")).size());
	}

	@Test
	void testFetchKPIDataFromDb_emptyResults() {
		when(scmKpiHelperService.getMergeRequests(any(ObjectId.class), any()))
				.thenReturn(Collections.emptyList());
		when(scmKpiHelperService.getJiraAssigneeForScmUsers(any(ObjectId.class)))
				.thenReturn(Collections.emptyList());

		Map<String, Object> result =
				service.fetchKPIDataFromDb(List.of(projectNode), null, null, kpiRequest);

		assertTrue(((List<?>) result.get("mrsList")).isEmpty());
		assertTrue(((List<?>) result.get("assigneeSet")).isEmpty());
	}

	// ── getKpiData ───────────────────────────────────────────────────────────

	@Test
	void testGetKpiData_noScmTools_returnsEarly() throws Exception {
		stubTrackerId("BitBucket-abc");

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class)) {
			devHelperMock
					.when(() -> DeveloperKpiHelper.getScmToolsForProject(any(), any(), any()))
					.thenReturn(Collections.emptyList());

			KpiElement result = service.getKpiData(kpiRequest, kpiElement, projectNode);

			assertNotNull(result);
			assertNotNull(result.getTrendValueList());
			assertTrue(((List<?>) result.getTrendValueList()).isEmpty());
		}
	}

	@Test
	void testGetKpiData_emptyMergeRequests_returnsEarly() throws Exception {
		stubTrackerId("BitBucket-abc");

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class)) {
			devHelperMock
					.when(() -> DeveloperKpiHelper.getScmToolsForProject(any(), any(), any()))
					.thenReturn(List.of(tool));
			devHelperMock
					.when(() -> DeveloperKpiHelper.getStartAndEndDate(any(KpiRequest.class)))
					.thenReturn(null);

			when(scmKpiHelperService.getMergeRequests(any(ObjectId.class), any()))
					.thenReturn(Collections.emptyList());
			when(scmKpiHelperService.getJiraAssigneeForScmUsers(any(ObjectId.class)))
					.thenReturn(Collections.emptyList());

			KpiElement result = service.getKpiData(kpiRequest, kpiElement, projectNode);

			assertNotNull(result);
			assertNotNull(result.getTrendValueList());
			assertTrue(((List<?>) result.getTrendValueList()).isEmpty());
		}
	}

	@Test
	void testGetKpiData_withMergeRequests_setsTrendValueList() throws Exception {
		stubTrackerId("BitBucket-abc");

		ScmMergeRequests mr = createMergeRequest("dev@example.com", 2);

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class)) {
			setupDeveloperKpiHelperMocks(devHelperMock);
			devHelperMock.when(() -> DeveloperKpiHelper.isValidTool(any(Tool.class))).thenReturn(true);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), anyString()))
					.thenReturn("main -> test-repo -> TestProject");
			devHelperMock
					.when(() -> DeveloperKpiHelper.filterMergeRequestsForBranch(anyList(), any(Tool.class)))
					.thenReturn(List.of(mr));
			devHelperMock
					.when(() -> DeveloperKpiHelper.groupMergeRequestsByUser(anyList()))
					.thenReturn(Map.of("dev@example.com", List.of(mr)));
			devHelperMock
					.when(() -> DeveloperKpiHelper.getDeveloperName(anyString(), anySet()))
					.thenReturn("Dev User");
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.setDataCount(
											anyString(), anyString(), anyString(), any(Number.class), anyMap(), anyMap()))
					.thenAnswer(inv -> null);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getNextRangeDate(anyString(), any(LocalDateTime.class)))
					.thenReturn(LocalDateTime.now().minusWeeks(1));
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.prepareDataCountGroups(
											ArgumentMatchers.<Map<String, List<DataCount>>>any(), anyString()))
					.thenReturn(List.of());

			when(scmKpiHelperService.getMergeRequests(any(ObjectId.class), any()))
					.thenReturn(List.of(mr));
			when(scmKpiHelperService.getJiraAssigneeForScmUsers(any(ObjectId.class)))
					.thenReturn(Collections.emptyList());

			KpiElement result = service.getKpiData(kpiRequest, kpiElement, projectNode);

			assertNotNull(result);
			assertNotNull(result.getTrendValueList());
		}
	}

	@Test
	void testGetKpiData_excelTrackerId_populatesExcelData() throws Exception {
		stubTrackerId("BitBucket-Excel-abc123");

		ScmMergeRequests mr = createMergeRequest("dev@example.com", 3);

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class);
				MockedStatic<KPIExcelUtility> excelMock = mockStatic(KPIExcelUtility.class)) {

			setupDeveloperKpiHelperMocks(devHelperMock);
			devHelperMock.when(() -> DeveloperKpiHelper.isValidTool(any(Tool.class))).thenReturn(true);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), anyString()))
					.thenReturn("main -> test-repo -> TestProject");
			devHelperMock
					.when(() -> DeveloperKpiHelper.filterMergeRequestsForBranch(anyList(), any(Tool.class)))
					.thenReturn(List.of(mr));
			devHelperMock
					.when(() -> DeveloperKpiHelper.groupMergeRequestsByUser(anyList()))
					.thenReturn(Map.of("dev@example.com", List.of(mr)));
			devHelperMock
					.when(() -> DeveloperKpiHelper.getDeveloperName(anyString(), anySet()))
					.thenReturn("Dev User");
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.setDataCount(
											anyString(), anyString(), anyString(), any(Number.class), anyMap(), anyMap()))
					.thenAnswer(inv -> null);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getNextRangeDate(anyString(), any(LocalDateTime.class)))
					.thenReturn(LocalDateTime.now().minusWeeks(1));
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.prepareDataCountGroups(
											ArgumentMatchers.<Map<String, List<DataCount>>>any(), anyString()))
					.thenReturn(List.of());
			excelMock
					.when(() -> KPIExcelUtility.populatePickupTimeExcelData(any(), any()))
					.thenAnswer(inv -> null);

			when(scmKpiHelperService.getMergeRequests(any(ObjectId.class), any()))
					.thenReturn(List.of(mr));
			when(scmKpiHelperService.getJiraAssigneeForScmUsers(any(ObjectId.class)))
					.thenReturn(Collections.emptyList());

			KpiElement result = service.getKpiData(kpiRequest, kpiElement, projectNode);

			assertNotNull(result);
			assertNotNull(result.getExcelData());
			assertNotNull(result.getExcelColumns());
		}
	}

	@Test
	void testGetKpiData_invalidTool_skipsProcessing() throws Exception {
		stubTrackerId("BitBucket-abc");

		ScmMergeRequests mr = createMergeRequest("dev@example.com", 1);

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class)) {
			setupDeveloperKpiHelperMocks(devHelperMock);
			devHelperMock.when(() -> DeveloperKpiHelper.isValidTool(any(Tool.class))).thenReturn(false);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getNextRangeDate(anyString(), any(LocalDateTime.class)))
					.thenReturn(LocalDateTime.now().minusWeeks(1));
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.prepareDataCountGroups(
											ArgumentMatchers.<Map<String, List<DataCount>>>any(), anyString()))
					.thenReturn(List.of());

			when(scmKpiHelperService.getMergeRequests(any(ObjectId.class), any()))
					.thenReturn(List.of(mr));
			when(scmKpiHelperService.getJiraAssigneeForScmUsers(any(ObjectId.class)))
					.thenReturn(Collections.emptyList());

			KpiElement result = service.getKpiData(kpiRequest, kpiElement, projectNode);

			assertNotNull(result);
			assertNotNull(result.getTrendValueList());
		}
	}

	// ── calculateKpi ─────────────────────────────────────────────────────────

	@Test
	void testCalculateKpi_withMergeRequests() {
		ScmMergeRequests mr = createMergeRequest("dev@example.com", 4);

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class)) {
			devHelperMock.when(() -> DeveloperKpiHelper.isValidTool(any(Tool.class))).thenReturn(true);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), anyString()))
					.thenReturn("main -> test-repo -> TestProject");
			devHelperMock
					.when(() -> DeveloperKpiHelper.filterMergeRequestsForBranch(anyList(), any(Tool.class)))
					.thenReturn(List.of(mr));
			devHelperMock
					.when(() -> DeveloperKpiHelper.groupMergeRequestsByUser(anyList()))
					.thenReturn(Map.of("dev@example.com", List.of(mr)));
			devHelperMock
					.when(() -> DeveloperKpiHelper.getDeveloperName(anyString(), anySet()))
					.thenReturn("Dev User");
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.setDataCount(
											anyString(), anyString(), anyString(), any(Number.class), anyMap(), anyMap()))
					.thenAnswer(inv -> null);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getNextRangeDate(anyString(), any(LocalDateTime.class)))
					.thenReturn(LocalDateTime.now().minusWeeks(1));

			kpiRequest.setXAxisDataPoints(1);
			kpiRequest.setDuration("WEEK");

			List<RepoToolValidationData> validationDataList = new ArrayList<>();
			Set<Assignee> assignees = new HashSet<>();

			Map<String, List<DataCount>> result =
					service.calculateKpi(
							kpiRequest, List.of(mr), List.of(tool), validationDataList, assignees, "TestProject");

			assertNotNull(result);
		}
	}

	@Test
	void testCalculateKpi_emptyMergeRequests() {
		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class)) {
			devHelperMock.when(() -> DeveloperKpiHelper.isValidTool(any(Tool.class))).thenReturn(true);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), anyString()))
					.thenReturn("main -> test-repo -> TestProject");
			devHelperMock
					.when(() -> DeveloperKpiHelper.filterMergeRequestsForBranch(anyList(), any(Tool.class)))
					.thenReturn(Collections.emptyList());
			devHelperMock
					.when(() -> DeveloperKpiHelper.groupMergeRequestsByUser(anyList()))
					.thenReturn(Collections.emptyMap());
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.setDataCount(
											anyString(), anyString(), anyString(), any(Number.class), anyMap(), anyMap()))
					.thenAnswer(inv -> null);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getNextRangeDate(anyString(), any(LocalDateTime.class)))
					.thenReturn(LocalDateTime.now().minusWeeks(1));

			kpiRequest.setXAxisDataPoints(1);
			kpiRequest.setDuration("WEEK");

			List<RepoToolValidationData> validationDataList = new ArrayList<>();
			Set<Assignee> assignees = new HashSet<>();

			Map<String, List<DataCount>> result =
					service.calculateKpi(
							kpiRequest,
							Collections.emptyList(),
							List.of(tool),
							validationDataList,
							assignees,
							"TestProject");

			assertNotNull(result);
			assertTrue(validationDataList.isEmpty());
		}
	}

	@Test
	void testCalculateKpi_multipleDataPoints() {
		ScmMergeRequests mr = createMergeRequest("dev@example.com", 2);

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class)) {
			devHelperMock.when(() -> DeveloperKpiHelper.isValidTool(any(Tool.class))).thenReturn(true);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), anyString()))
					.thenReturn("main -> test-repo -> TestProject");
			devHelperMock
					.when(() -> DeveloperKpiHelper.filterMergeRequestsForBranch(anyList(), any(Tool.class)))
					.thenReturn(List.of(mr));
			devHelperMock
					.when(() -> DeveloperKpiHelper.groupMergeRequestsByUser(anyList()))
					.thenReturn(Map.of("dev@example.com", List.of(mr)));
			devHelperMock
					.when(() -> DeveloperKpiHelper.getDeveloperName(anyString(), anySet()))
					.thenReturn("Dev User");
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.setDataCount(
											anyString(), anyString(), anyString(), any(Number.class), anyMap(), anyMap()))
					.thenAnswer(inv -> null);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getNextRangeDate(anyString(), any(LocalDateTime.class)))
					.thenReturn(LocalDateTime.now().minusWeeks(1));

			kpiRequest.setXAxisDataPoints(3);
			kpiRequest.setDuration("WEEK");

			List<RepoToolValidationData> validationDataList = new ArrayList<>();
			Set<Assignee> assignees = new HashSet<>();

			Map<String, List<DataCount>> result =
					service.calculateKpi(
							kpiRequest, List.of(mr), List.of(tool), validationDataList, assignees, "TestProject");

			assertNotNull(result);
		}
	}

	@Test
	void testCalculateKpi_invalidTool_noDataProcessed() {
		ScmMergeRequests mr = createMergeRequest("dev@example.com", 2);

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class)) {
			devHelperMock.when(() -> DeveloperKpiHelper.isValidTool(any(Tool.class))).thenReturn(false);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getNextRangeDate(anyString(), any(LocalDateTime.class)))
					.thenReturn(LocalDateTime.now().minusWeeks(1));

			kpiRequest.setXAxisDataPoints(1);
			kpiRequest.setDuration("WEEK");

			List<RepoToolValidationData> validationDataList = new ArrayList<>();
			Set<Assignee> assignees = new HashSet<>();

			Map<String, List<DataCount>> result =
					service.calculateKpi(
							kpiRequest, List.of(mr), List.of(tool), validationDataList, assignees, "TestProject");

			assertNotNull(result);
			assertTrue(result.isEmpty());
		}
	}

	@Test
	void testCalculateKpi_multipleToolsProcessed() {
		ScmMergeRequests mr = createMergeRequest("dev@example.com", 2);

		Tool tool2 = new Tool();
		tool2.setBranch("develop");
		tool2.setRepositoryName("other-repo");
		ProcessorItem pi2 = new ProcessorItem();
		pi2.setId(new ObjectId("63316e5667446e5ec838b67f"));
		tool2.setProcessorItemList(List.of(pi2));

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class)) {
			devHelperMock.when(() -> DeveloperKpiHelper.isValidTool(any(Tool.class))).thenReturn(true);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), anyString()))
					.thenReturn("main -> test-repo -> TestProject");
			devHelperMock
					.when(() -> DeveloperKpiHelper.filterMergeRequestsForBranch(anyList(), any(Tool.class)))
					.thenReturn(List.of(mr));
			devHelperMock
					.when(() -> DeveloperKpiHelper.groupMergeRequestsByUser(anyList()))
					.thenReturn(Map.of("dev@example.com", List.of(mr)));
			devHelperMock
					.when(() -> DeveloperKpiHelper.getDeveloperName(anyString(), anySet()))
					.thenReturn("Dev User");
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.setDataCount(
											anyString(), anyString(), anyString(), any(Number.class), anyMap(), anyMap()))
					.thenAnswer(inv -> null);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getNextRangeDate(anyString(), any(LocalDateTime.class)))
					.thenReturn(LocalDateTime.now().minusWeeks(1));

			kpiRequest.setXAxisDataPoints(1);
			kpiRequest.setDuration("WEEK");

			List<RepoToolValidationData> validationDataList = new ArrayList<>();
			Set<Assignee> assignees = new HashSet<>();

			Map<String, List<DataCount>> result =
					service.calculateKpi(
							kpiRequest,
							List.of(mr),
							List.of(tool, tool2),
							validationDataList,
							assignees,
							"TestProject");

			assertNotNull(result);
		}
	}

	@Test
	void testCalculateKpi_withAssigneeMatching() {
		ScmMergeRequests mr = createMergeRequest("dev@example.com", 5);

		Assignee assignee = new Assignee("uid1", "Dev User", new HashSet<>(List.of("dev@example.com")));
		Set<Assignee> assignees = new HashSet<>();
		assignees.add(assignee);

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class)) {
			devHelperMock.when(() -> DeveloperKpiHelper.isValidTool(any(Tool.class))).thenReturn(true);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), anyString()))
					.thenReturn("main -> test-repo -> TestProject");
			devHelperMock
					.when(() -> DeveloperKpiHelper.filterMergeRequestsForBranch(anyList(), any(Tool.class)))
					.thenReturn(List.of(mr));
			devHelperMock
					.when(() -> DeveloperKpiHelper.groupMergeRequestsByUser(anyList()))
					.thenReturn(Map.of("dev@example.com", List.of(mr)));
			devHelperMock
					.when(() -> DeveloperKpiHelper.getDeveloperName(anyString(), anySet()))
					.thenReturn("Dev User");
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.setDataCount(
											anyString(), anyString(), anyString(), any(Number.class), anyMap(), anyMap()))
					.thenAnswer(inv -> null);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getNextRangeDate(anyString(), any(LocalDateTime.class)))
					.thenReturn(LocalDateTime.now().minusWeeks(1));

			kpiRequest.setXAxisDataPoints(1);
			kpiRequest.setDuration("WEEK");

			List<RepoToolValidationData> validationDataList = new ArrayList<>();

			Map<String, List<DataCount>> result =
					service.calculateKpi(
							kpiRequest, List.of(mr), List.of(tool), validationDataList, assignees, "TestProject");

			assertNotNull(result);
		}
	}

	@Test
	void testCalculateKpi_multipleUsersGrouped() {
		ScmMergeRequests mr1 = createMergeRequest("dev1@example.com", 2);
		ScmMergeRequests mr2 = createMergeRequest("dev2@example.com", 4);

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class)) {
			devHelperMock.when(() -> DeveloperKpiHelper.isValidTool(any(Tool.class))).thenReturn(true);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), anyString()))
					.thenReturn("main -> test-repo -> TestProject");
			devHelperMock
					.when(() -> DeveloperKpiHelper.filterMergeRequestsForBranch(anyList(), any(Tool.class)))
					.thenReturn(List.of(mr1, mr2));
			devHelperMock
					.when(() -> DeveloperKpiHelper.groupMergeRequestsByUser(anyList()))
					.thenReturn(Map.of("dev1@example.com", List.of(mr1), "dev2@example.com", List.of(mr2)));
			devHelperMock
					.when(() -> DeveloperKpiHelper.getDeveloperName(anyString(), anySet()))
					.thenAnswer(inv -> inv.getArgument(0));
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.setDataCount(
											anyString(), anyString(), anyString(), any(Number.class), anyMap(), anyMap()))
					.thenAnswer(inv -> null);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getNextRangeDate(anyString(), any(LocalDateTime.class)))
					.thenReturn(LocalDateTime.now().minusWeeks(1));

			kpiRequest.setXAxisDataPoints(1);
			kpiRequest.setDuration("WEEK");

			List<RepoToolValidationData> validationDataList = new ArrayList<>();
			Set<Assignee> assignees = new HashSet<>();

			Map<String, List<DataCount>> result =
					service.calculateKpi(
							kpiRequest,
							List.of(mr1, mr2),
							List.of(tool),
							validationDataList,
							assignees,
							"TestProject");

			assertNotNull(result);
		}
	}

	@Test
	void testCalculateKpi_mergeRequestWithNullPickedForReviewFiltered() {
		// MR without pickedForReviewOn should be filtered out in date range filtering
		ScmMergeRequests mr = new ScmMergeRequests();
		mr.setProcessorItemId(processorItemId);
		mr.setPickedForReviewOn(null);
		mr.setCreatedDate(toMillis(LocalDateTime.now().minusHours(5)));

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class)) {
			devHelperMock.when(() -> DeveloperKpiHelper.isValidTool(any(Tool.class))).thenReturn(true);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), anyString()))
					.thenReturn("main -> test-repo -> TestProject");
			devHelperMock
					.when(() -> DeveloperKpiHelper.filterMergeRequestsForBranch(anyList(), any(Tool.class)))
					.thenReturn(Collections.emptyList());
			devHelperMock
					.when(() -> DeveloperKpiHelper.groupMergeRequestsByUser(anyList()))
					.thenReturn(Collections.emptyMap());
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.setDataCount(
											anyString(), anyString(), anyString(), any(Number.class), anyMap(), anyMap()))
					.thenAnswer(inv -> null);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getNextRangeDate(anyString(), any(LocalDateTime.class)))
					.thenReturn(LocalDateTime.now().minusWeeks(1));

			kpiRequest.setXAxisDataPoints(1);
			kpiRequest.setDuration("WEEK");

			List<RepoToolValidationData> validationDataList = new ArrayList<>();
			Set<Assignee> assignees = new HashSet<>();

			Map<String, List<DataCount>> result =
					service.calculateKpi(
							kpiRequest, List.of(mr), List.of(tool), validationDataList, assignees, "TestProject");

			assertNotNull(result);
		}
	}

	@Test
	void testCalculateKpi_toolWithRepositoryNameInsteadOfRepoSlug() {
		ScmMergeRequests mr = createMergeRequest("dev@example.com", 2);

		Tool repoNameTool = new Tool();
		repoNameTool.setBranch("develop");
		repoNameTool.setRepositoryName("my-repo");
		repoNameTool.setProcessorItemList(List.of(processorItem));

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class)) {
			devHelperMock.when(() -> DeveloperKpiHelper.isValidTool(any(Tool.class))).thenReturn(true);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), anyString()))
					.thenReturn("develop -> my-repo -> TestProject");
			devHelperMock
					.when(() -> DeveloperKpiHelper.filterMergeRequestsForBranch(anyList(), any(Tool.class)))
					.thenReturn(List.of(mr));
			devHelperMock
					.when(() -> DeveloperKpiHelper.groupMergeRequestsByUser(anyList()))
					.thenReturn(Map.of("dev@example.com", List.of(mr)));
			devHelperMock
					.when(() -> DeveloperKpiHelper.getDeveloperName(anyString(), anySet()))
					.thenReturn("Dev User");
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.setDataCount(
											anyString(), anyString(), anyString(), any(Number.class), anyMap(), anyMap()))
					.thenAnswer(inv -> null);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getNextRangeDate(anyString(), any(LocalDateTime.class)))
					.thenReturn(LocalDateTime.now().minusWeeks(1));

			kpiRequest.setXAxisDataPoints(1);
			kpiRequest.setDuration("WEEK");

			List<RepoToolValidationData> validationDataList = new ArrayList<>();
			Set<Assignee> assignees = new HashSet<>();

			Map<String, List<DataCount>> result =
					service.calculateKpi(
							kpiRequest,
							List.of(mr),
							List.of(repoNameTool),
							validationDataList,
							assignees,
							"TestProject");

			assertNotNull(result);
		}
	}

	@Test
	void testCalculateKpi_multipleMrsForSameUser() {
		ScmMergeRequests mr1 = createMergeRequest("dev@example.com", 2);
		ScmMergeRequests mr2 = createMergeRequest("dev@example.com", 6);

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class)) {
			devHelperMock.when(() -> DeveloperKpiHelper.isValidTool(any(Tool.class))).thenReturn(true);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(Tool.class), anyString()))
					.thenReturn("main -> test-repo -> TestProject");
			devHelperMock
					.when(() -> DeveloperKpiHelper.filterMergeRequestsForBranch(anyList(), any(Tool.class)))
					.thenReturn(List.of(mr1, mr2));
			devHelperMock
					.when(() -> DeveloperKpiHelper.groupMergeRequestsByUser(anyList()))
					.thenReturn(Map.of("dev@example.com", List.of(mr1, mr2)));
			devHelperMock
					.when(() -> DeveloperKpiHelper.getDeveloperName(anyString(), anySet()))
					.thenReturn("Dev User");
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.setDataCount(
											anyString(), anyString(), anyString(), any(Number.class), anyMap(), anyMap()))
					.thenAnswer(inv -> null);
			devHelperMock
					.when(() -> DeveloperKpiHelper.getNextRangeDate(anyString(), any(LocalDateTime.class)))
					.thenReturn(LocalDateTime.now().minusWeeks(1));

			kpiRequest.setXAxisDataPoints(1);
			kpiRequest.setDuration("WEEK");

			List<RepoToolValidationData> validationDataList = new ArrayList<>();
			Set<Assignee> assignees = new HashSet<>();

			Map<String, List<DataCount>> result =
					service.calculateKpi(
							kpiRequest,
							List.of(mr1, mr2),
							List.of(tool),
							validationDataList,
							assignees,
							"TestProject");

			assertNotNull(result);
		}
	}

	private void stubTrackerId(String trackerId) {
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.BITBUCKET.name()))
				.thenReturn(trackerId);
	}

	private void setupDeveloperKpiHelperMocks(MockedStatic<DeveloperKpiHelper> devHelperMock) {
		devHelperMock
				.when(() -> DeveloperKpiHelper.getScmToolsForProject(any(), any(), any()))
				.thenReturn(List.of(tool));
		devHelperMock
				.when(() -> DeveloperKpiHelper.getStartAndEndDate(any(KpiRequest.class)))
				.thenReturn(null);
	}

	private ScmMergeRequests createMergeRequest(String authorEmail, int hoursAgo) {
		LocalDateTime createdTime = LocalDateTime.now().minusHours(hoursAgo + 2);
		LocalDateTime pickedForReviewTime = LocalDateTime.now().minusHours(hoursAgo);

		ScmMergeRequests mr = new ScmMergeRequests();
		mr.setProcessorItemId(processorItemId);
		mr.setCreatedDate(toMillis(createdTime));
		mr.setPickedForReviewOn(toMillis(pickedForReviewTime));
		mr.setState("open");
		mr.setMergeRequestUrl("https://example.com/pr/1");

		if (authorEmail != null) {
			User user = new User();
			user.setEmail(authorEmail);
			mr.setAuthorId(user);
		}
		return mr;
	}

	private Long toMillis(LocalDateTime dateTime) {
		return dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
	}
}
