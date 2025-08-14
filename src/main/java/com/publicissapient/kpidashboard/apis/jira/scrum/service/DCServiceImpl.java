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

package com.publicissapient.kpidashboard.apis.jira.scrum.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.publicissapient.kpidashboard.apis.common.service.KpiDataCacheService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiDataProvider;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.Filters;
import com.publicissapient.kpidashboard.apis.enums.JiraFeature;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.jira.service.JiraKPIService;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.constant.NormalizedJira;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.SprintWiseStory;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * This class fetches the defect count KPI along with trend analysis. Trend
 * analysis for Defect Count KPI has total defect count at y-axis and sprint id
 * at x-axis. {@link JiraKPIService}
 *
 * @author tauakram
 */
@Component
@Slf4j
public class DCServiceImpl extends JiraKPIService<Long, List<Object>, Map<String, Object>> {

	public static final String UNCHECKED = "unchecked";

	private static final String TOTAL_DEFECT_DATA = "totalBugKey";
	public static final String STORY_LIST = "storyList";
	private static final String SPRINT_WISE_STORY_DATA = "storyData";

	@Autowired
	private JiraIssueRepository jiraIssueRepository;

	@Autowired
	private ConfigHelperService configHelperService;

	@Autowired
	private CustomApiConfig customApiConfig;

	@Autowired
	private FilterHelperService flterHelperService;

	@Autowired
	private CacheService cacheService;

	@Autowired
	private KpiDataCacheService kpiDataCacheService;
	@Autowired
	private KpiDataProvider kpiDataProvider;
	@Autowired
	private FilterHelperService filterHelperService;

	private List<String> sprintIdList = Collections.synchronizedList(new ArrayList<>());

	private static final String SEPARATOR_ASTERISK = "*************************************";

	/** {@inheritDoc} */
	@Override
	public String getQualifierType() {
		return KPICode.DEFECT_COUNT_BY_PRIORITY.name();
	}

	/** {@inheritDoc} */
	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
			throws ApplicationException {

		List<DataCount> trendValueList = new ArrayList<>();
		Node root = treeAggregatorDetail.getRoot();
		Map<String, Node> mapTmp = treeAggregatorDetail.getMapTmp();
		sprintIdList = treeAggregatorDetail.getMapOfListOfLeafNodes().get(CommonConstant.SPRINT_MASTER).stream()
				.map(node -> node.getSprintFilter().getId()).collect(Collectors.toList());
		treeAggregatorDetail.getMapOfListOfLeafNodes().forEach((k, v) -> {
			if (Filters.getFilter(k) == Filters.SPRINT) {
				sprintWiseLeafNodeValue(mapTmp, v, trendValueList, kpiElement, kpiRequest);
			}
		});

		log.debug("[DC-LEAF-NODE-VALUE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(), root);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValueMap(root, nodeWiseKPIValue, KPICode.DEFECT_COUNT_BY_PRIORITY);
		Map<String, List<DataCount>> trendValuesMap = getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue,
				KPICode.DEFECT_COUNT_BY_PRIORITY);

		trendValuesMap = KPIHelperUtil.sortTrendMapByKeyOrder(trendValuesMap, priorityTypes(true));
		Map<String, Map<String, List<DataCount>>> issueTypeProjectWiseDc = new LinkedHashMap<>();
		trendValuesMap.forEach((issueType, dataCounts) -> {
			Map<String, List<DataCount>> projectWiseDc = dataCounts.stream()
					.collect(Collectors.groupingBy(DataCount::getData));
			issueTypeProjectWiseDc.put(issueType, projectWiseDc);
		});

		List<DataCountGroup> dataCountGroups = new ArrayList<>();
		issueTypeProjectWiseDc.forEach((issueType, projectWiseDc) -> {
			DataCountGroup dataCountGroup = new DataCountGroup();
			List<DataCount> dataList = new ArrayList<>();
			projectWiseDc.entrySet().stream().forEach(trend -> dataList.addAll(trend.getValue()));
			dataCountGroup.setFilter(issueType);
			dataCountGroup.setValue(dataList);
			dataCountGroups.add(dataCountGroup);
		});

		kpiElement.setTrendValueList(dataCountGroups);
		return kpiElement;
	}

