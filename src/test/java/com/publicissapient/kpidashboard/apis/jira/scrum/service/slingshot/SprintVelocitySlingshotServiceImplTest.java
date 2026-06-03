package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.KpiDataCacheService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.data.AccountHierarchyFilterDataFactory;
import com.publicissapient.kpidashboard.apis.data.FieldMappingDataFactory;
import com.publicissapient.kpidashboard.apis.data.JiraIssueDataFactory;
import com.publicissapient.kpidashboard.apis.data.KpiRequestFactory;
import com.publicissapient.kpidashboard.apis.data.SprintDetailsDataFactory;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.jira.service.SprintVelocityServiceHelper;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepositoryCustom;

@RunWith(MockitoJUnitRunner.class)
public class SprintVelocitySlingshotServiceImplTest {

	private static final String SPRINTVELOCITYKEY = "sprintVelocityKey";
	private static final String SPRINT_WISE_SPRINTDETAILS = "sprintWiseSprintDetailMap";

	@Mock private KpiHelperService kpiHelperService;
	@Mock private CustomApiConfig customApiConfig;
	@Mock private ConfigHelperService configHelperService;
	@Mock private SprintVelocityServiceHelper velocityHelper;
	@Mock private SprintRepositoryCustom sprintRepositoryCustom;
	@Mock private FilterHelperService flterHelperService;
	@Mock private FilterHelperService filterHelperService;
	@Mock private CacheService cacheService;
	@Mock private KpiDataCacheService kpiDataCacheService;
	@Mock private JiraIssueRepository jiraIssueRepository;

	@InjectMocks private SprintVelocitySlingshotServiceImpl service;

	private KpiRequest kpiRequest;
	private List<AccountHierarchyData> accountHierarchyDataList = new ArrayList<>();
	private Map<ObjectId, FieldMapping> fieldMappingMap = new HashMap<>();
	private List<JiraIssue> totalIssueList = new ArrayList<>();
	private List<SprintDetails> sprintDetailsList = new ArrayList<>();
	private TreeAggregatorDetail treeAggregatorDetail;
	private List<Node> leafNodeList = new ArrayList<>();

	@Before
	public void setup() throws ApplicationException {
		KpiRequestFactory kpiRequestFactory = KpiRequestFactory.newInstance();
		kpiRequest = kpiRequestFactory.findKpiRequest(KPICode.DEFECT_REMOVAL_EFFICIENCY.getKpiId());
		kpiRequest.setLabel("PROJECT");

		AccountHierarchyFilterDataFactory accountHierarchyFilterDataFactory =
				AccountHierarchyFilterDataFactory.newInstance();
		accountHierarchyDataList = accountHierarchyFilterDataFactory.getAccountHierarchyDataList();

		JiraIssueDataFactory jiraIssueDataFactory = JiraIssueDataFactory.newInstance();
		totalIssueList = jiraIssueDataFactory.getBugs();

		SprintDetailsDataFactory sprintDetailsDataFactory = SprintDetailsDataFactory.newInstance();
		sprintDetailsList = sprintDetailsDataFactory.getSprintDetails();

		ProjectBasicConfig projectConfig = new ProjectBasicConfig();
		projectConfig.setId(new ObjectId("6335363749794a18e8a4479b"));
		projectConfig.setProjectName("Scrum Project");
		projectConfig.setProjectNodeId("Scrum Project_6335363749794a18e8a4479b");

		FieldMappingDataFactory fieldMappingDataFactory =
				FieldMappingDataFactory.newInstance("/json/default/scrum_project_field_mappings.json");
		FieldMapping fieldMapping = fieldMappingDataFactory.getFieldMappings().get(0);
		fieldMappingMap.put(fieldMapping.getBasicProjectConfigId(), fieldMapping);
		configHelperService.setFieldMappingMap(fieldMappingMap);

		treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		leafNodeList =
				KPIHelperUtil.getLeafNodes(treeAggregatorDetail.getRoot(), new ArrayList<>(), false);

		// Mock for getColumns calls
		when(cacheService.cacheAccountHierarchyData()).thenReturn(accountHierarchyDataList);
		when(flterHelperService.getAdditionalFilterHierarchyLevel()).thenReturn(new HashMap<>());
		when(filterHelperService.getAdditionalFilterHierarchyLevel()).thenReturn(new HashMap<>());
		when(cacheService.cacheProjectConfigMapData()).thenReturn(new HashMap<>());
	}

	@Test
	public void testGetQualifierType() {
		assertThat(service.getQualifierType(), equalTo(KPICode.SPRINT_VELOCITY_SLINGSHOT.name()));
	}

	@Test
	public void testCalculateKPIMetrics() {
		Map<String, Object> dataMap = new HashMap<>();
		dataMap.put(SPRINTVELOCITYKEY, totalIssueList);
		Double result = service.calculateKPIMetrics(dataMap);
		assertNotNull(result);
	}

	@Test
	public void testCalculateKpiValue() {
		List<Double> valueList = Arrays.asList(5.0, 10.0, 15.0);
		Double result = service.calculateKpiValue(valueList, KPICode.SPRINT_VELOCITY_SLINGSHOT.name());
		assertNotNull(result);
	}

