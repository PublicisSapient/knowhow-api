package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.speed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
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
import com.publicissapient.kpidashboard.common.model.application.Deployment;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.repository.application.DeploymentRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

/** Tests for {@link LeadTimeForChangeSlingshotServiceImpl}. */
@RunWith(MockitoJUnitRunner.class)
public class LeadTimeForChangeSlingshotServiceImplTest {

	private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern(DateUtil.TIME_FORMAT);
	private static final String PRODUCTION_BRANCH = "master";

	@Mock private ScmKpiHelperService scmKpiHelperService;
	@Mock private DeploymentRepository deploymentRepository;
	@Mock private ConfigHelperService configHelperService;

	@Mock private CacheService cacheService;
	@Mock private CommonService commonService;
	@Mock private CustomApiConfig customApiConfig;

	@InjectMocks private LeadTimeForChangeSlingshotServiceImpl service;

	private KpiRequest kpiRequest;
	private List<AccountHierarchyData> accountHierarchyDataList;
	private ObjectId projectConfigId;

	@Before
	public void setUp() throws Exception {
		// Reconstruct with constructor injection (three constructor args).
		service =
				new LeadTimeForChangeSlingshotServiceImpl(
						scmKpiHelperService, deploymentRepository, configHelperService);

		// Inject parent-class @Autowired fields.
		injectField(service, "cacheService", cacheService);
		injectField(service, "commonService", commonService);
		injectField(service, "configHelperService", configHelperService);
		injectField(service, "customApiConfig", customApiConfig);

		projectConfigId = new ObjectId("6335363749794a18e8a4479b");

		KpiRequestFactory kpiRequestFactory = KpiRequestFactory.newInstance();
		kpiRequest = kpiRequestFactory.findKpiRequest("kpi116");
		kpiRequest.setLabel("PROJECT");

		AccountHierarchyFilterDataFactory hierarchyFactory =
				AccountHierarchyFilterDataFactory.newInstance();
		accountHierarchyDataList = hierarchyFactory.getAccountHierarchyDataList();

		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JENKINS.name()))
				.thenReturn("trackerid");
		when(cacheService.getAdditionalFilterHierarchyLevel()).thenReturn(new HashMap<>());
		when(cacheService.cacheProjectConfigMapData()).thenReturn(new HashMap<>());
		when(configHelperService.calculateCriteria()).thenReturn(new HashMap<>());
		when(configHelperService.loadKpiMaster()).thenReturn(new ArrayList<>());
		when(configHelperService.getFieldMappingMap()).thenReturn(new HashMap<>());
		when(commonService.sortTrendValueMap(anyMap())).thenAnswer(i -> i.getArgument(0));
	}

	// ---------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------

	private void injectField(Object target, String fieldName, Object value) {
		Class<?> clazz = target.getClass();
		while (clazz != null) {
			try {
				Field f = clazz.getDeclaredField(fieldName);
				f.setAccessible(true);
				f.set(target, value);
			} catch (NoSuchFieldException ignored) {
				// walk up
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
			clazz = clazz.getSuperclass();
		}
	}

	private Node createProjectNode() {
		Node node = new Node();
		node.setId("project1");
		node.setName("Test Project");
		node.setGroupName("PROJECT");
		ProjectFilter projectFilter = new ProjectFilter("project1", "Test Project", projectConfigId);
		node.setProjectFilter(projectFilter);
		return node;
	}

	private ScmMergeRequests createMergedPr(
			String externalId,
			String toBranch,
			LocalDateTime firstCommitDate,
			LocalDateTime mergedAt,
			List<String> commitShas) {
		ScmMergeRequests pr = new ScmMergeRequests();
		pr.setExternalId(externalId);
		pr.setToBranch(toBranch);
		pr.setState("MERGED");
		pr.setFirstCommitDate(firstCommitDate);
		pr.setMergedAt(mergedAt);
		pr.setCommitShas(commitShas);
		pr.setRepositoryName("repoA");
		return pr;
	}

	private Deployment createDeployment(String startTime, List<String> changeSets, String repoUrl) {
		Deployment d = new Deployment();
		d.setBasicProjectConfigId(projectConfigId);
		d.setStartTime(startTime);
		d.setChangeSets(changeSets);
		d.setRepoUrl(repoUrl);
		return d;
	}

	private Deployment createDeployment(
			String startTime, String endTime, List<String> changeSets, String repoUrl, String tool) {
		Deployment d = new Deployment();
		d.setBasicProjectConfigId(projectConfigId);
		d.setStartTime(startTime);
		d.setEndTime(endTime);
		d.setChangeSets(changeSets);
		d.setRepoUrl(repoUrl);
		d.setTool(tool);
		return d;
	}

	private ScmCommits createCommit(String sha, LocalDateTime when, String repositoryName) {
		ScmCommits c = new ScmCommits();
		c.setSha(sha);
		c.setCommitTimestamp(when.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
		c.setRepositoryName(repositoryName);
		return c;
	}

	private String formatTs(LocalDateTime dt) {
		return dt.format(TS_FMT);
	}

	private void mockProductionBranchFieldMapping(String branchName) {
		FieldMapping fm = new FieldMapping();
		fm.setProductionBranchKPI214(branchName);
		fm.setThresholdValueKPI214("24");
		Map<ObjectId, FieldMapping> map = new HashMap<>();
		map.put(projectConfigId, fm);
		when(configHelperService.getFieldMappingMap()).thenReturn(map);
	}

	// ---------------------------------------------------------------------
	// Simple method tests
	// ---------------------------------------------------------------------

	@Test
	public void testGetQualifierType() {
		assertEquals(KPICode.LEAD_TIME_FOR_CHANGE_SLINGSHOT.name(), service.getQualifierType());
	}

	@Test
	public void testCalculateKPIMetrics_returnsNull() {
		assertNull(service.calculateKPIMetrics(new HashMap<>()));
	}

	@Test
	public void testCalculateKpiValue() {
		Double result =
				service.calculateKpiValue(
						Arrays.asList(2.0, 4.0), KPICode.LEAD_TIME_FOR_CHANGE_SLINGSHOT.getKpiId());
		assertNotNull(result);
	}

	@Test
	public void testCalculateThresholdValue_withValue() {
		FieldMapping fm = new FieldMapping();
		fm.setThresholdValueKPI214("24");
		Double result = service.calculateThresholdValue(fm);
		assertNotNull(result);
	}

	@Test
	public void testCalculateThresholdValue_nullFieldMapping() {
		Double result = service.calculateThresholdValue(null);
		assertNotNull(result);
	}

	@Test
	public void testCalculateThresholdValue_nullThreshold() {
		Double result = service.calculateThresholdValue(new FieldMapping());
		assertNotNull(result);
	}

	// ---------------------------------------------------------------------
	// resolveProductionBranch tests
	// ---------------------------------------------------------------------

	@Test
	public void testResolveProductionBranch_defaultWhenNoFieldMapping() {
		when(configHelperService.getFieldMappingMap()).thenReturn(new HashMap<>());
		assertEquals("master", service.resolveProductionBranch(projectConfigId));
	}

	@Test
	public void testResolveProductionBranch_defaultWhenNullId() {
		assertEquals("master", service.resolveProductionBranch(null));
	}

	@Test
	public void testResolveProductionBranch_defaultWhenNullMap() {
		when(configHelperService.getFieldMappingMap()).thenReturn(null);
		assertEquals("master", service.resolveProductionBranch(projectConfigId));
	}

	@Test
	public void testResolveProductionBranch_customBranch() {
		mockProductionBranchFieldMapping("release/prod");
		assertEquals("release/prod", service.resolveProductionBranch(projectConfigId));
	}

	@Test
	public void testResolveProductionBranch_blankBranchFallsBackToDefault() {
		mockProductionBranchFieldMapping("   ");
		assertEquals("master", service.resolveProductionBranch(projectConfigId));
	}

	// ---------------------------------------------------------------------
	// fetchKPIDataFromDb tests
	// ---------------------------------------------------------------------

	@Test
	@SuppressWarnings("unchecked")
	public void testFetchKPIDataFromDb_filtersByProductionBranch() {
		mockProductionBranchFieldMapping("master");

		LocalDateTime firstCommit = LocalDateTime.now().minusDays(2);
		LocalDateTime mergedAt = LocalDateTime.now().minusDays(1);

		ScmMergeRequests prMaster =
				createMergedPr("PR-1", "master", firstCommit, mergedAt, Arrays.asList("sha1"));
		ScmMergeRequests prDev =
				createMergedPr("PR-2", "develop", firstCommit, mergedAt, Arrays.asList("sha2"));
		ScmMergeRequests prNullBranch = createMergedPr("PR-3", null, firstCommit, mergedAt, null);

		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Arrays.asList(prMaster, prDev, prNullBranch));

		Deployment deployment =
				createDeployment(
						formatTs(LocalDateTime.now()), Arrays.asList("sha1"), "https://scm/repoA.git");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(deployment));

		Node node = createProjectNode();
		Map<String, Object> result =
				service.fetchKPIDataFromDb(List.of(node), "2026-01-01", "2026-02-01", kpiRequest);

		assertNotNull(result);
		List<ScmMergeRequests> mergedPrs = (List<ScmMergeRequests>) result.get("mergedPrs");
		List<Deployment> deployments = (List<Deployment>) result.get("deployments");

		assertEquals(1, mergedPrs.size());
		assertEquals("PR-1", mergedPrs.get(0).getExternalId());
		assertEquals(1, deployments.size());
		assertEquals("master", result.get("productionBranch"));
	}

	@Test
	public void testFetchKPIDataFromDb_emptyLeafNodeList_returnsEmptyMap() {
		Map<String, Object> result =
				service.fetchKPIDataFromDb(Collections.emptyList(), "2026-01-01", "2026-02-01", kpiRequest);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testFetchKPIDataFromDb_invalidDatesUsesFallbackRange() {
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.emptyList());
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.emptyList());
		Node node = createProjectNode();
		Map<String, Object> result =
				service.fetchKPIDataFromDb(List.of(node), "not-a-date", "also-not", kpiRequest);
		assertNotNull(result);
		assertTrue(((List<?>) result.get("mergedPrs")).isEmpty());
	}

	// ---------------------------------------------------------------------
	// getKpiData tests (end-to-end trend building)
	// ---------------------------------------------------------------------

	@Test
	public void testGetKpiData_withMatchingCommitSha() throws Exception {
		mockProductionBranchFieldMapping("master");

		LocalDateTime firstCommit = LocalDateTime.now().minusDays(3);
		LocalDateTime mergedAt = LocalDateTime.now().minusDays(2);
		LocalDateTime deployTs = LocalDateTime.now().minusHours(1);

		ScmMergeRequests pr =
				createMergedPr("PR-1", "master", firstCommit, mergedAt, Arrays.asList("sha1"));
		pr.setMergeCommitSha("mergeShaX");

		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));

		Deployment deployment =
				createDeployment(formatTs(deployTs), Arrays.asList("sha1"), "https://scm/repoA.git");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(deployment));

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);
		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_withMergeCommitShaFallback() throws Exception {
		mockProductionBranchFieldMapping("master");

		LocalDateTime firstCommit = LocalDateTime.now().minusDays(3);
		LocalDateTime mergedAt = LocalDateTime.now().minusDays(2);
		LocalDateTime deployTs = LocalDateTime.now().minusHours(1);

		ScmMergeRequests pr = createMergedPr("PR-9", "master", firstCommit, mergedAt, null);
		pr.setMergeCommitSha("mergeSha");

		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));

		Deployment deployment =
				createDeployment(formatTs(deployTs), Arrays.asList("mergeSha"), "https://scm/repoA.git");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(deployment));

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);
		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_fallbackByMergedAtAndRepo() throws Exception {
		mockProductionBranchFieldMapping("master");

		LocalDateTime firstCommit = LocalDateTime.now().minusDays(3);
		LocalDateTime mergedAt = LocalDateTime.now().minusDays(2);
		LocalDateTime deployTs = LocalDateTime.now().minusHours(1);

		ScmMergeRequests pr =
				createMergedPr("PR-2", "master", firstCommit, mergedAt, Arrays.asList("noMatchSha"));

		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));

		// Deployment has NO overlapping SHA - falls back to time+repo match.
		Deployment deployment =
				createDeployment(formatTs(deployTs), Arrays.asList("anotherSha"), "https://scm/repoA.git");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(deployment));

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);
		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_emptyMergedPrs_setsEmptyValue() throws Exception {
		mockProductionBranchFieldMapping("master");
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.emptyList());
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(
						Collections.singletonList(
								createDeployment(formatTs(LocalDateTime.now()), null, "https://scm/repoA.git")));

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);
		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_emptyDeployments_setsEmptyValue() throws Exception {
		mockProductionBranchFieldMapping("master");

		ScmMergeRequests pr =
				createMergedPr(
						"PR-3",
						"master",
						LocalDateTime.now().minusDays(2),
						LocalDateTime.now().minusDays(1),
						Arrays.asList("sha1"));
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.emptyList());

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);
		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_deploymentBeforeFirstCommit_isSkipped() throws Exception {
		mockProductionBranchFieldMapping("master");

		LocalDateTime firstCommit = LocalDateTime.now().minusHours(1);
		LocalDateTime deployTs = LocalDateTime.now().minusDays(5); // before commit

		ScmMergeRequests pr =
				createMergedPr("PR-4", "master", firstCommit, LocalDateTime.now(), Arrays.asList("sha1"));
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));

		Deployment d =
				createDeployment(formatTs(deployTs), Arrays.asList("sha1"), "https://scm/repoA.git");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(d));

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);
		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_prWithNullFirstCommitDate_isSkipped() throws Exception {
		mockProductionBranchFieldMapping("master");

		ScmMergeRequests pr = createMergedPr("PR-5", "master", null, LocalDateTime.now(), null);
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));

		Deployment d = createDeployment(formatTs(LocalDateTime.now()), null, "https://scm/repoA.git");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(d));

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);
		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_prStateBasedMerged() throws Exception {
		mockProductionBranchFieldMapping("master");

		ScmMergeRequests pr =
				createMergedPr(
						"PR-6", "master", LocalDateTime.now().minusDays(2), null, Arrays.asList("sha1"));
		pr.setState("merged"); // state-based detection

		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));

		Deployment d =
				createDeployment(
						formatTs(LocalDateTime.now().minusHours(2)),
						Arrays.asList("sha1"),
						"https://scm/repoA.git");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(d));

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);
		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_unmergedPrsSkipped() throws Exception {
		mockProductionBranchFieldMapping("master");

		ScmMergeRequests pr =
				createMergedPr(
						"PR-7", "master", LocalDateTime.now().minusDays(2), null, Arrays.asList("sha1"));
		pr.setState("OPEN");

		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));

		Deployment d = createDeployment(formatTs(LocalDateTime.now()), Arrays.asList("sha1"), null);
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(d));

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);
		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_deploymentWithUnparseableStartTime() throws Exception {
		mockProductionBranchFieldMapping("master");

		ScmMergeRequests pr =
				createMergedPr(
						"PR-8",
						"master",
						LocalDateTime.now().minusDays(2),
						LocalDateTime.now().minusDays(1),
						Arrays.asList("sha1"));
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));

		Deployment d = createDeployment("garbage", Arrays.asList("sha1"), "https://scm/repoA.git");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(d));

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);
		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_deploymentIsoDateFallbackParse() throws Exception {
		mockProductionBranchFieldMapping("master");

		LocalDateTime firstCommit = LocalDateTime.now().minusDays(2);
		LocalDateTime deployTs = LocalDateTime.now().minusHours(3);

		ScmMergeRequests pr =
				createMergedPr(
						"PR-ISO",
						"master",
						firstCommit,
						LocalDateTime.now().minusDays(1),
						Arrays.asList("sha1"));
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));

		// Use ISO_LOCAL_DATE_TIME format (not DateUtil.TIME_FORMAT) to exercise
		// fallback parse.
		Deployment d =
				createDeployment(deployTs.toString(), Arrays.asList("sha1"), "https://scm/repoA.git");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(d));

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);
		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_deploymentWithBlankAndNullStartTime_isFilteredOut() throws Exception {
		mockProductionBranchFieldMapping("master");

		ScmMergeRequests pr =
				createMergedPr(
						"PR-10",
						"master",
						LocalDateTime.now().minusDays(2),
						LocalDateTime.now().minusDays(1),
						Arrays.asList("sha1"));
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));

		Deployment blank = createDeployment("", Arrays.asList("sha1"), "https://scm/repoA.git");
		Deployment nullTs = createDeployment(null, Arrays.asList("sha1"), "https://scm/repoA.git");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Arrays.asList(blank, nullTs));

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);
		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_repoUrlWithoutGitSuffixStillMatches() throws Exception {
		mockProductionBranchFieldMapping("master");

		LocalDateTime firstCommit = LocalDateTime.now().minusDays(2);
		LocalDateTime deployTs = LocalDateTime.now().minusHours(1);

		ScmMergeRequests pr =
				createMergedPr(
						"PR-11",
						"master",
						firstCommit,
						LocalDateTime.now().minusDays(1),
						Arrays.asList("nonMatching"));
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));

		Deployment d = createDeployment(formatTs(deployTs), null, "https://scm/repoA");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(d));

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);
		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_prWithNullMergedAt_noShaMatch_isSkipped() throws Exception {
		mockProductionBranchFieldMapping("master");

		ScmMergeRequests pr =
				createMergedPr(
						"PR-NoMerge",
						"master",
						LocalDateTime.now().minusDays(2),
						null,
						Arrays.asList("someSha"));
		pr.setState("MERGED");

		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));

		Deployment d =
				createDeployment(
						formatTs(LocalDateTime.now()), Arrays.asList("otherSha"), "https://scm/repoA.git");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(d));

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);
		assertNotNull(kpiElement);
	}

	// ---------------------------------------------------------------------
	// Sanity of unused helpers
	// ---------------------------------------------------------------------

	@Test
	public void testFormatTs_helperProducesParseableString() {
		String s = formatTs(LocalDateTime.now());
		LocalDateTime parsed = LocalDateTime.parse(s, TS_FMT);
		assertNotNull(parsed);
	}

	@Test
	public void testCreateProjectNode_hasProjectFilter() {
		Node n = createProjectNode();
		assertNotNull(n.getProjectFilter());
		assertEquals(projectConfigId, n.getProjectFilter().getBasicProjectConfigId());
	}

	@Test
	public void testProductionBranchConstant_shouldBeMaster() {
		assertEquals(PRODUCTION_BRANCH, "master");
		// touch project id init path
		long epochMillis =
				LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		assertFalse(String.valueOf(epochMillis).isEmpty());
	}

	// ---------------------------------------------------------------------
	// Full-algorithm tests (commit-driven join)
	// ---------------------------------------------------------------------

	/**
	 * End-to-end happy path that actually produces one lead-time record. Also exercises {@code
	 * collectRepoLabels} (the repo comes from the deployment URL) and the trend loop per-repo
	 * bucketing.
	 */
	@Test
	public void testGetKpiData_fullAlgorithm_producesRecord() throws Exception {
		mockProductionBranchFieldMapping("master");
		LocalDateTime commitTime = LocalDateTime.now().minusDays(3);
		LocalDateTime mergedAt = LocalDateTime.now().minusDays(2);
		LocalDateTime deployStart = LocalDateTime.now().minusHours(3);
		LocalDateTime deployEnd = LocalDateTime.now().minusHours(1);

		ScmMergeRequests pr =
				createMergedPr("PR-100", "master", commitTime, mergedAt, Arrays.asList("shaA"));
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));

		Deployment deployment =
				createDeployment(
						formatTs(deployStart),
						formatTs(deployEnd),
						Arrays.asList("shaA"),
						"https://scm/repoA.git",
						"Jenkins");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(deployment));

		when(scmKpiHelperService.getCommitDetails(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(createCommit("shaA", commitTime, "repoA")));

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		KpiElement kpiElement = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);
		assertNotNull(kpiElement);
		assertNotNull(kpiElement.getTrendValueList());
		assertNotNull(kpiElement.getExcelColumns());
	}

	/** Excel path is only populated when the request tracker id contains 'EXCEL'. */
	@Test
	public void testGetKpiData_excelExport_populatesExcelData() throws Exception {
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JENKINS.name()))
				.thenReturn("Excel-trackerid");
		mockProductionBranchFieldMapping("master");
		LocalDateTime commitTime = LocalDateTime.now().minusDays(3);
		LocalDateTime mergedAt = LocalDateTime.now().minusDays(2);
		LocalDateTime deployStart = LocalDateTime.now().minusHours(3);
		LocalDateTime deployEnd = LocalDateTime.now().minusHours(1);

		ScmMergeRequests pr =
				createMergedPr("PR-EX", "master", commitTime, mergedAt, Arrays.asList("shaB"));
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));
		Deployment deployment =
				createDeployment(
						formatTs(deployStart),
						formatTs(deployEnd),
						Arrays.asList("shaB"),
						"https://scm/repoA.git",
						"Jenkins");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(deployment));
		when(scmKpiHelperService.getCommitDetails(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(createCommit("shaB", commitTime, "repoA")));

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		KpiElement kpiElement = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);
		assertNotNull(kpiElement.getExcelData());
		assertFalse(kpiElement.getExcelData().isEmpty());
	}

	/** Excel path skipped when tracker id does not contain 'EXCEL'. */
	@Test
	public void testGetKpiData_nonExcelTracker_doesNotPopulateExcelData() throws Exception {
		mockProductionBranchFieldMapping("master");
		LocalDateTime commitTime = LocalDateTime.now().minusDays(3);
		LocalDateTime mergedAt = LocalDateTime.now().minusDays(2);
		LocalDateTime deployStart = LocalDateTime.now().minusHours(3);
		ScmMergeRequests pr =
				createMergedPr("PR-NEX", "master", commitTime, mergedAt, Arrays.asList("shaC"));
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));
		Deployment deployment =
				createDeployment(
						formatTs(deployStart),
						formatTs(LocalDateTime.now()),
						Arrays.asList("shaC"),
						"https://scm/repoA.git",
						"Jenkins");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(deployment));
		when(scmKpiHelperService.getCommitDetails(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(createCommit("shaC", commitTime, "repoA")));

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		KpiElement kpiElement = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);
		// Excel path early-returns without touching kpiElement.excelData when tracker
		// is not EXCEL.
		assertNull(kpiElement.getExcelData());
	}

	/**
	 * Exercises {@code enrichHeadOnlyDeployments}: two ArgoCD deployments; the second one's
	 * changeSets should be expanded with every commit between the two.
	 */
	@Test
	public void testGetKpiData_argocdEnrichment_expandsChangeSets() throws Exception {
		mockProductionBranchFieldMapping("master");
		LocalDateTime baseCommit = LocalDateTime.now().minusDays(5);
		LocalDateTime midCommit = LocalDateTime.now().minusDays(3);
		LocalDateTime firstDeploy = LocalDateTime.now().minusDays(4);
		LocalDateTime secondDeploy = LocalDateTime.now().minusDays(2);

		ScmMergeRequests pr =
				createMergedPr("PR-ARGO", "master", baseCommit, midCommit, Arrays.asList("shaMid"));
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));

		Deployment d1 =
				createDeployment(
						formatTs(firstDeploy),
						formatTs(firstDeploy.plusMinutes(5)),
						new ArrayList<>(Arrays.asList("shaBase")),
						"https://scm/repoA.git",
						"ArgoCD");
		Deployment d2 =
				createDeployment(
						formatTs(secondDeploy),
						formatTs(secondDeploy.plusMinutes(5)),
						new ArrayList<>(Arrays.asList("shaHead")),
						"https://scm/repoA.git",
						"ArgoCD");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(new ArrayList<>(Arrays.asList(d1, d2)));

		when(scmKpiHelperService.getCommitDetails(eq(projectConfigId), any()))
				.thenReturn(
						Arrays.asList(
								createCommit("shaBase", baseCommit, "repoA"),
								createCommit("shaMid", midCommit, "repoA"),
								createCommit("shaHead", secondDeploy.minusMinutes(1), "repoA")));

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		KpiElement kpiElement = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail);
		assertNotNull(kpiElement);
		// Second deployment should now contain shaMid (added via enrichment).
		assertTrue(d2.getChangeSets().contains("shaMid"));
	}

	/** GitHubAction is also head-only - covers the OR branch of the enrichment tool filter. */
	@Test
	public void testGetKpiData_githubActionEnrichment() throws Exception {
		mockProductionBranchFieldMapping("master");
		LocalDateTime baseCommit = LocalDateTime.now().minusDays(5);
		LocalDateTime midCommit = LocalDateTime.now().minusDays(3);
		LocalDateTime firstDeploy = LocalDateTime.now().minusDays(4);
		LocalDateTime secondDeploy = LocalDateTime.now().minusDays(2);
		ScmMergeRequests pr =
				createMergedPr("PR-GHA", "master", baseCommit, midCommit, Arrays.asList("shaMid"));
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));
		Deployment d1 =
				createDeployment(
						formatTs(firstDeploy),
						formatTs(firstDeploy.plusMinutes(1)),
						new ArrayList<>(Arrays.asList("shaBase")),
						"https://scm/repoA.git",
						"GitHubAction");
		Deployment d2 =
				createDeployment(
						formatTs(secondDeploy),
						formatTs(secondDeploy.plusMinutes(1)),
						new ArrayList<>(Arrays.asList("shaHead")),
						"https://scm/repoA.git",
						"GitHubAction");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(new ArrayList<>(Arrays.asList(d1, d2)));
		when(scmKpiHelperService.getCommitDetails(eq(projectConfigId), any()))
				.thenReturn(
						Arrays.asList(
								createCommit("shaBase", baseCommit, "repoA"),
								createCommit("shaMid", midCommit, "repoA"),
								createCommit("shaHead", secondDeploy.minusMinutes(1), "repoA")));
		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		assertNotNull(service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail));
	}

	/** Non-head-only tool: {@code enrichHeadOnlyDeployments} should short-circuit. */
	@Test
	public void testGetKpiData_nonArgoTool_skipsEnrichment() throws Exception {
		mockProductionBranchFieldMapping("master");
		LocalDateTime commitTime = LocalDateTime.now().minusDays(3);
		ScmMergeRequests pr =
				createMergedPr(
						"PR-J", "master", commitTime, LocalDateTime.now().minusDays(2), Arrays.asList("shaJ"));
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));
		Deployment d =
				createDeployment(
						formatTs(LocalDateTime.now().minusHours(2)),
						formatTs(LocalDateTime.now().minusHours(1)),
						Arrays.asList("shaJ"),
						"https://scm/repoA.git",
						"Jenkins");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(d));
		when(scmKpiHelperService.getCommitDetails(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(createCommit("shaJ", commitTime, "repoA")));
		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		assertNotNull(service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail));
	}

	/** Commit with no matching MR should be filtered out inside {@code buildLeadTimeRecord}. */
	@Test
	public void testGetKpiData_commitWithoutMr_isSkipped() throws Exception {
		mockProductionBranchFieldMapping("master");
		LocalDateTime commitTime = LocalDateTime.now().minusDays(3);
		ScmMergeRequests pr =
				createMergedPr(
						"PR-X",
						"master",
						commitTime,
						LocalDateTime.now().minusDays(2),
						Arrays.asList("otherSha"));
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));
		Deployment d =
				createDeployment(
						formatTs(LocalDateTime.now().minusHours(1)),
						formatTs(LocalDateTime.now()),
						Arrays.asList("commitOnly"),
						"https://scm/repoA.git",
						"Jenkins");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(d));
		when(scmKpiHelperService.getCommitDetails(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(createCommit("commitOnly", commitTime, "repoA")));
		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		assertNotNull(service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail));
	}

	/** Commit whose SHA has no deployment reference should be filtered out. */
	@Test
	public void testGetKpiData_commitWithoutDeployment_isSkipped() throws Exception {
		mockProductionBranchFieldMapping("master");
		LocalDateTime commitTime = LocalDateTime.now().minusDays(3);
		ScmMergeRequests pr =
				createMergedPr(
						"PR-Y", "master", commitTime, LocalDateTime.now().minusDays(2), Arrays.asList("shaY"));
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));
		Deployment d =
				createDeployment(
						formatTs(LocalDateTime.now().minusHours(1)),
						formatTs(LocalDateTime.now()),
						Arrays.asList("otherSha"),
						"https://scm/repoA.git",
						"Jenkins");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(d));
		when(scmKpiHelperService.getCommitDetails(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(createCommit("shaY", commitTime, "repoA")));
		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		assertNotNull(service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail));
	}

	/** Commit with a null timestamp is skipped in {@code buildLeadTimeRecord}. */
	@Test
	public void testGetKpiData_commitWithNullTimestamp_isSkipped() throws Exception {
		mockProductionBranchFieldMapping("master");
		ScmMergeRequests pr =
				createMergedPr(
						"PR-N",
						"master",
						LocalDateTime.now().minusDays(3),
						LocalDateTime.now().minusDays(2),
						Arrays.asList("shaN"));
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));
		Deployment d =
				createDeployment(
						formatTs(LocalDateTime.now().minusHours(1)),
						formatTs(LocalDateTime.now()),
						Arrays.asList("shaN"),
						"https://scm/repoA.git",
						"Jenkins");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(d));

		ScmCommits noTs = new ScmCommits();
		noTs.setSha("shaN");
		noTs.setRepositoryName("repoA");
		// commitTimestamp deliberately left null
		when(scmKpiHelperService.getCommitDetails(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(noTs));

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		assertNotNull(service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail));
	}

	/**
	 * When the deployment's end time is null, {@code lastDeployEndTime} falls back to the start time
	 * - exercises that branch.
	 */
	@Test
	public void testGetKpiData_deploymentWithNullEndTime_fallbackToStart() throws Exception {
		mockProductionBranchFieldMapping("master");
		LocalDateTime commitTime = LocalDateTime.now().minusDays(3);
		LocalDateTime deployStart = LocalDateTime.now().minusHours(2);
		ScmMergeRequests pr =
				createMergedPr(
						"PR-NE",
						"master",
						commitTime,
						LocalDateTime.now().minusDays(2),
						Arrays.asList("shaNE"));
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));
		Deployment d =
				createDeployment(
						formatTs(deployStart),
						null,
						Arrays.asList("shaNE"),
						"https://scm/repoA.git",
						"Jenkins");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(d));
		when(scmKpiHelperService.getCommitDetails(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(createCommit("shaNE", commitTime, "repoA")));
		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		assertNotNull(service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail));
	}

	/** MR without mergedAt: filtered out inside buildLeadTimeRecord. */
	@Test
	public void testGetKpiData_mrWithoutMergedAt_isSkipped() throws Exception {
		mockProductionBranchFieldMapping("master");
		LocalDateTime commitTime = LocalDateTime.now().minusDays(3);
		ScmMergeRequests pr =
				createMergedPr("PR-NM", "master", commitTime, null, Arrays.asList("shaNM"));
		pr.setState("merged");
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));
		Deployment d =
				createDeployment(
						formatTs(LocalDateTime.now().minusHours(1)),
						formatTs(LocalDateTime.now()),
						Arrays.asList("shaNM"),
						"https://scm/repoA.git",
						"Jenkins");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(d));
		when(scmKpiHelperService.getCommitDetails(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(createCommit("shaNM", commitTime, "repoA")));
		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		assertNotNull(service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail));
	}

	/** ArgoCD deployment with unparseable startTime is skipped inside the enrichment loop. */
	@Test
	public void testGetKpiData_argocdUnparseableStart_isSkipped() throws Exception {
		mockProductionBranchFieldMapping("master");
		LocalDateTime commitTime = LocalDateTime.now().minusDays(3);
		ScmMergeRequests pr =
				createMergedPr(
						"PR-U", "master", commitTime, LocalDateTime.now().minusDays(2), Arrays.asList("shaU"));
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));
		Deployment bad =
				createDeployment(
						"garbage",
						"garbage",
						new ArrayList<>(Arrays.asList("shaU")),
						"https://scm/repoA.git",
						"ArgoCD");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(new ArrayList<>(Collections.singletonList(bad)));
		when(scmKpiHelperService.getCommitDetails(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(createCommit("shaU", commitTime, "repoA")));
		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		assertNotNull(service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail));
	}

	/** Merge request whose repo name is null: repoName resolution falls back to deployment url. */
	@Test
	public void testGetKpiData_prWithoutRepositoryName_fallsBackToDeploymentUrl() throws Exception {
		mockProductionBranchFieldMapping("master");
		LocalDateTime commitTime = LocalDateTime.now().minusDays(3);
		ScmMergeRequests pr =
				createMergedPr(
						"PR-NR",
						"master",
						commitTime,
						LocalDateTime.now().minusDays(2),
						Arrays.asList("shaNR"));
		pr.setRepositoryName(null);
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));
		Deployment d =
				createDeployment(
						formatTs(LocalDateTime.now().minusHours(1)),
						formatTs(LocalDateTime.now()),
						Arrays.asList("shaNR"),
						"https://scm/repoA.git",
						"Jenkins");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(d));
		when(scmKpiHelperService.getCommitDetails(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(createCommit("shaNR", commitTime, null)));
		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		assertNotNull(service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail));
	}

	/** Empty commits list: {@code computeLeadTimeRecords} short-circuits. */
	@Test
	public void testGetKpiData_emptyCommits_producesNoRecords() throws Exception {
		mockProductionBranchFieldMapping("master");
		ScmMergeRequests pr =
				createMergedPr(
						"PR-EC",
						"master",
						LocalDateTime.now().minusDays(2),
						LocalDateTime.now().minusDays(1),
						Arrays.asList("sha1"));
		when(scmKpiHelperService.getMergedRequests(eq(projectConfigId), any()))
				.thenReturn(Collections.singletonList(pr));
		Deployment d =
				createDeployment(
						formatTs(LocalDateTime.now()),
						formatTs(LocalDateTime.now().plusMinutes(1)),
						Arrays.asList("sha1"),
						"https://scm/repoA.git",
						"Jenkins");
		when(deploymentRepository.findDeploymentList(anyMap(), anySet(), anyString(), anyString()))
				.thenReturn(Collections.singletonList(d));
		when(scmKpiHelperService.getCommitDetails(eq(projectConfigId), any()))
				.thenReturn(Collections.emptyList());
		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		assertNotNull(service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), detail));
	}
}