	/** {@inheritDoc} */
	@Override
	public Long calculateKPIMetrics(Map<String, Object> objectMap) {
		return 0L;
	}

	/** {@inheritDoc} */
	@Override
	public Map<String, Object> fetchKPIDataFromDb(final List<Node> leafNodeList, final String startDate,
			final String endDate, final KpiRequest kpiRequest) {


		Map<ObjectId, List<String>> projectWiseSprints = new HashMap<>();
		leafNodeList.forEach(leaf -> {
			ObjectId basicProjectConfigId = leaf.getProjectFilter().getBasicProjectConfigId();
			String sprint = leaf.getSprintFilter().getId();
			projectWiseSprints.putIfAbsent(basicProjectConfigId, new ArrayList<>());
			projectWiseSprints.get(basicProjectConfigId).add(sprint);
		});
		List<SprintWiseStory> sprintWiseStoryList = new ArrayList<>();
		List<JiraIssue> jiraIssueList = new ArrayList<>();
		List<JiraIssue> defectListWoDrop = new ArrayList<>();
		boolean fetchCachedData = filterHelperService.isFilterSelectedTillSprintLevel(kpiRequest.getLevel(), false);
		projectWiseSprints.forEach((basicProjectConfigId, sprintList) -> {
			Map<String, Object> result;
			if (fetchCachedData) { // fetch data from cache only if Filter is selected till Sprint
				// level.
				result = kpiDataCacheService.fetchDCPData(kpiRequest, basicProjectConfigId, sprintIdList,
						KPICode.DEFECT_COUNT_BY_PRIORITY.getKpiId());
			} else { // fetch data from DB if filters below Sprint level (i.e. additional filters)
				result = kpiDataProvider.fetchDCPData(kpiRequest, basicProjectConfigId, sprintList);
			}
			jiraIssueList.addAll((List<JiraIssue>) result.getOrDefault(STORY_LIST, new ArrayList<>() ));
			sprintWiseStoryList.addAll((List<SprintWiseStory>) result
					.getOrDefault(SPRINT_WISE_STORY_DATA, new ArrayList<>()));
			defectListWoDrop.addAll((List<JiraIssue>) result.getOrDefault(TOTAL_DEFECT_DATA, new ArrayList<>()));
		});
		final Map<String, Object> resultListMap = new HashMap<>();
		resultListMap.put(SPRINT_WISE_STORY_DATA, sprintWiseStoryList);
		resultListMap.put(TOTAL_DEFECT_DATA, defectListWoDrop);
		resultListMap.put(STORY_LIST, jiraIssueList);
		return resultListMap;
	}


