/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/
package com.publicissapient.kpidashboard.apis.zephyr.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.enums.Filters;
import com.publicissapient.kpidashboard.apis.enums.JiraFeature;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.common.constant.NormalizedJira;
import com.publicissapient.kpidashboard.common.constant.ProcessorConstants;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectToolConfig;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.SprintWiseStory;
import com.publicissapient.kpidashboard.common.model.zephyr.TestCaseDetails;
import com.publicissapient.kpidashboard.common.model.zephyr.TestCaseExecutionData;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.repository.zephyr.TestCaseDetailsRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TestExecutionTimeServiceImpl
		extends ZephyrKPIService<Double, List<Object>, Map<String, Object>> {

	@Autowired private ConfigHelperService configHelperService;
	@Autowired private CacheService cacheService;
	@Autowired private JiraIssueRepository jiraIssueRepository;
	@Autowired TestCaseDetailsRepository testCaseDetailsRepository;

	private static final String SPRINTSTORIES = "storyData";
	private static final String TESTCASEKEY = "testCaseData";
	private static final String ISSUE_DATA = "issueData";
	private static final String AUTOMATEDTESTCASEKEY = "automatedTestCaseData";
	private static final String MANUALTESTCASEKEY = "manualTestCaseData";
	private static final String AVGEXECUTIONTIME = "avgExecutionTimeSec";
	private static final String COUNT = "count";
	private static final String TOOL_ZEPHYR = ProcessorConstants.ZEPHYR;
	private static final String TOOL_JIRA_TEST = ProcessorConstants.JIRA_TEST;
	private static final String DEVELOPER_KPI = "DeveloperKpi";
	private static final String NIN = "nin";

	@Override
	public String getQualifierType() {
		return KPICode.TEST_EXECUTION_TIME.name();
	}

	@Override
	public KpiElement getKpiData(
			KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
			throws ApplicationException {

		List<DataCount> trendValueList = new ArrayList<>();
		Node root = treeAggregatorDetail.getRoot();
		Map<String, Node> mapTmp = treeAggregatorDetail.getMapTmp();
		treeAggregatorDetail
				.getMapOfListOfLeafNodes()
				.forEach(
						(k, v) -> {
							if (Filters.getFilter(k) == Filters.SPRINT) {
								sprintWiseLeafNodeValue(mapTmp, v, trendValueList, kpiElement, kpiRequest);
							}
						});

		log.debug(
				"[TEST-EXECUTION-TIME-LEAF-NODE-VALUE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				root);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValue(root, nodeWiseKPIValue, KPICode.TEST_EXECUTION_TIME);

		List<DataCount> trendValues =
				getTrendValues(kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.TEST_EXECUTION_TIME);
		kpiElement.setTrendValueList(trendValues);
		return kpiElement;
	}

	/**
	 * Populates KPI value to sprint leaf nodes. It also gives the trend analysis at sprint wise.
	 *
	 * @param mapTmp
	 * @param sprintLeafNodeList
	 */
	@SuppressWarnings("unchecked")
	private void sprintWiseLeafNodeValue(
			Map<String, Node> mapTmp,
			List<Node> sprintLeafNodeList,
			List<DataCount> trendValueList,
			KpiElement kpiElement,
			KpiRequest kpiRequest) {

		String requestTrackerId = getRequestTrackerId();
		Collections.sort(
				sprintLeafNodeList,
				(Node o1, Node o2) ->
						o1.getSprintFilter().getStartDate().compareTo(o2.getSprintFilter().getStartDate()));

		Map<String, Object> defectDataListMap =
				fetchKPIDataFromDb(sprintLeafNodeList, null, null, kpiRequest);

		List<SprintWiseStory> sprintWiseStoryList =
				(List<SprintWiseStory>) defectDataListMap.getOrDefault(SPRINTSTORIES, new ArrayList<>());

		Map<Pair<String, String>, List<SprintWiseStory>> sprintWiseMap =
				sprintWiseStoryList.stream()
						.collect(
								Collectors.groupingBy(
										sws -> Pair.of(sws.getBasicProjectConfigId(), sws.getSprint()),
										Collectors.toList()));

		List<TestCaseDetails> testCaseList =
				(List<TestCaseDetails>) defectDataListMap.getOrDefault(TESTCASEKEY, new ArrayList<>());
		Map<String, Set<JiraIssue>> projectWiseStories =
				((List<JiraIssue>) defectDataListMap.getOrDefault(ISSUE_DATA, new ArrayList<>()))
						.stream()
								.collect(
										Collectors.groupingBy(JiraIssue::getBasicProjectConfigId, Collectors.toSet()));

		Map<Pair<String, String>, List<TestCaseDetails>> sprintWiseAutoTestMap = new HashMap<>();
		Map<Pair<String, String>, List<TestCaseDetails>> sprintWiseManualTestMap = new HashMap<>();
		Map<Pair<String, String>, List<TestCaseDetails>> sprintWiseTotalTestMap = new HashMap<>();

		sprintWiseMap.forEach(
				(sprintFilter, sprintWiseStories) -> {
					List<TestCaseDetails> sprintWiseAutomatedTestList = new ArrayList<>();
					List<TestCaseDetails> sprintWiseTotalTestList = new ArrayList<>();
					List<TestCaseDetails> sprintWiseManualTestList = new ArrayList<>();

					sprintWiseAutomatedTestList.addAll(
							getAutomatedTestCases(testCaseList, sprintWiseStories));
					sprintWiseManualTestList.addAll(getManualTestCases(testCaseList, sprintWiseStories));
					sprintWiseTotalTestList.addAll(getTotalTestCases(testCaseList, sprintWiseStories));
					Map<String, Object> currentSprintLeafNodeDefectDataMap = new HashMap<>();
					currentSprintLeafNodeDefectDataMap.put(
							AUTOMATEDTESTCASEKEY, getAutomatedTestCases(testCaseList, sprintWiseStories));
					currentSprintLeafNodeDefectDataMap.put(
							TESTCASEKEY, getTotalTestCases(testCaseList, sprintWiseStories));

					sprintWiseManualTestMap.put(sprintFilter, sprintWiseManualTestList);
					sprintWiseAutoTestMap.put(sprintFilter, sprintWiseAutomatedTestList);
					sprintWiseTotalTestMap.put(sprintFilter, sprintWiseTotalTestList);
				});
		List<KPIExcelData> excelData = new ArrayList<>();
		sprintLeafNodeList.forEach(
				node -> {
					String validationKey = node.getSprintFilter().getName();
					// Leaf node wise data
					String trendLineName = node.getProjectFilter().getName();

					Pair<String, String> currentNodeIdentifier =
							Pair.of(
									node.getProjectFilter().getBasicProjectConfigId().toString(),
									node.getSprintFilter().getId());

					Map<String, Object> hoverMap = new LinkedHashMap<>();
					Map<String, Object> currentSprintLeafNodeDefectDataMap = new HashMap<>();
					currentSprintLeafNodeDefectDataMap.put(
							MANUALTESTCASEKEY, sprintWiseManualTestMap.get(currentNodeIdentifier));
					currentSprintLeafNodeDefectDataMap.put(
							AUTOMATEDTESTCASEKEY, sprintWiseAutoTestMap.get(currentNodeIdentifier));
					currentSprintLeafNodeDefectDataMap.put(
							TESTCASEKEY, sprintWiseTotalTestMap.get(currentNodeIdentifier));

					populateExcelDataObject(
							requestTrackerId,
							currentSprintLeafNodeDefectDataMap,
							excelData,
							validationKey,
							projectWiseStories.get(node.getProjectFilter().getBasicProjectConfigId().toString()));
					setHoverMap(
							sprintWiseAutoTestMap,
							sprintWiseManualTestMap,
							sprintWiseTotalTestMap,
							currentNodeIdentifier,
							hoverMap);

					double executionTimeForCurrentLeaf = 0.0;
					executionTimeForCurrentLeaf =
							getValueFromHoverMap(hoverMap, "TOTAL", AVGEXECUTIONTIME, Double.class);

					mapTmp.get(node.getId()).setValue(executionTimeForCurrentLeaf);

					log.debug(
							"[TEST-AUTOMATION-SPRINT-WISE][{}]. TEST-AUTOMATION for sprint {}  is {}",
							requestTrackerId,
							node.getSprintFilter().getName(),
							executionTimeForCurrentLeaf);
					DataCount dataCount = new DataCount();
					dataCount.setData(String.valueOf(executionTimeForCurrentLeaf));
					dataCount.setSProjectName(trendLineName);
					dataCount.setSprintIds(new ArrayList<>(Arrays.asList(node.getSprintFilter().getId())));
					dataCount.setSprintNames(
							new ArrayList<>(Arrays.asList(node.getSprintFilter().getName())));
					dataCount.setSSprintID(node.getSprintFilter().getId());
					dataCount.setSSprintName(node.getSprintFilter().getName());
					dataCount.setHoverValue(hoverMap);
					dataCount.setValue(executionTimeForCurrentLeaf);
					mapTmp.get(node.getId()).setValue(new ArrayList<>(Arrays.asList(dataCount)));
					trendValueList.add(dataCount);
				});
		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(KPIExcelColumn.TEST_EXECUTION_TIME.getColumns());
	}

	/**
	 * Sets Hover map with test case counts and average execution times
	 *
	 * @param sprintWiseAutomatedMap map of automated test cases per sprint
	 * @param sprintWiseManualMap map of manual test cases per sprint
	 * @param sprintWiseTotalMap map of total test cases per sprint
	 * @param sprintId sprint identifier
	 * @param hoverMap output map to populate
	 */
	private void setHoverMap(
			Map<Pair<String, String>, List<TestCaseDetails>> sprintWiseAutomatedMap,
			Map<Pair<String, String>, List<TestCaseDetails>> sprintWiseManualMap,
			Map<Pair<String, String>, List<TestCaseDetails>> sprintWiseTotalMap,
			Pair<String, String> sprintId,
			Map<String, Object> hoverMap) {

		// Automated test cases
		populateCategoryData("AUTOMATED", sprintWiseAutomatedMap.get(sprintId), hoverMap);

		// Manual test cases
		populateCategoryData("MANUAL", sprintWiseManualMap.get(sprintId), hoverMap);

		// Total test cases
		populateCategoryData("TOTAL", sprintWiseTotalMap.get(sprintId), hoverMap);
	}

	/** Helper method to compute count & average execution time for a category */
	private void populateCategoryData(
			String category, List<TestCaseDetails> testCases, Map<String, Object> hoverMap) {
		if (CollectionUtils.isNotEmpty(testCases)) {
			int totalCount = testCases.size();

			// Compute per-test average execution time in **minutes**
			double avgExecTimeMin =
					BigDecimal.valueOf(
							testCases.stream()
											.mapToDouble(
													tc -> {
														if (CollectionUtils.isEmpty(tc.getExecutions())) {
															return 0.0;
														}
														double sum =
																tc.getExecutions().stream()
																		.filter(exec -> exec.getExecutionTime() != null)
																		.mapToDouble(TestCaseExecutionData::getExecutionTime)
																		.sum();

														long count =
																tc.getExecutions().stream()
																		.filter(exec -> exec.getExecutionTime() != null)
																		.count();

														return count > 0 ? (sum / count) : 0.0;
													})
											.average()
											.orElse(0.0)
									/ 1000.0
									/ 60.0 // ms → sec → min
							).setScale(2, RoundingMode.HALF_UP)
							.doubleValue();

			// Put results into hover map
			Map<String, Object> categoryData = new HashMap<>();
			categoryData.put(COUNT, totalCount);
			categoryData.put(AVGEXECUTIONTIME, avgExecTimeMin); // in minutes

			hoverMap.put(category, categoryData);
		} else {
			Map<String, Object> categoryData = new HashMap<>();
			categoryData.put(COUNT, 0);
			categoryData.put(AVGEXECUTIONTIME, 0L); // 0 minutes
			hoverMap.put(category, categoryData);
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T getValueFromHoverMap(
			Map<String, Object> hoverMap, String category, String key, Class<T> type) {
		if (hoverMap == null || !hoverMap.containsKey(category)) {
			return null;
		}

		Object categoryObj = hoverMap.get(category);
		if (!(categoryObj instanceof Map)) {
			return null;
		}

		Map<String, Object> categoryMap = (Map<String, Object>) categoryObj;
		Object value = categoryMap.get(key);

		if (value == null) {
			return null;
		}

		if (type.isInstance(value)) {
			return type.cast(value);
		}

		// attempt conversion if possible (e.g. Integer -> Double)
		if (type == Double.class && value instanceof Number) {
			return (T) Double.valueOf(((Number) value).doubleValue());
		}
		if (type == Integer.class && value instanceof Number) {
			return (T) Integer.valueOf(((Number) value).intValue());
		}
		if (type == Long.class && value instanceof Number) {
			return (T) Long.valueOf(((Number) value).longValue());
		}

		return null; // type mismatch
	}

	private void populateExcelDataObject(
			String requestTrackerId,
			Map<String, Object> currentSprintLeafNodeDefectDataMap,
			List<KPIExcelData> excelData,
			String sprint,
			Set<JiraIssue> jiraIssues) {

		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			List<TestCaseDetails> automatedTest =
					(List<TestCaseDetails>) currentSprintLeafNodeDefectDataMap.get(AUTOMATEDTESTCASEKEY);
			List<TestCaseDetails> manualTest =
					(List<TestCaseDetails>) currentSprintLeafNodeDefectDataMap.get(MANUALTESTCASEKEY);
			List<TestCaseDetails> totalTest =
					(List<TestCaseDetails>) currentSprintLeafNodeDefectDataMap.get(TESTCASEKEY);
			KPIExcelUtility.populateTestExecutionTimeExcelData(
					sprint,
					totalTest,
					automatedTest,
					manualTest,
					excelData,
					KPICode.TEST_EXECUTION_TIME.getKpiId(),
					"");
		}
	}

	/**
	 * @param testCaseList
	 * @return
	 */
	private List<TestCaseDetails> getAutomatedTestCases(
			List<TestCaseDetails> testCaseList, List<SprintWiseStory> sprintWiseStory) {
		return testCaseList.stream()
				.filter(
						tc ->
								sprintWiseStory.stream()
										.anyMatch(
												st ->
														st.getBasicProjectConfigId().equals(tc.getBasicProjectConfigId())
																&& CollectionUtils.isNotEmpty(tc.getDefectStoryID())
																&& CollectionUtils.containsAny(
																		tc.getDefectStoryID(), st.getStoryList())
																&& NormalizedJira.YES_VALUE
																		.getValue()
																		.equals(tc.getIsTestAutomated())))
				.toList();
	}

	/**
	 * @param testCaseList
	 * @return
	 */
	private List<TestCaseDetails> getManualTestCases(
			List<TestCaseDetails> testCaseList, List<SprintWiseStory> sprintWiseStory) {
		return testCaseList.stream()
				.filter(
						tc ->
								sprintWiseStory.stream()
										.anyMatch(
												st ->
														st.getBasicProjectConfigId().equals(tc.getBasicProjectConfigId())
																&& CollectionUtils.isNotEmpty(tc.getDefectStoryID())
																&& CollectionUtils.containsAny(
																		tc.getDefectStoryID(), st.getStoryList())
																&& NormalizedJira.NO_VALUE
																		.getValue()
																		.equals(tc.getIsTestAutomated())))
				.toList();
	}

	/**
	 * @param testCaseList
	 * @return
	 */
	private List<TestCaseDetails> getTotalTestCases(
			List<TestCaseDetails> testCaseList, List<SprintWiseStory> sprintWiseStory) {
		return testCaseList.stream()
				.filter(
						tc ->
								sprintWiseStory.stream()
										.anyMatch(
												st ->
														st.getBasicProjectConfigId().equals(tc.getBasicProjectConfigId())
																&& CollectionUtils.isNotEmpty(tc.getDefectStoryID())
																&& CollectionUtils.containsAny(
																		tc.getDefectStoryID(), st.getStoryList())))
				.toList();
	}

	@Override
	public Double calculateKPIMetrics(Map<String, Object> stringObjectMap) {
		return 0.0;
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {

		Map<String, Object> resultListMap = new HashMap<>();
		Map<String, List<String>> mapOfFilters = new LinkedHashMap<>();
		List<String> sprintList = new ArrayList<>();
		List<String> basicProjectConfigIds = new ArrayList<>();
		Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();
		Map<String, Map<String, Object>> uniqueProjectMapForTestCase = new HashMap<>();
		Map<String, String> sprintProjectIdMap = new HashMap<>();
		Map<ObjectId, Map<String, List<ProjectToolConfig>>> toolMap =
				(Map<ObjectId, Map<String, List<ProjectToolConfig>>>)
						cacheService.cacheProjectToolConfigMapData();
		Map<ObjectId, FieldMapping> basicProjetWiseConfig = configHelperService.getFieldMappingMap();
		leafNodeList.forEach(
				leaf -> {
					ObjectId basicProjectConfigId = leaf.getProjectFilter().getBasicProjectConfigId();
					List<ProjectToolConfig> zephyrTools =
							getToolConfigBasedOnProcessors(toolMap, basicProjectConfigId, TOOL_ZEPHYR);

					List<ProjectToolConfig> jiraTestTools =
							getToolConfigBasedOnProcessors(toolMap, basicProjectConfigId, TOOL_JIRA_TEST);

					Map<String, Object> mapOfProjectFilters = new LinkedHashMap<>();
					Map<String, Object> mapOfFolderPathFilters = new LinkedHashMap<>();
					Map<String, Object> mapOfProjectFiltersNotIn = new LinkedHashMap<>();
					FieldMapping fieldMapping = basicProjetWiseConfig.get(basicProjectConfigId);

					sprintList.add(leaf.getSprintFilter().getId());
					basicProjectConfigIds.add(basicProjectConfigId.toString());
					sprintProjectIdMap.put(leaf.getSprintFilter().getId(), basicProjectConfigId.toString());

					mapOfProjectFilters.put(
							JiraFeature.ISSUE_TYPE.getFieldValueInFeature(),
							CommonUtils.convertToPatternList(fieldMapping.getJiraTestAutomationIssueType()));
					// if Zephyr scale as a tool is setup with project
					if (CollectionUtils.isNotEmpty(zephyrTools)) {
						List<String> sprintAutomationFolderPath = new ArrayList<>();
						zephyrTools.forEach(
								tool -> {
									if (CollectionUtils.isNotEmpty(tool.getInSprintAutomationFolderPath())) {
										sprintAutomationFolderPath.addAll(tool.getInSprintAutomationFolderPath());
									}
								});
						if (CollectionUtils.isNotEmpty(sprintAutomationFolderPath)) {
							mapOfFolderPathFilters.put(
									JiraFeature.ATM_TEST_FOLDER.getFieldValueInFeature(),
									CommonUtils.convertTestFolderToPatternList(sprintAutomationFolderPath));
						}
					}
					// if Zephyr squad as a jira plguin is setup with project
					if (CollectionUtils.isNotEmpty(jiraTestTools)) {
						jiraTestTools.forEach(
								tool -> {
									if (CollectionUtils.isNotEmpty(tool.getTestCaseStatus())) {
										mapOfProjectFiltersNotIn.put(
												JiraFeature.TEST_CASE_STATUS.getFieldValueInFeature(),
												CommonUtils.convertTestFolderToPatternList(tool.getTestCaseStatus()));
									}
								});
					}

					uniqueProjectMap.put(basicProjectConfigId.toString(), mapOfProjectFilters);
				});

		mapOfFilters.put(
				JiraFeature.SPRINT_ID.getFieldValueInFeature(), sprintList.stream().distinct().toList());
		mapOfFilters.put(
				JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
				basicProjectConfigIds.stream().distinct().toList());

		List<SprintWiseStory> sprintWiseStoryList =
				jiraIssueRepository.findIssuesGroupBySprint(
						mapOfFilters, uniqueProjectMap, kpiRequest.getFilterToShowOnTrend(), DEVELOPER_KPI);

		Map<String, List<String>> projectStoryNumberMap = new HashMap<>();
		List<String> storyIdList = new ArrayList<>();
		sprintWiseStoryList.forEach(
				s -> {
					String basicProjConfId = sprintProjectIdMap.get(s.getSprint());
					projectStoryNumberMap.putIfAbsent(basicProjConfId, new ArrayList<>());
					projectStoryNumberMap.get(basicProjConfId).addAll(s.getStoryList());
					storyIdList.addAll(s.getStoryList());
				});

		projectStoryNumberMap.forEach(
				(k, v) -> {
					Map<String, Object> mapOfProjectFilters = new LinkedHashMap<>();
					uniqueProjectMapForTestCase.putIfAbsent(k, mapOfProjectFilters);
					uniqueProjectMapForTestCase
							.get(k)
							.put(
									JiraFeature.DEFECT_STORY_ID.getFieldValueInFeature(),
									v.stream().distinct().toList());
				});
		Map<String, List<String>> mapOfFiltersStoryQuery = new LinkedHashMap<>();
		mapOfFiltersStoryQuery.put(
				JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
				basicProjectConfigIds.stream().distinct().toList());

		List<TestCaseDetails> testCasesList =
				testCaseDetailsRepository.findTestDetails(
						mapOfFiltersStoryQuery, uniqueProjectMapForTestCase, NIN);

		resultListMap.put(SPRINTSTORIES, sprintWiseStoryList);
		resultListMap.put(TESTCASEKEY, testCasesList);
		resultListMap.put(ISSUE_DATA, jiraIssueRepository.findIssueAndDescByNumber(storyIdList));
		return resultListMap;
	}
}
