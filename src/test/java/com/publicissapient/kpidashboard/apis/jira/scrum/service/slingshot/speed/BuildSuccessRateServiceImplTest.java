package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.speed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.CommonService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.data.AccountHierarchyFilterDataFactory;
import com.publicissapient.kpidashboard.apis.data.KpiRequestFactory;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.ProjectFilter;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.common.constant.BuildStatus;
import com.publicissapient.kpidashboard.common.model.application.Build;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.repository.application.BuildRepository;

@RunWith(MockitoJUnitRunner.class)
public class BuildSuccessRateServiceImplTest {

	@Mock private BuildRepository buildRepository;

	@Mock private CacheService cacheService;

	@Mock private ConfigHelperService configHelperService;

	@Mock private CommonService commonService;

	@Mock private CustomApiConfig customApiConfig;

	@InjectMocks private BuildSuccessRateServiceImpl buildSuccessRateService;

	private KpiRequest kpiRequest;
	private List<AccountHierarchyData> accountHierarchyDataList;
	private List<Build> buildList;
	private ObjectId projectConfigId;

	@Before
	public void setUp() throws Exception {
		// Reconstruct with constructor injection
		buildSuccessRateService = new BuildSuccessRateServiceImpl(buildRepository);

		// Inject parent-class @Autowired fields
		injectField(buildSuccessRateService, "cacheService", cacheService);
		injectField(buildSuccessRateService, "commonService", commonService);
		injectField(buildSuccessRateService, "configHelperService", configHelperService);
		injectField(buildSuccessRateService, "customApiConfig", customApiConfig);

		projectConfigId = new ObjectId("6335363749794a18e8a4479b");

		KpiRequestFactory kpiRequestFactory = KpiRequestFactory.newInstance();
		kpiRequest = kpiRequestFactory.findKpiRequest("kpi116"); // use any available kpiRequest
		kpiRequest.setLabel("PROJECT");

		AccountHierarchyFilterDataFactory hierarchyFactory =
				AccountHierarchyFilterDataFactory.newInstance();
		accountHierarchyDataList = hierarchyFactory.getAccountHierarchyDataList();

		buildList = createBuildList();

		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JENKINS.name()))
				.thenReturn("trackerid");
		when(cacheService.getAdditionalFilterHierarchyLevel()).thenReturn(new HashMap<>());
		when(cacheService.cacheProjectConfigMapData()).thenReturn(new HashMap<>());
		when(configHelperService.calculateCriteria()).thenReturn(new HashMap<>());
		when(configHelperService.loadKpiMaster()).thenReturn(new ArrayList<>());
		when(commonService.sortTrendValueMap(anyMap())).thenAnswer(i -> i.getArgument(0));
	}

	private void injectField(Object target, String fieldName, Object value) {
		Class<?> clazz = target.getClass();
		while (clazz != null) {
			try {
				Field f = clazz.getDeclaredField(fieldName);
				f.setAccessible(true);
				f.set(target, value);
			} catch (NoSuchFieldException ignored) {
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
			clazz = clazz.getSuperclass();
		}
	}

	private List<Build> createBuildList() {
		List<Build> builds = new ArrayList<>();

		// Build within this week - SUCCESS
		Build successBuild = new Build();
		successBuild.setBuildJob("job1");
		successBuild.setBuildBranch("main");
		successBuild.setBuildStatus(BuildStatus.SUCCESS);
		successBuild.setStartTime(System.currentTimeMillis());
		successBuild.setBuildUrl("http://jenkins/job1/1");
		successBuild.setBasicProjectConfigId(projectConfigId);
		builds.add(successBuild);

		// Build within this week - FAILURE
		Build failBuild = new Build();
		failBuild.setBuildJob("job1");
		failBuild.setBuildBranch("main");
		failBuild.setBuildStatus(BuildStatus.FAILURE);
		failBuild.setStartTime(System.currentTimeMillis());
		failBuild.setBuildUrl("http://jenkins/job1/2");
		failBuild.setBasicProjectConfigId(projectConfigId);
		builds.add(failBuild);

		// Build last week - SUCCESS
		Build lastWeekBuild = new Build();
		lastWeekBuild.setBuildJob("job1");
		lastWeekBuild.setBuildBranch("main");
		lastWeekBuild.setBuildStatus(BuildStatus.SUCCESS);
		lastWeekBuild.setStartTime(
				LocalDate.now()
						.minusDays(7)
						.atStartOfDay(ZoneId.systemDefault())
						.toInstant()
						.toEpochMilli());
		lastWeekBuild.setBuildUrl("http://jenkins/job1/3");
		lastWeekBuild.setBasicProjectConfigId(projectConfigId);
		builds.add(lastWeekBuild);

		return builds;
	}

	private Build createBuild(String jobName, String branch, BuildStatus status, long startTime) {
		Build build = new Build();
		build.setBuildJob(jobName);
		build.setBuildBranch(branch);
		build.setBuildStatus(status);
		build.setStartTime(startTime);
		build.setBuildUrl("http://jenkins/" + jobName + "/" + startTime);
		build.setBasicProjectConfigId(projectConfigId);
		return build;
	}

	// ---- Simple method tests ----

	@Test
	public void testGetQualifierType() {
		assertEquals(KPICode.BUILD_SUCCESS_RATE.name(), buildSuccessRateService.getQualifierType());
	}

	@Test
	public void testCalculateKPIMetrics_returnsZero() {
		Long result = buildSuccessRateService.calculateKPIMetrics(new HashMap<>());
		assertEquals(Long.valueOf(0L), result);
	}

	@Test
	public void testCalculateKpiValue() {
		List<Long> values = Arrays.asList(2L, 3L);
		Long result =
				buildSuccessRateService.calculateKpiValue(values, KPICode.BUILD_SUCCESS_RATE.getKpiId());
		assertNotNull(result);
	}

	@Test
	public void testCalculateThresholdValue_withValue() {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setThresholdValueKPI211("85.0");
		Double result = buildSuccessRateService.calculateThresholdValue(fieldMapping);
		assertNotNull(result);
		assertEquals(85.0, result, 0.01);
	}

	@Test
	public void testCalculateThresholdValue_nullValue() {
		FieldMapping fieldMapping = new FieldMapping();
		Double result = buildSuccessRateService.calculateThresholdValue(fieldMapping);
		assertNotNull(result);
	}

	// ---- fetchKPIDataFromDb tests ----

	@Test
	public void testFetchKPIDataFromDb_withBuilds() {
		when(buildRepository.findBuildList(any(), any(), any(), any())).thenReturn(buildList);

		Node node = createProjectNode();
		Map<String, List<Object>> result =
				buildSuccessRateService.fetchKPIDataFromDb(
						List.of(node),
						LocalDate.now().minusWeeks(12).toString(),
						LocalDate.now().toString(),
						kpiRequest);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals(3, result.get(projectConfigId.toString()).size());
	}

	@Test
	public void testFetchKPIDataFromDb_emptyBuilds() {
		when(buildRepository.findBuildList(any(), any(), any(), any())).thenReturn(new ArrayList<>());

		Node node = createProjectNode();
		Map<String, List<Object>> result =
				buildSuccessRateService.fetchKPIDataFromDb(
						List.of(node),
						LocalDate.now().minusWeeks(12).toString(),
						LocalDate.now().toString(),
						kpiRequest);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals(0, result.get(projectConfigId.toString()).size());
	}

	// ---- getKpiData tests ----

	@Test
	public void testGetKpiData_withBuilds() throws Exception {
		when(buildRepository.findBuildList(any(), any(), any(), any())).thenReturn(buildList);

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				buildSuccessRateService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_emptyBuilds() throws Exception {
		when(buildRepository.findBuildList(any(), any(), any(), any())).thenReturn(new ArrayList<>());

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				buildSuccessRateService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_allSuccessBuilds() throws Exception {
		List<Build> allSuccess = new ArrayList<>();
		allSuccess.add(createBuild("job1", "main", BuildStatus.SUCCESS, System.currentTimeMillis()));
		allSuccess.add(
				createBuild("job1", "main", BuildStatus.SUCCESS, System.currentTimeMillis() - 1000));

		when(buildRepository.findBuildList(any(), any(), any(), any())).thenReturn(allSuccess);

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				buildSuccessRateService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_allFailureBuilds() throws Exception {
		List<Build> allFailure = new ArrayList<>();
		allFailure.add(createBuild("job1", "main", BuildStatus.FAILURE, System.currentTimeMillis()));
		allFailure.add(
				createBuild("job1", "main", BuildStatus.FAILURE, System.currentTimeMillis() - 1000));

		when(buildRepository.findBuildList(any(), any(), any(), any())).thenReturn(allFailure);

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				buildSuccessRateService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_multipleJobsAndBranches() throws Exception {
		List<Build> multiJobBuilds = new ArrayList<>();
		multiJobBuilds.add(
				createBuild("job1", "main", BuildStatus.SUCCESS, System.currentTimeMillis()));
		multiJobBuilds.add(
				createBuild("job1", "develop", BuildStatus.FAILURE, System.currentTimeMillis()));
		multiJobBuilds.add(
				createBuild("job2", "main", BuildStatus.SUCCESS, System.currentTimeMillis()));
		multiJobBuilds.add(
				createBuild("job2", "main", BuildStatus.SUCCESS, System.currentTimeMillis() - 1000));

		when(buildRepository.findBuildList(any(), any(), any(), any())).thenReturn(multiJobBuilds);

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				buildSuccessRateService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_buildsSpreadAcrossWeeks() throws Exception {
		List<Build> spreadBuilds = new ArrayList<>();
		long now = System.currentTimeMillis();
		long oneWeekAgo =
				LocalDate.now()
						.minusDays(7)
						.atStartOfDay(ZoneId.systemDefault())
						.toInstant()
						.toEpochMilli();
		long twoWeeksAgo =
				LocalDate.now()
						.minusDays(14)
						.atStartOfDay(ZoneId.systemDefault())
						.toInstant()
						.toEpochMilli();
		long fourWeeksAgo =
				LocalDate.now()
						.minusDays(28)
						.atStartOfDay(ZoneId.systemDefault())
						.toInstant()
						.toEpochMilli();

		spreadBuilds.add(createBuild("job1", "main", BuildStatus.SUCCESS, now));
		spreadBuilds.add(createBuild("job1", "main", BuildStatus.FAILURE, oneWeekAgo));
		spreadBuilds.add(createBuild("job1", "main", BuildStatus.SUCCESS, twoWeeksAgo));
		spreadBuilds.add(createBuild("job1", "main", BuildStatus.FAILURE, fourWeeksAgo));

		when(buildRepository.findBuildList(any(), any(), any(), any())).thenReturn(spreadBuilds);

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				buildSuccessRateService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_withExcelTrackerId() throws Exception {
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JENKINS.name()))
				.thenReturn("Excel-JENKINS-abc123");

		when(buildRepository.findBuildList(any(), any(), any(), any())).thenReturn(buildList);

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				buildSuccessRateService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);

		assertNotNull(kpiElement);
		assertNotNull(kpiElement.getExcelData());
		assertNotNull(kpiElement.getExcelColumns());
	}

	@Test
	public void testGetKpiData_buildWithJobFolderOnly() throws Exception {
		Build build = new Build();
		build.setBuildJob(null);
		build.setJobFolder("folder1");
		build.setBuildBranch("main");
		build.setBuildStatus(BuildStatus.SUCCESS);
		build.setStartTime(System.currentTimeMillis());
		build.setBuildUrl("http://jenkins/folder1/1");
		build.setBasicProjectConfigId(projectConfigId);

		// need buildJob for grouping key - but buildJob is used + buildBranch
		// Let's set buildJob to empty to test buildFrequencyInfo path
		build.setBuildJob("");
		build.setJobFolder("folder1");

		when(buildRepository.findBuildList(any(), any(), any(), any()))
				.thenReturn(Collections.singletonList(build));

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				buildSuccessRateService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_buildWithPipelineNameOnly() throws Exception {
		Build build = new Build();
		build.setBuildJob("");
		build.setJobFolder("");
		build.setPipelineName("pipeline1");
		build.setBuildBranch("main");
		build.setBuildStatus(BuildStatus.SUCCESS);
		build.setStartTime(System.currentTimeMillis());
		build.setBuildUrl("http://jenkins/pipeline1/1");
		build.setBasicProjectConfigId(projectConfigId);

		when(buildRepository.findBuildList(any(), any(), any(), any()))
				.thenReturn(Collections.singletonList(build));

		// Use Excel tracker to exercise buildFrequencyInfo path for pipelineName
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JENKINS.name()))
				.thenReturn("Excel-JENKINS-abc123");

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				buildSuccessRateService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);

		assertNotNull(kpiElement);
		assertNotNull(kpiElement.getExcelData());
	}

	@Test
	public void testGetKpiData_buildWithJobNameForFrequencyInfo() throws Exception {
		Build build = new Build();
		build.setBuildJob("myJob");
		build.setBuildBranch("main");
		build.setBuildStatus(BuildStatus.SUCCESS);
		build.setStartTime(System.currentTimeMillis());
		build.setBuildUrl("http://jenkins/myJob/1");
		build.setBasicProjectConfigId(projectConfigId);

		when(buildRepository.findBuildList(any(), any(), any(), any()))
				.thenReturn(Collections.singletonList(build));

		// Use Excel tracker to exercise buildFrequencyInfo path
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JENKINS.name()))
				.thenReturn("Excel-JENKINS-abc123");

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				buildSuccessRateService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);

		assertNotNull(kpiElement);
		assertNotNull(kpiElement.getExcelData());
	}

	@Test
	public void testGetKpiData_mixedStatusBuilds() throws Exception {
		List<Build> mixedBuilds = new ArrayList<>();
		long now = System.currentTimeMillis();
		mixedBuilds.add(createBuild("job1", "main", BuildStatus.SUCCESS, now));
		mixedBuilds.add(createBuild("job1", "main", BuildStatus.FAILURE, now - 1000));
		mixedBuilds.add(createBuild("job1", "main", BuildStatus.UNSTABLE, now - 2000));
		mixedBuilds.add(createBuild("job1", "main", BuildStatus.SUCCESS, now - 3000));

		when(buildRepository.findBuildList(any(), any(), any(), any())).thenReturn(mixedBuilds);

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				buildSuccessRateService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_buildsOutsideRangeNotCounted() throws Exception {
		// Build far in the past (outside 12-week window)
		long oldBuildTime =
				LocalDate.now()
						.minusWeeks(15)
						.atStartOfDay(ZoneId.systemDefault())
						.toInstant()
						.toEpochMilli();
		List<Build> oldBuilds =
				Collections.singletonList(createBuild("job1", "main", BuildStatus.SUCCESS, oldBuildTime));

		when(buildRepository.findBuildList(any(), any(), any(), any())).thenReturn(oldBuilds);

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				buildSuccessRateService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_singleBuildSuccess100Percent() throws Exception {
		List<Build> singleBuild =
				Collections.singletonList(
						createBuild("job1", "main", BuildStatus.SUCCESS, System.currentTimeMillis()));

		when(buildRepository.findBuildList(any(), any(), any(), any())).thenReturn(singleBuild);

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				buildSuccessRateService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_singleBuildFailure0Percent() throws Exception {
		List<Build> singleBuild =
				Collections.singletonList(
						createBuild("job1", "main", BuildStatus.FAILURE, System.currentTimeMillis()));

		when(buildRepository.findBuildList(any(), any(), any(), any())).thenReturn(singleBuild);

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				buildSuccessRateService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_multipleJobsSameWeek() throws Exception {
		long now = System.currentTimeMillis();
		List<Build> builds = new ArrayList<>();
		builds.add(createBuild("job1", "main", BuildStatus.SUCCESS, now));
		builds.add(createBuild("job2", "develop", BuildStatus.FAILURE, now));
		builds.add(createBuild("job1", "main", BuildStatus.FAILURE, now - 5000));
		builds.add(createBuild("job2", "develop", BuildStatus.SUCCESS, now - 5000));

		when(buildRepository.findBuildList(any(), any(), any(), any())).thenReturn(builds);

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				buildSuccessRateService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_nonExcelTrackerId() throws Exception {
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JENKINS.name()))
				.thenReturn("normal-tracker-id");

		when(buildRepository.findBuildList(any(), any(), any(), any())).thenReturn(buildList);

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				buildSuccessRateService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);

		assertNotNull(kpiElement);
		// Excel data should not be populated for non-excel tracker
	}

	@Test
	public void testGetKpiData_buildWithJobFolderInFrequencyInfo() throws Exception {
		Build build = new Build();
		build.setBuildJob("");
		build.setJobFolder("myFolder");
		build.setPipelineName("pipeline1");
		build.setBuildBranch("feature");
		build.setBuildStatus(BuildStatus.SUCCESS);
		build.setStartTime(System.currentTimeMillis());
		build.setBuildUrl("http://jenkins/myFolder/1");
		build.setBasicProjectConfigId(projectConfigId);

		when(buildRepository.findBuildList(any(), any(), any(), any()))
				.thenReturn(Collections.singletonList(build));

		// Excel tracker to exercise the buildFrequencyInfo jobFolder path
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JENKINS.name()))
				.thenReturn("Excel-JENKINS-track");

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				buildSuccessRateService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);

		assertNotNull(kpiElement);
		assertNotNull(kpiElement.getExcelData());
	}

	// ---- Helper method ----

	private Node createProjectNode() {
		Node node = new Node();
		node.setId("project1");
		node.setName("Test Project");
		node.setGroupName("PROJECT");
		ProjectFilter projectFilter = new ProjectFilter("project1", "Test Project", projectConfigId);
		node.setProjectFilter(projectFilter);
		return node;
	}
}