	/**
	 * This method populates KPI value to sprint leaf nodes. It also gives the trend
	 * analysis at sprint wise.
	 *
	 * @param mapTmp
	 *          node id map
	 * @param sprintLeafNodeList
	 *          sprint nodes list
	 * @param trendValueList
	 *          list to hold trend nodes data
	 * @param kpiElement
	 *          the KpiElement
	 * @param kpiRequest
	 *          the KpiRequest
	 */
	@SuppressWarnings(UNCHECKED)
	private void sprintWiseLeafNodeValue(Map<String, Node> mapTmp, List<Node> sprintLeafNodeList,
			List<DataCount> trendValueList, KpiElement kpiElement, KpiRequest kpiRequest) {

		String requestTrackerId = getRequestTrackerId();

		String startDate;
		String endDate;

		sprintLeafNodeList.sort(
				(node1, node2) -> node1.getSprintFilter().getStartDate().compareTo(node2.getSprintFilter().getStartDate()));

		startDate = sprintLeafNodeList.get(0).getSprintFilter().getStartDate();
		endDate = sprintLeafNodeList.get(sprintLeafNodeList.size() - 1).getSprintFilter().getEndDate();

		Map<String, Object> storyDefectDataListMap = fetchKPIDataFromDb(sprintLeafNodeList, startDate, endDate, kpiRequest);

		List<JiraIssue> storyList = (List<JiraIssue>) storyDefectDataListMap.get(STORY_LIST);
		List<SprintWiseStory> sprintWiseStoryList = (List<SprintWiseStory>) storyDefectDataListMap
				.get(SPRINT_WISE_STORY_DATA);

		Map<Pair<String, String>, List<SprintWiseStory>> sprintWiseMap = sprintWiseStoryList.stream().collect(
				Collectors.groupingBy(sws -> Pair.of(sws.getBasicProjectConfigId(), sws.getSprint()), Collectors.toList()));

		Map<Pair<String, String>, Map<String, Long>> sprintWiseDCPriorityMap = new HashMap<>();
		Map<Pair<String, String>, Integer> sprintWiseTDCMap = new HashMap<>();
		Map<Pair<String, String>, List<JiraIssue>> sprintWiseDefectDataListMap = new HashMap<>();

		List<KPIExcelData> excelData = new ArrayList<>();

		Set<String> projectWisePriorityList = new HashSet<>();
		sprintWiseMap.forEach((sprintFilter, sprintWiseStories) -> {
			List<String> storyIdList = new ArrayList<>();
			sprintWiseStories.stream().map(SprintWiseStory::getStoryList).collect(Collectors.toList())
					.forEach(storyIdList::addAll);

			List<JiraIssue> sprintWiseDefectDataList = ((List<JiraIssue>) storyDefectDataListMap.get(TOTAL_DEFECT_DATA))
					.stream().filter(f -> CollectionUtils.containsAny(f.getDefectStoryID(), storyIdList))
					.collect(Collectors.toList());
			// Below code is needed if defect->sprint linkage is considered

			Map<String, Long> priorityCountMap = KPIHelperUtil.setpriorityScrum(sprintWiseDefectDataList, customApiConfig);
			projectWisePriorityList.addAll(priorityCountMap.keySet());
			sprintWiseDefectDataListMap.put(sprintFilter, sprintWiseDefectDataList);

			setSprintWiseLogger(sprintFilter, storyIdList, sprintWiseDefectDataList, priorityCountMap);

			sprintWiseDCPriorityMap.put(sprintFilter, priorityCountMap);
			sprintWiseTDCMap.put(sprintFilter, sprintWiseDefectDataList.size());
		});

		sprintLeafNodeList.forEach(node -> {
			String trendLineName = node.getProjectFilter().getName();
			Pair<String, String> currentNodeIdentifier = Pair.of(node.getProjectFilter().getBasicProjectConfigId().toString(),
					node.getSprintFilter().getId());

			Map<String, List<DataCount>> dataCountMap = new HashMap<>();
			Map<String, Long> priorityMap = sprintWiseDCPriorityMap.getOrDefault(currentNodeIdentifier, new HashMap<>());
			Map<String, Long> finalMap = new HashMap<>();
			Map<String, Object> overAllHoverValueMap = new HashMap<>();
			if (CollectionUtils.isNotEmpty(projectWisePriorityList)) {
				projectWisePriorityList.forEach(priority -> {
					Long rcaWiseCount = priorityMap.getOrDefault(priority, 0L);
					finalMap.put(StringUtils.capitalize(priority), rcaWiseCount);
					overAllHoverValueMap.put(StringUtils.capitalize(priority), rcaWiseCount.intValue());
				});
				projectWisePriorityList.forEach(priority -> finalMap.computeIfAbsent(priority, val -> 0L));
				Long overAllCount = finalMap.values().stream().mapToLong(val -> val).sum();
				finalMap.put(CommonConstant.OVERALL, overAllCount);

				String finalTrendLineName = trendLineName;
				finalMap.forEach((priority, value) -> {
					DataCount dataCount = getDataCountObject(node, finalTrendLineName, overAllHoverValueMap, priority, value);
					trendValueList.add(dataCount);
					dataCountMap.computeIfAbsent(priority, k -> new ArrayList<>()).add(dataCount);
				});

				populateExcelDataObject(requestTrackerId, node.getSprintFilter().getName(), excelData,
						sprintWiseDefectDataListMap.get(currentNodeIdentifier), customApiConfig, storyList);
			}
			log.debug("[DC-SPRINT-WISE][{}]. DC for sprint {}  is {} and trend value is {}", requestTrackerId,
					node.getSprintFilter().getName(), sprintWiseDCPriorityMap.get(currentNodeIdentifier),
					sprintWiseTDCMap.get(currentNodeIdentifier));
			mapTmp.get(node.getId()).setValue(dataCountMap);
		});

		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(
				KPIExcelColumn.DEFECT_COUNT_BY_PRIORITY.getColumns(sprintLeafNodeList, cacheService, flterHelperService));
	}

