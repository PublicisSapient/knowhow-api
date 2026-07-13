package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.speed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.CommonService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.data.AccountHierarchyFilterDataFactory;
import com.publicissapient.kpidashboard.apis.data.KpiRequestFactory;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.generic.ProcessorItem;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.model.scm.User;

@RunWith(MockitoJUnitRunner.class)
public class ScmPrRevertRateServiceImplTest {

	@InjectMocks private ScmPrRevertRateServiceImpl service;

	@Mock private ConfigHelperService configHelperService;
	@Mock private KpiHelperService kpiHelperService;
	@Mock private CacheService cacheService;
	@Mock private CommonService commonService;
	@Mock private CustomApiConfig customApiConfig;
	@Mock private ScmKpiHelperService scmKpiHelperService;

	private KpiRequest kpiRequest;
	private List<AccountHierarchyData> accountHierarchyDataList;
	private Map<ObjectId, Map<String, List<Tool>>> toolMap;
	private List<Tool> toolList;
	private Tool tool;
	private ProcessorItem processorItem;
	private ObjectId processorItemId;
	private ObjectId projectConfigId;

	@Before
	public void setUp() throws Exception {
		MockitoAnnotations.openMocks(this);

		service =
				new ScmPrRevertRateServiceImpl(configHelperService, kpiHelperService, scmKpiHelperService);

		injectField(service, "cacheService", cacheService);
		injectField(service, "commonService", commonService);
		injectField(service, "configHelperService", configHelperService);
		injectField(service, "customApiConfig", customApiConfig);
		injectField(service, "scmKpiHelperService", scmKpiHelperService);

		KpiRequestFactory factory = KpiRequestFactory.newInstance();
		kpiRequest = factory.findKpiRequest("kpi157");
		kpiRequest.setLabel("PROJECT");
		kpiRequest.setXAxisDataPoints(2);
		kpiRequest.setDuration(CommonConstant.WEEK);

		AccountHierarchyFilterDataFactory hierarchyFactory =
				AccountHierarchyFilterDataFactory.newInstance();
		accountHierarchyDataList = hierarchyFactory.getAccountHierarchyDataList();

		projectConfigId = new ObjectId("6335363749794a18e8a4479b");
		processorItemId = new ObjectId("63316e5667446e5ec838b67e");

		processorItem = new ProcessorItem();
		processorItem.setId(processorItemId);
		processorItem.setProcessorId(new ObjectId("63242d00aaf87a5b01de7ad6"));

		tool = new Tool();
		tool.setBranch("main");
		tool.setRepoSlug("test-repo");
		tool.setProcessorItemList(Arrays.asList(processorItem));

		toolList = new ArrayList<>();
		toolList.add(tool);

		Map<String, List<Tool>> toolGroup = new HashMap<>();
		toolGroup.put(Constant.TOOL_BITBUCKET, toolList);
		toolMap = new HashMap<>();
		toolMap.put(projectConfigId, toolGroup);

		when(configHelperService.getToolItemMap()).thenReturn(toolMap);
		when(kpiHelperService.populateSCMToolsRepoList(anyMap())).thenReturn(toolList);
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.BITBUCKET.name()))
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

	private ScmMergeRequests createMergeRequest(
			String authorEmail, boolean isClosed, String state, String title) {
		ScmMergeRequests mr = new ScmMergeRequests();
		mr.setProcessorItemId(processorItemId);
		mr.setClosed(isClosed);
		mr.setMergedAt(LocalDateTime.now());
		mr.setState(state);
		mr.setTitle(title);
		if (authorEmail != null) {
			User user = new User();
			user.setEmail(authorEmail);
			mr.setAuthorId(user);
		}
		return mr;
	}

	// ---- simple method tests ----

	@Test
	public void testGetQualifierType() {
		assertEquals(KPICode.PR_REVERT_RATE_SLINGSHOT.name(), service.getQualifierType());
	}

	@Test
	public void testCalculateKPIMetrics_returnsNull() {
		assertNull(service.calculateKPIMetrics(new HashMap<>()));
	}

	@Test
	public void testCalculateKpiValue() {
		List<Double> values = Arrays.asList(2.0, 3.0);
		Double result = service.calculateKpiValue(values, KPICode.PR_REVERT_RATE_SLINGSHOT.getKpiId());
		assertNotNull(result);
	}

	@Test
	public void testCalculateThresholdValue_withValue() {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setThresholdValueKPI215("5");
		Double result = service.calculateThresholdValue(fieldMapping);
		assertNotNull(result);
	}

	@Test
	public void testCalculateThresholdValue_null() {
		FieldMapping fieldMapping = new FieldMapping();
		Double result = service.calculateThresholdValue(fieldMapping);
		assertNotNull(result);
	}

	// ---- getKpiData tests ----

	@Test
	public void testGetKpiData_withMixedPRs() throws Exception {
		List<ScmMergeRequests> mrs = new ArrayList<>();
		mrs.add(createMergeRequest("dev@example.com", true, "merged", "Revert some feature"));
		mrs.add(createMergeRequest("dev@example.com", true, "merged", "Add new feature"));
		mrs.add(createMergeRequest("other@example.com", true, "merged", "REVERT: critical fix"));

		when(scmKpiHelperService.getMergedRequests(any(), any())).thenReturn(mrs);
		when(scmKpiHelperService.getJiraAssigneeForScmUsers(any())).thenReturn(new ArrayList<>());

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				service.getKpiData(
						kpiRequest,
						kpiRequest.getKpiList().get(0),
						detail.getMapOfListOfProjectNodes().get("project").get(0));

		assertNotNull(kpiElement);
		assertNotNull(kpiElement.getTrendValueList());
	}

	@Test
	public void testGetKpiData_emptyMRs() throws Exception {
		when(scmKpiHelperService.getMergedRequests(any(), any())).thenReturn(new ArrayList<>());
		when(scmKpiHelperService.getJiraAssigneeForScmUsers(any())).thenReturn(new ArrayList<>());

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				service.getKpiData(
						kpiRequest,
						kpiRequest.getKpiList().get(0),
						detail.getMapOfListOfProjectNodes().get("project").get(0));

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_noScmTools() throws Exception {
		when(kpiHelperService.populateSCMToolsRepoList(anyMap())).thenReturn(new ArrayList<>());

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				service.getKpiData(
						kpiRequest,
						kpiRequest.getKpiList().get(0),
						detail.getMapOfListOfProjectNodes().get("project").get(0));

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_withExcelTrackerId() throws Exception {
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.BITBUCKET.name()))
				.thenReturn("Excel-Bitbucket-abc123");

		List<ScmMergeRequests> mrs = new ArrayList<>();
		mrs.add(createMergeRequest("dev@example.com", true, "merged", "Revert: bug fix"));
		when(scmKpiHelperService.getMergedRequests(any(), any())).thenReturn(mrs);
		when(scmKpiHelperService.getJiraAssigneeForScmUsers(any())).thenReturn(new ArrayList<>());

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				service.getKpiData(
						kpiRequest,
						kpiRequest.getKpiList().get(0),
						detail.getMapOfListOfProjectNodes().get("project").get(0));

		assertNotNull(kpiElement);
		assertNotNull(kpiElement.getExcelData());
		assertNotNull(kpiElement.getExcelColumns());
	}

	@Test
	public void testGetKpiData_nonRevertPRsFiltered() throws Exception {
		List<ScmMergeRequests> mrs = new ArrayList<>();
		mrs.add(createMergeRequest("dev@example.com", true, "merged", "Add login feature"));
		mrs.add(createMergeRequest("dev@example.com", true, "merged", "Fix null pointer bug"));
		when(scmKpiHelperService.getMergedRequests(any(), any())).thenReturn(mrs);
		when(scmKpiHelperService.getJiraAssigneeForScmUsers(any())).thenReturn(new ArrayList<>());

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				service.getKpiData(
						kpiRequest,
						kpiRequest.getKpiList().get(0),
						detail.getMapOfListOfProjectNodes().get("project").get(0));

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_withAssigneeMatch() throws Exception {
		Assignee assignee =
				new Assignee("uid1", "Dev User", new HashSet<>(Arrays.asList("matched@example.com")));

		List<ScmMergeRequests> mrs = new ArrayList<>();
		mrs.add(createMergeRequest("matched@example.com", true, "merged", "Revert previous PR"));
		when(scmKpiHelperService.getMergedRequests(any(), any())).thenReturn(mrs);
		when(scmKpiHelperService.getJiraAssigneeForScmUsers(any())).thenReturn(Arrays.asList(assignee));

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				service.getKpiData(
						kpiRequest,
						kpiRequest.getKpiList().get(0),
						detail.getMapOfListOfProjectNodes().get("project").get(0));

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_invalidToolEmptyProcessorItems() throws Exception {
		Tool invalidTool = new Tool();
		invalidTool.setBranch("main");
		invalidTool.setProcessorItemList(new ArrayList<>());
		when(kpiHelperService.populateSCMToolsRepoList(anyMap()))
				.thenReturn(Arrays.asList(invalidTool));

		when(scmKpiHelperService.getMergedRequests(any(), any())).thenReturn(new ArrayList<>());
		when(scmKpiHelperService.getJiraAssigneeForScmUsers(any())).thenReturn(new ArrayList<>());

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				service.getKpiData(
						kpiRequest,
						kpiRequest.getKpiList().get(0),
						detail.getMapOfListOfProjectNodes().get("project").get(0));

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_withRepositoryName() throws Exception {
		Tool repoNameTool = new Tool();
		repoNameTool.setBranch("develop");
		repoNameTool.setRepositoryName("my-repo");
		repoNameTool.setProcessorItemList(Arrays.asList(processorItem));
		when(kpiHelperService.populateSCMToolsRepoList(anyMap()))
				.thenReturn(Arrays.asList(repoNameTool));

		List<ScmMergeRequests> mrs = new ArrayList<>();
		mrs.add(createMergeRequest("dev@example.com", true, "merged", "Revert change"));
		when(scmKpiHelperService.getMergedRequests(any(), any())).thenReturn(mrs);
		when(scmKpiHelperService.getJiraAssigneeForScmUsers(any())).thenReturn(new ArrayList<>());

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				service.getKpiData(
						kpiRequest,
						kpiRequest.getKpiList().get(0),
						detail.getMapOfListOfProjectNodes().get("project").get(0));

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_multipleDataPoints() throws Exception {
		kpiRequest.setXAxisDataPoints(3);
		List<ScmMergeRequests> mrs = new ArrayList<>();
		mrs.add(createMergeRequest("dev1@example.com", true, "merged", "Revert PR #1"));
		mrs.add(createMergeRequest("dev2@example.com", true, "merged", "Normal PR"));
		when(scmKpiHelperService.getMergedRequests(any(), any())).thenReturn(mrs);
		when(scmKpiHelperService.getJiraAssigneeForScmUsers(any())).thenReturn(new ArrayList<>());

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				service.getKpiData(
						kpiRequest,
						kpiRequest.getKpiList().get(0),
						detail.getMapOfListOfProjectNodes().get("project").get(0));

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_caseInsensitiveRevertDetection() throws Exception {
		List<ScmMergeRequests> mrs = new ArrayList<>();
		mrs.add(createMergeRequest("dev@example.com", true, "merged", "REVERT: important change"));
		mrs.add(createMergeRequest("dev@example.com", true, "merged", "Revert \"Add thing\""));
		mrs.add(createMergeRequest("dev@example.com", true, "merged", "revert old code"));
		when(scmKpiHelperService.getMergedRequests(any(), any())).thenReturn(mrs);
		when(scmKpiHelperService.getJiraAssigneeForScmUsers(any())).thenReturn(new ArrayList<>());

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				service.getKpiData(
						kpiRequest,
						kpiRequest.getKpiList().get(0),
						detail.getMapOfListOfProjectNodes().get("project").get(0));

		assertNotNull(kpiElement);
		assertNotNull(kpiElement.getTrendValueList());
	}

	@Test
	public void testGetKpiData_nullTitle() throws Exception {
		List<ScmMergeRequests> mrs = new ArrayList<>();
		mrs.add(createMergeRequest("dev@example.com", true, "merged", null));
		when(scmKpiHelperService.getMergedRequests(any(), any())).thenReturn(mrs);
		when(scmKpiHelperService.getJiraAssigneeForScmUsers(any())).thenReturn(new ArrayList<>());

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				service.getKpiData(
						kpiRequest,
						kpiRequest.getKpiList().get(0),
						detail.getMapOfListOfProjectNodes().get("project").get(0));

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_nullProcessorItemList() throws Exception {
		Tool invalidTool = new Tool();
		invalidTool.setBranch("main");
		invalidTool.setProcessorItemList(null);
		when(kpiHelperService.populateSCMToolsRepoList(anyMap()))
				.thenReturn(Arrays.asList(invalidTool));

		when(scmKpiHelperService.getMergedRequests(any(), any())).thenReturn(new ArrayList<>());
		when(scmKpiHelperService.getJiraAssigneeForScmUsers(any())).thenReturn(new ArrayList<>());

		TreeAggregatorDetail detail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		KpiElement kpiElement =
				service.getKpiData(
						kpiRequest,
						kpiRequest.getKpiList().get(0),
						detail.getMapOfListOfProjectNodes().get("project").get(0));

		assertNotNull(kpiElement);
	}
}