	@Test
	public void testCalculateThresholdValue() throws ApplicationException {
		FieldMappingDataFactory factory =
				FieldMappingDataFactory.newInstance("/json/default/scrum_project_field_mappings.json");
		FieldMapping fieldMapping = factory.getFieldMappings().get(0);
		// should not throw
		service.calculateThresholdValue(fieldMapping);
	}

	@Test
	public void testFetchKPIDataFromDb_withCachedData() throws ApplicationException {
		Map<String, Object> resultListMap = new HashMap<>();
		resultListMap.put(SPRINTVELOCITYKEY, totalIssueList);
		resultListMap.put(SPRINT_WISE_SPRINTDETAILS, sprintDetailsList);

		when(flterHelperService.isFilterSelectedTillSprintLevel(anyInt(), anyBoolean()))
				.thenReturn(true);
		when(kpiDataCacheService.fetchSprintVelocityData(any(), any(), any()))
				.thenReturn(resultListMap);

		Map<String, Object> result = service.fetchKPIDataFromDb(leafNodeList, null, null, kpiRequest);

		assertNotNull(result);
		assertThat(
				((List<JiraIssue>) result.get(SPRINTVELOCITYKEY)).size(), equalTo(totalIssueList.size()));
	}

	@Test
	public void testFetchKPIDataFromDb_withDbData() throws ApplicationException {
		Map<String, Object> resultListMap = new HashMap<>();
		resultListMap.put(SPRINTVELOCITYKEY, totalIssueList);
		resultListMap.put(SPRINT_WISE_SPRINTDETAILS, sprintDetailsList);

		when(flterHelperService.isFilterSelectedTillSprintLevel(anyInt(), anyBoolean()))
				.thenReturn(false);
		when(customApiConfig.getSprintVelocityLimit()).thenReturn(5);
		when(customApiConfig.getSprintCountForFilters()).thenReturn(3);
		when(sprintRepositoryCustom.findByBasicProjectConfigIdInAndStateInOrderByStartDateDesc(
						any(), any(), anyLong()))
				.thenReturn(sprintDetailsList);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(kpiHelperService.getProjectWiseTotalSprintDetail(any())).thenReturn(new HashMap<>());
		when(jiraIssueRepository.findIssuesBySprintAndType(any(), any())).thenReturn(totalIssueList);

		Map<String, Object> result = service.fetchKPIDataFromDb(leafNodeList, null, null, kpiRequest);

		assertNotNull(result);
	}

	@Test
	public void testFetchKPIDataFromDb_publicMethod_withSprintDetails() {
		List<String> configIds = new ArrayList<>();
		configIds.add("6335363749794a18e8a4479b");

		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(kpiHelperService.getProjectWiseTotalSprintDetail(any())).thenReturn(new HashMap<>());
		when(jiraIssueRepository.findIssuesBySprintAndType(any(), any())).thenReturn(totalIssueList);

		Map<String, Object> result =
				service.fetchSprintVelocityDataFromDb(kpiRequest, configIds, sprintDetailsList);

		assertNotNull(result);
	}

	@Test
	public void testFetchKPIDataFromDb_publicMethod_emptySprintDetails() {
		List<String> configIds = new ArrayList<>();
		configIds.add("6335363749794a18e8a4479b");

		Map<String, Object> result =
				service.fetchSprintVelocityDataFromDb(kpiRequest, configIds, new ArrayList<>());

		assertNotNull(result);
	}

