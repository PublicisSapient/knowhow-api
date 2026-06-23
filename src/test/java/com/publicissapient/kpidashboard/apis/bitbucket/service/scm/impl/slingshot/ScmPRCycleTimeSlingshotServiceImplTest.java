package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl.slingshot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.strategy.KpiCalculationStrategy;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.strategy.KpiStrategyRegistry;
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
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScmPRCycleTimeSlingshotServiceImplTest {

	@Mock private ConfigHelperService configHelperService;
	@Mock private KpiHelperService kpiHelperService;
	@Mock private KpiStrategyRegistry kpiStrategyRegistry;
	@Mock private ScmKpiHelperService scmKpiHelperService;
	@Mock private KpiCalculationStrategy<Object> strategy;
	@Mock private CacheService cacheService;
	@Mock private CommonService commonService;
	@Mock private CustomApiConfig customApiConfig;

	private ScmPRCycleTimeSlingshotServiceImpl service;

	private KpiRequest kpiRequest;
	private KpiElement kpiElement;
	private Node projectNode;
	private ObjectId projectConfigId;

	@BeforeEach
	void setUp() {
		service =
				new ScmPRCycleTimeSlingshotServiceImpl(
						configHelperService, kpiHelperService, kpiStrategyRegistry, scmKpiHelperService);

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

		Map<String, String> aggregationCriteria = new HashMap<>();
		aggregationCriteria.put(KPICode.PR_CYCLE_TIME_SLINGSHOT.getKpiId(), Constant.AVERAGE);
		when(configHelperService.calculateCriteria()).thenReturn(aggregationCriteria);
		when(configHelperService.getFieldMappingMap()).thenReturn(new HashMap<>());
		when(cacheService.getKpiBenchmarkTargets()).thenReturn(new HashMap<>());
		when(cacheService.getAdditionalFilterHierarchyLevel()).thenReturn(new HashMap<>());
		when(cacheService.cacheProjectConfigMapData()).thenReturn(new HashMap<>());
		when(commonService.sortTrendValueMap(anyMap()))
				.thenAnswer(invocation -> invocation.getArgument(0));

		projectConfigId = new ObjectId("507f1f77bcf86cd799439011");

		kpiRequest = new KpiRequest();
		kpiRequest.setLabel("PROJECT");
		kpiRequest.setIds(new String[] {"project1"});
		kpiRequest.setSelectedMap(new HashMap<>());
		kpiRequest.setXAxisDataPoints(1);
		kpiRequest.setDuration("WEEK");

		kpiElement = new KpiElement();
		kpiElement.setKpiId(KPICode.PR_CYCLE_TIME_SLINGSHOT.getKpiId());

		projectNode = new Node();
		projectNode.setId("project1");
		projectNode.setGroupName("project");
		projectNode.setName("TestProject");
		projectNode.setProjectFilter(new ProjectFilter("project1", "TestProject", projectConfigId));
	}

	// ── getQualifierType ─────────────────────────────────────────────────────

	@Test
	void testGetQualifierType() {
		assertEquals(KPICode.PR_CYCLE_TIME_SLINGSHOT.name(), service.getQualifierType());
	}

	// ── calculateKPIMetrics ───────────────────────────────────────────────────

	@Test
	void testCalculateKPIMetrics_returnsNull() {
		assertNull(service.calculateKPIMetrics(Collections.emptyMap()));
	}

	// ── calculateKpiValue ─────────────────────────────────────────────────────

	@Test
	void testCalculateKpiValue_nonEmptyList_returnsValue() {
		Double result =
				service.calculateKpiValue(List.of(10.0, 20.0), KPICode.PR_CYCLE_TIME_SLINGSHOT.getKpiId());
		assertEquals(15.0, result);
	}

	@Test
	void testCalculateKpiValue_emptyList_returnsZero() {
		Double result =
				service.calculateKpiValue(
						Collections.emptyList(), KPICode.PR_CYCLE_TIME_SLINGSHOT.getKpiId());
		assertEquals(0.0, result);
	}

	// ── calculateThresholdValue ───────────────────────────────────────────────

	@Test
	void testCalculateThresholdValue_withValue() {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setThresholdValueKPI209("5");
		assertEquals(5.0, service.calculateThresholdValue(fieldMapping));
	}

	@Test
	void testCalculateThresholdValue_nullValue_fallsBackToKpiMasterThreshold() {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setThresholdValueKPI209(null);

		KpiMaster kpiMaster = new KpiMaster();
		kpiMaster.setKpiId(KPICode.PR_CYCLE_TIME_SLINGSHOT.getKpiId());
		kpiMaster.setThresholdValue(8.0);
		when(configHelperService.loadKpiMaster()).thenReturn(List.of(kpiMaster));

		assertEquals(8.0, service.calculateThresholdValue(fieldMapping));
	}

	// ── fetchKPIDataFromDb ────────────────────────────────────────────────────

	@Test
	void testFetchKPIDataFromDb_populatesAssigneeSetAndMergeRequestList() {
		ScmMergeRequests mr = new ScmMergeRequests();
		Assignee assignee = new Assignee("u1", "User One", Collections.emptySet());

		when(scmKpiHelperService.getMergeRequests(any(ObjectId.class), any())).thenReturn(List.of(mr));
		when(scmKpiHelperService.getJiraAssigneeForScmUsers(any(ObjectId.class)))
				.thenReturn(List.of(assignee));

		Map<String, Object> result =
				service.fetchKPIDataFromDb(List.of(projectNode), null, null, kpiRequest);

		assertNotNull(result);
		assertTrue(result.containsKey("assigneeSet"));
		assertTrue(result.containsKey("mergeRequestList"));
		assertEquals(1, ((List<?>) result.get("mergeRequestList")).size());
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

		assertTrue(((List<?>) result.get("mergeRequestList")).isEmpty());
		assertTrue(((List<?>) result.get("assigneeSet")).isEmpty());
	}

	// ── getKpiData ────────────────────────────────────────────────────────────

	@Test
	void testGetKpiData_noScmTools_trendValueListEmpty() throws Exception {
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
	void testGetKpiData_emptyMergeRequests_trendValueListEmpty() throws Exception {
		stubTrackerId("BitBucket-abc");

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class)) {
			devHelperMock
					.when(() -> DeveloperKpiHelper.getScmToolsForProject(any(), any(), any()))
					.thenReturn(List.of(buildTool("main", "test-repo")));
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

		ScmMergeRequests mr = new ScmMergeRequests();
		mr.setState("MERGED");
		Map<String, List<DataCount>> kpiTrendData = new HashMap<>();
		kpiTrendData.put("main -> test-repo -> TestProject", List.of(new DataCount()));

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class)) {
			devHelperMock
					.when(() -> DeveloperKpiHelper.getScmToolsForProject(any(), any(), any()))
					.thenReturn(List.of(buildTool("main", "test-repo")));
			devHelperMock
					.when(() -> DeveloperKpiHelper.getStartAndEndDate(any(KpiRequest.class)))
					.thenReturn(null);
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
			when(kpiStrategyRegistry.getStrategy(KPICode.PR_CYCLE_TIME_SLINGSHOT, "line"))
					.thenReturn(strategy);
			when(strategy.calculateKpi(any(), any(), any(), any(), any(), any(), anyString()))
					.thenReturn(kpiTrendData);

			KpiElement result = service.getKpiData(kpiRequest, kpiElement, projectNode);

			assertNotNull(result);
			assertNotNull(result.getTrendValueList());
		}
	}

	@Test
	void testGetKpiData_excelTrackerId_populatesExcelData() throws Exception {
		stubTrackerId("BitBucket-Excel-abc123");

		ScmMergeRequests mr = new ScmMergeRequests();
		mr.setState("MERGED");
		Map<String, List<DataCount>> kpiTrendData = new HashMap<>();
		kpiTrendData.put("main -> test-repo -> TestProject", List.of(new DataCount()));

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class);
				MockedStatic<KPIExcelUtility> excelMock = mockStatic(KPIExcelUtility.class)) {

			devHelperMock
					.when(() -> DeveloperKpiHelper.getScmToolsForProject(any(), any(), any()))
					.thenReturn(List.of(buildTool("main", "test-repo")));
			devHelperMock
					.when(() -> DeveloperKpiHelper.getStartAndEndDate(any(KpiRequest.class)))
					.thenReturn(null);
			devHelperMock
					.when(
							() ->
									DeveloperKpiHelper.prepareDataCountGroups(
											ArgumentMatchers.<Map<String, List<DataCount>>>any(), anyString()))
					.thenReturn(List.of());
			excelMock
					.when(() -> KPIExcelUtility.populatePRCycleTimeExcelData(any(), any()))
					.thenAnswer(inv -> null);

			when(scmKpiHelperService.getMergeRequests(any(ObjectId.class), any()))
					.thenReturn(List.of(mr));
			when(scmKpiHelperService.getJiraAssigneeForScmUsers(any(ObjectId.class)))
					.thenReturn(Collections.emptyList());
			when(kpiStrategyRegistry.getStrategy(KPICode.PR_CYCLE_TIME_SLINGSHOT, "line"))
					.thenReturn(strategy);
			when(strategy.calculateKpi(any(), any(), any(), any(), any(), any(), anyString()))
					.thenReturn(kpiTrendData);

			KpiElement result = service.getKpiData(kpiRequest, kpiElement, projectNode);

			assertNotNull(result);
			assertNotNull(result.getExcelData());
			assertNotNull(result.getExcelColumns());

			excelMock.verify(() -> KPIExcelUtility.populatePRCycleTimeExcelData(any(), any()));
		}
	}

	@Test
	void testGetKpiData_validationDataPopulatedByStrategy() throws Exception {
		stubTrackerId("BitBucket-abc");

		ScmMergeRequests mr = new ScmMergeRequests();
		mr.setState("MERGED");

		try (MockedStatic<DeveloperKpiHelper> devHelperMock = mockStatic(DeveloperKpiHelper.class)) {
			devHelperMock
					.when(() -> DeveloperKpiHelper.getScmToolsForProject(any(), any(), any()))
					.thenReturn(List.of(buildTool("main", "test-repo")));
			devHelperMock
					.when(() -> DeveloperKpiHelper.getStartAndEndDate(any(KpiRequest.class)))
					.thenReturn(null);
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
			when(kpiStrategyRegistry.getStrategy(KPICode.PR_CYCLE_TIME_SLINGSHOT, "line"))
					.thenReturn(strategy);
			when(strategy.calculateKpi(any(), any(), any(), any(), any(), any(), anyString()))
					.thenAnswer(
							inv -> {
								List<RepoToolValidationData> list = inv.getArgument(4);
								RepoToolValidationData data = new RepoToolValidationData();
								data.setDeveloperName("Dev One");
								data.setProjectName("TestProject");
								list.add(data);
								return new HashMap<String, List<DataCount>>();
							});

			KpiElement result = service.getKpiData(kpiRequest, kpiElement, projectNode);

			assertNotNull(result);
		}
	}

	// ── helpers ───────────────────────────────────────────────────────────────

	private void stubTrackerId(String trackerId) {
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.BITBUCKET.name()))
				.thenReturn(trackerId);
	}

	private Tool buildTool(String branch, String repoName) {
		Tool tool = new Tool();
		tool.setBranch(branch);
		tool.setRepositoryName(repoName);
		return tool;
	}
}