	/**
	 * @param node
	 * @param trendLineName
	 * @param overAllHoverValueMap
	 * @param key
	 * @param value
	 * @return
	 */
	private DataCount getDataCountObject(Node node, String trendLineName, Map<String, Object> overAllHoverValueMap,
			String key, Long value) {
		DataCount dataCount = new DataCount();
		dataCount.setData(String.valueOf(value));
		dataCount.setSProjectName(trendLineName);
		dataCount.setSSprintID(node.getSprintFilter().getId());
		dataCount.setSSprintName(node.getSprintFilter().getName());
		dataCount.setValue(value);
		dataCount.setKpiGroup(key);
		Map<String, Object> hoverValueMap = new HashMap<>();
		if (key.equalsIgnoreCase(CommonConstant.OVERALL)) {
			dataCount.setHoverValue(overAllHoverValueMap);
		} else {
			hoverValueMap.put(key, value.intValue());
			dataCount.setHoverValue(hoverValueMap);
		}
		return dataCount;
	}

	private void populateExcelDataObject(String requestTrackerId, String sprintName, List<KPIExcelData> excelData,
			List<JiraIssue> sprintWiseDefectDataList, CustomApiConfig customApiConfig, List<JiraIssue> storyList) {

		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			KPIExcelUtility.populateDefectRelatedExcelData(sprintName, sprintWiseDefectDataList, excelData, customApiConfig,
					storyList);
		}
	}


	/**
	 * Sets logger to log sprint level details.
	 *
	 * @param sprint
	 *          unique key to identify sprint
	 * @param storyIdList
	 *          story id list
	 * @param sprintWiseDefectDataList
	 *          defects for current sprint
	 * @param priorityCountMap
	 *          priority map of defects
	 */
	private void setSprintWiseLogger(Pair<String, String> sprint, List<String> storyIdList,
			List<JiraIssue> sprintWiseDefectDataList, Map<String, Long> priorityCountMap) {

		if (customApiConfig.getApplicationDetailedLogger().equalsIgnoreCase("on")) {
			log.info(SEPARATOR_ASTERISK);
			log.info("************* SPRINT WISE DC *******************");
			log.info("Sprint: {}", sprint.getValue());
			log.info("Story[{}]: {}", storyIdList.size(), storyIdList);
			log.info("DefectDataList[{}]: {}", sprintWiseDefectDataList.size(),
					sprintWiseDefectDataList.stream().map(JiraIssue::getNumber).collect(Collectors.toList()));
			log.info("DefectPriorityCountMap: {}", priorityCountMap);
			log.info(SEPARATOR_ASTERISK);
			log.info(SEPARATOR_ASTERISK);
		}
	}

	@Override
	public Long calculateKpiValue(List<Long> valueList, String kpiName) {
		return calculateKpiValueForLong(valueList, kpiName);
	}

	private List<String> priorityTypes(boolean addOverall) {
		if (addOverall) {
			return Arrays.asList(CommonConstant.OVERALL, Constant.P1, Constant.P2, Constant.P3, Constant.P4, Constant.MISC);
		} else {
			return Arrays.asList(Constant.P1, Constant.P2, Constant.P3, Constant.P4, Constant.MISC);
		}
	}

	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(fieldMapping.getThresholdValueKPI28(), KPICode.DEFECT_COUNT_BY_PRIORITY.getKpiId());
	}
}