	@Test
	public void testGetKpiData() throws ApplicationException {
		String kpiRequestTrackerId = "Jira-5be544de025de212549176a9";
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRA.name()))
				.thenReturn(kpiRequestTrackerId);

		Map<String, Object> resultListMap = new HashMap<>();
		resultListMap.put(SPRINTVELOCITYKEY, totalIssueList);
		resultListMap.put(SPRINT_WISE_SPRINTDETAILS, sprintDetailsList);

		when(flterHelperService.isFilterSelectedTillSprintLevel(anyInt(), anyBoolean()))
				.thenReturn(true);
		when(kpiDataCacheService.fetchSprintVelocityData(any(), any(), any()))
				.thenReturn(resultListMap);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);

		Map<Pair<String, String>, Set<JiraIssue>> velocityMap = new HashMap<>();
		when(customApiConfig.getApplicationDetailedLogger()).thenReturn("off");
		when(customApiConfig.getSprintVelocityLimit()).thenReturn(5);

		KpiElement kpiElement =
				service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);

		assertNotNull(kpiElement);
		assertNotNull(kpiElement.getTrendValueList());
	}

	@Test
	public void testGetKpiData_withExcelTracker() throws ApplicationException {
		String kpiRequestTrackerId = "Excel-Jira-5be544de025de212549176a9";
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRA.name()))
				.thenReturn(kpiRequestTrackerId);

		Map<String, Object> resultListMap = new HashMap<>();
		resultListMap.put(SPRINTVELOCITYKEY, totalIssueList);
		resultListMap.put(SPRINT_WISE_SPRINTDETAILS, sprintDetailsList);

		when(flterHelperService.isFilterSelectedTillSprintLevel(anyInt(), anyBoolean()))
				.thenReturn(true);
		when(kpiDataCacheService.fetchSprintVelocityData(any(), any(), any()))
				.thenReturn(resultListMap);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(customApiConfig.getApplicationDetailedLogger()).thenReturn("off");
		when(customApiConfig.getSprintVelocityLimit()).thenReturn(5);

		KpiElement kpiElement =
				service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_emptySprintDetails() throws ApplicationException {
		String kpiRequestTrackerId = "Jira-5be544de025de212549176a9";
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRA.name()))
				.thenReturn(kpiRequestTrackerId);

		Map<String, Object> resultListMap = new HashMap<>();
		resultListMap.put(SPRINTVELOCITYKEY, new ArrayList<>());
		resultListMap.put(SPRINT_WISE_SPRINTDETAILS, new ArrayList<>());

		when(flterHelperService.isFilterSelectedTillSprintLevel(anyInt(), anyBoolean()))
				.thenReturn(true);
		when(kpiDataCacheService.fetchSprintVelocityData(any(), any(), any()))
				.thenReturn(resultListMap);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(customApiConfig.getApplicationDetailedLogger()).thenReturn("off");
		when(customApiConfig.getSprintVelocityLimit()).thenReturn(5);

		KpiElement kpiElement =
				service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);

		assertNotNull(kpiElement);
	}

	@Test
	public void testGetKpiData_withDetailedLogger() throws ApplicationException {
		String kpiRequestTrackerId = "Jira-5be544de025de212549176a9";
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRA.name()))
				.thenReturn(kpiRequestTrackerId);

		Map<String, Object> resultListMap = new HashMap<>();
		resultListMap.put(SPRINTVELOCITYKEY, totalIssueList);
		resultListMap.put(SPRINT_WISE_SPRINTDETAILS, sprintDetailsList);

		when(flterHelperService.isFilterSelectedTillSprintLevel(anyInt(), anyBoolean()))
				.thenReturn(true);
		when(kpiDataCacheService.fetchSprintVelocityData(any(), any(), any()))
				.thenReturn(resultListMap);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(customApiConfig.getApplicationDetailedLogger()).thenReturn("on");
		when(customApiConfig.getSprintVelocityLimit()).thenReturn(5);

		// velocity helper returns non-empty issues for logger path
		Set<JiraIssue> issueSet = new HashSet<>(totalIssueList);
		when(velocityHelper.calculateSprintVelocityValue(any(), any(), any())).thenReturn(10.0);

		KpiElement kpiElement =
				service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);

		assertNotNull(kpiElement);
	}

	@Test
	public void testFetchKPIDataFromDb_withDuplicateIssuesAndFieldMapping() {
		List<String> configIds = new ArrayList<>();
		String projectId = "6335363749794a18e8a4479b";
		configIds.add(projectId);
		ObjectId objectId = new ObjectId(projectId);

		Map<ObjectId, Set<String>> duplicateIssues = new HashMap<>();
		Set<String> issueIds = new HashSet<>(Arrays.asList("ISSUE-1", "ISSUE-2"));
		duplicateIssues.put(objectId, issueIds);

		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(kpiHelperService.getProjectWiseTotalSprintDetail(any())).thenReturn(duplicateIssues);
		when(kpiHelperService.getMinimumClosedDateFromConfiguration(any(), any()))
				.thenReturn(new HashMap<>());
		when(jiraIssueRepository.findIssuesBySprintAndType(any(), any())).thenReturn(totalIssueList);

		Map<String, Object> result =
				service.fetchSprintVelocityDataFromDb(kpiRequest, configIds, sprintDetailsList);

		assertNotNull(result);
	}

	@Test
	public void testCalculateKPIMetrics_emptyList() {
		Map<String, Object> dataMap = new HashMap<>();
		dataMap.put(SPRINTVELOCITYKEY, new ArrayList<>());
		Double result = service.calculateKPIMetrics(dataMap);
		assertThat(result, equalTo(0.0));
	}

	@Test
	public void testFetchKPIDataFromDb_cachedDataReturnsNull() {
		Map<String, Object> resultListMap = new HashMap<>();
		resultListMap.put(SPRINTVELOCITYKEY, totalIssueList);
		resultListMap.put(SPRINT_WISE_SPRINTDETAILS, sprintDetailsList);

		when(flterHelperService.isFilterSelectedTillSprintLevel(anyInt(), anyBoolean()))
				.thenReturn(true);
		// Return map with lists for all projects
		when(kpiDataCacheService.fetchSprintVelocityData(any(), any(), any()))
				.thenReturn(resultListMap);

		Map<String, Object> result = service.fetchKPIDataFromDb(leafNodeList, null, null, kpiRequest);

		assertNotNull(result);
	}

	@Test
	public void testFetchKPIDataFromDb_dbData_emptySprintDetails() {
		// Test the public overload with empty sprint details
		List<String> configIds = List.of("6335363749794a18e8a4479b");
		Map<String, Object> result =
				service.fetchSprintVelocityDataFromDb(kpiRequest, configIds, new ArrayList<>());
		assertNotNull(result);
	}
}
