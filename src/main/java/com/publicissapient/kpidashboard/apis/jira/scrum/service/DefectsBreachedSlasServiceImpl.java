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

package com.publicissapient.kpidashboard.apis.jira.scrum.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.codehaus.plexus.util.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.Filters;
import com.publicissapient.kpidashboard.apis.enums.JiraFeature;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.service.JiraKPIService;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.constant.NormalizedJira;
import com.publicissapient.kpidashboard.common.model.application.BaseFieldMappingStructure;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
public class DefectsBreachedSlasServiceImpl extends JiraKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String TOTAL_RESOLVED_ISSUES = "totalResolvedIssues";
	private static final String BREACHED_PERCENTAGE = "breachedPercentage";
	private static final String SEVERITY = "severity";

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS");

	private final MongoTemplate mongoTemplate;

	@Data
	@Builder
	@AllArgsConstructor
	@NoArgsConstructor
	private static class DefectsBreachedSLAsKPIData {
		private String basicProjectConfigId;
		private String projectName;

		private FieldMapping projectKPISettingsFieldMapping;

		private List<SprintDataForKPI195> sprintDataForKPI195List;

		private Set<String> severitiesFoundAcrossSprintIssues;
	}

	@Data
	@Builder
	private static class SprintDataForKPI195 {
		private String sprintId;
		private String sprintName;
		private String sprintNodeId;

		private List<JiraDefectIssueDataForKPI195> jiraDefectIssueDataForKPI195List;
	}

	@Data
	private static class SeverityJiraIssuesDefectsBreachedSLAData {
		private int solvedIssues;
		private int breachedIssues;
	}

	@Data
	@AllArgsConstructor
	private static class SeverityJiraDefectDrillDownValue {
		private String severity;

		private double breachedPercentage;
	}

	@Data
	@Builder
	private static class JiraDefectIssueDataForKPI195 {
		private String issueKey;
		private String url;
		private String priority;
		private String status;
		private String severity;
		private String timeSpentInHours;
		private String resolutionTime;

		private Set<String> issueIdsDefectRelatesTo;

		private SLAData slaData;

		private LocalDateTime createdDate;
		private LocalDateTime closedDate;
	}

	@Data
	@AllArgsConstructor
	static class SLAData {
		private double sla;

		private String generalSeverity;
		private String timeUnit;
	}

	@Override
	public String getQualifierType() {
		return KPICode.DEFECTS_BREACHED_SLAS.name();
	}

	@Override
	public Double calculateKpiValue(List<Double> valueList, String kpiName) {
		return calculateKpiValueForDouble(valueList, kpiName);
	}

	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement,
			TreeAggregatorDetail treeAggregatorDetail) throws ApplicationException {
		List<DataCount> trendValueList = new ArrayList<>();
		Node root = treeAggregatorDetail.getRoot();

		Map<String, Node> mapTmp = treeAggregatorDetail.getMapTmp();

		Map<String, List<Node>> userAccessibleDataByType = treeAggregatorDetail.getMapOfListOfLeafNodes();

		userAccessibleDataByType.keySet().stream().filter(dataType -> Filters.getFilter(dataType) == Filters.SPRINT)
				.findFirst().ifPresent(dataType -> calculateDefectsBreachSLAsForSprints(mapTmp,
						userAccessibleDataByType.get(dataType), trendValueList, kpiElement, kpiRequest));

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValueMap(root, nodeWiseKPIValue, KPICode.DEFECTS_BREACHED_SLAS);
		Map<String, List<DataCount>> trendValuesMap = getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue,
				KPICode.DEFECTS_BREACHED_SLAS);

		trendValuesMap = KPIHelperUtil.sortTrendMapByKeyOrder(trendValuesMap,
				List.of(CommonConstant.OVERALL, Constant.DSE_1, Constant.DSE_2, Constant.DSE_3, Constant.DSE_4));

		Map<String, Map<String, List<DataCount>>> severityTypeProjectWiseDataCount = new LinkedHashMap<>();
		trendValuesMap.forEach((String issueType, List<DataCount> dataCounts) -> {
			Map<String, List<DataCount>> projectWiseDataCount = dataCounts.stream()
					.collect(Collectors.groupingBy(DataCount::getData));
			severityTypeProjectWiseDataCount.put(issueType, projectWiseDataCount);
		});

		List<DataCountGroup> dataCountGroups = new ArrayList<>();
		severityTypeProjectWiseDataCount
				.forEach((String issueType, Map<String, List<DataCount>> projectWiseDataCount) -> {
					DataCountGroup dataCountGroup = new DataCountGroup();
					List<DataCount> dataList = new ArrayList<>();
					projectWiseDataCount.forEach((key, value) -> dataList.addAll(value));
					dataCountGroup.setFilter(issueType);
					dataCountGroup.setValue(dataList);
					dataCountGroups.add(dataCountGroup);
				});

		kpiElement.setTrendValueList(dataCountGroups);

		return kpiElement;
	}

	@Override
	public Map<String, Object> calculateHoverMap(List<Map<String, Object>> hoverMapValues) {
		Map<String, Object> aggregatedHoverMapValues = new HashMap<>();
		if (CollectionUtils.isNotEmpty(hoverMapValues)) {
			int totalResolvedIssuesSum = 0;
			int breachedPercentagesCount = 0;
			double breachedPercentagesSum = 0.0;

			for (Map<String, Object> hoverMapValue : hoverMapValues) {
				if (MapUtils.isNotEmpty(hoverMapValue)) {
					totalResolvedIssuesSum += (int) hoverMapValue.get(TOTAL_RESOLVED_ISSUES);
					breachedPercentagesSum += (double) hoverMapValue.get(BREACHED_PERCENTAGE);
					breachedPercentagesCount++;
				}
			}
			aggregatedHoverMapValues.put(TOTAL_RESOLVED_ISSUES, totalResolvedIssuesSum);
			if (breachedPercentagesCount == 0) {
				aggregatedHoverMapValues.put(BREACHED_PERCENTAGE, 0.0D);
			} else {
				aggregatedHoverMapValues.put(BREACHED_PERCENTAGE,
						Math.floor(breachedPercentagesSum / breachedPercentagesCount));
			}
		}
		return aggregatedHoverMapValues;
	}

	@Override
	@SuppressWarnings("java:S3776")
	public Object calculateDrillDownValue(List<Object> drillDownValues) {
		List<SeverityJiraDefectDrillDownValue> aggregatedSeverityJiraDefectDrillDownValueList = new ArrayList<>();
		if (CollectionUtils.isNotEmpty(drillDownValues)) {
			Map<String, List<Double>> severityIssuesBreachedPercentagesAcrossAllProjects = new HashMap<>();
			for (Object projectOverallDrillDownValue : drillDownValues) {
				if (Objects.nonNull(projectOverallDrillDownValue)) {
					for (SeverityJiraDefectDrillDownValue severityJiraDefectDrillDownValue : (List<SeverityJiraDefectDrillDownValue>) projectOverallDrillDownValue) {
						severityIssuesBreachedPercentagesAcrossAllProjects.computeIfAbsent(
								severityJiraDefectDrillDownValue.getSeverity(), k -> new ArrayList<>());
						severityIssuesBreachedPercentagesAcrossAllProjects
								.get(severityJiraDefectDrillDownValue.getSeverity())
								.add(severityJiraDefectDrillDownValue.getBreachedPercentage());
					}
				}
			}
			if (MapUtils.isNotEmpty(severityIssuesBreachedPercentagesAcrossAllProjects)) {
				severityIssuesBreachedPercentagesAcrossAllProjects.keySet().forEach((String severity) -> {
					double averageBreachedPercentage = 0.0D;
					if (severityIssuesBreachedPercentagesAcrossAllProjects.get(severity).isEmpty()) {
						aggregatedSeverityJiraDefectDrillDownValueList
								.add(new SeverityJiraDefectDrillDownValue(severity, averageBreachedPercentage));
					} else {
						double breachedPercentageSum = severityIssuesBreachedPercentagesAcrossAllProjects.get(severity)
								.stream().mapToDouble(Double::doubleValue).sum();
						int breachedPercentagesCount = severityIssuesBreachedPercentagesAcrossAllProjects.get(severity)
								.size();
						aggregatedSeverityJiraDefectDrillDownValueList.add(new SeverityJiraDefectDrillDownValue(
								severity, Math.floor(breachedPercentageSum / breachedPercentagesCount)));
					}
				});
			}
		}
		return aggregatedSeverityJiraDefectDrillDownValueList;
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(List<Node> leafNodeList, String startDate, String endDate,
			KpiRequest kpiRequest) {
		// intentionally left without implementation
		return Collections.emptyMap();
	}

	@Override
	public Double calculateKPIMetrics(Map<String, Object> stringObjectMap) {
		// intentionally left without implementation
		return 0.0D;
	}

	@SuppressWarnings("java:S3776")
	private List<DefectsBreachedSLAsKPIData> constructKPIData(List<Node> sprintLeafNodesList) {
		List<DefectsBreachedSLAsKPIData> defectsBreachedSLAsKPIDataList = constructProjectsAndSprintsDataFromSprintLeafNodeList(
				sprintLeafNodesList);

		defectsBreachedSLAsKPIDataList.forEach((DefectsBreachedSLAsKPIData defectsBreachedSLAsKPIData) -> {
			FieldMapping fieldMapping = defectsBreachedSLAsKPIData.getProjectKPISettingsFieldMapping();
			if (projectDoesNotContainRequiredKPISettings(fieldMapping)) {
				return;
			}
			Query jiraIssuesQuery = constructJiraIssuesQueryApplyingAllKPISettingFilters(defectsBreachedSLAsKPIData,
					fieldMapping);
			if (Objects.nonNull(jiraIssuesQuery)) {
				List<JiraIssue> jiraIssuesAfterApplyingAllFilters = mongoTemplate.find(jiraIssuesQuery,
						JiraIssue.class);

				if (CollectionUtils.isNotEmpty(jiraIssuesAfterApplyingAllFilters)) {
					Query jiraIssueCustomHistoryQuery = constructJiraIssueCustomHistoryQueryFromBasicProjectConfigIdAndFilteredJiraIssues(
							defectsBreachedSLAsKPIData.getBasicProjectConfigId(), jiraIssuesAfterApplyingAllFilters);
					if (Objects.nonNull(jiraIssueCustomHistoryQuery)) {
						List<JiraIssueCustomHistory> jiraIssueCustomHistoryList = mongoTemplate
								.find(jiraIssueCustomHistoryQuery, JiraIssueCustomHistory.class);

						if (CollectionUtils.isNotEmpty(jiraIssueCustomHistoryList)) {
							populateSprintJiraDefectsDataForProject(defectsBreachedSLAsKPIData,
									jiraIssueCustomHistoryList, jiraIssuesAfterApplyingAllFilters);
						}
					}
				}
			}
		});
		return defectsBreachedSLAsKPIDataList;
	}

	private void calculateDefectsBreachSLAsForSprints(Map<String, Node> nodeIdMap, List<Node> sprintLeafNodes,
			List<DataCount> trendValueList, KpiElement kpiElement, KpiRequest kpiRequest) {
		List<DefectsBreachedSLAsKPIData> kpiDataFromDatabase = constructKPIData(sprintLeafNodes);

		if (CollectionUtils.isNotEmpty(kpiDataFromDatabase)) {
			List<KPIExcelData> kpiExcelDataList = new ArrayList<>();
			Set<String> severitiesFoundAcrossAllProjects = new HashSet<>();

			kpiDataFromDatabase.forEach((DefectsBreachedSLAsKPIData defectsBreachedSLAsKPIData) -> {
				if (CollectionUtils.isNotEmpty(defectsBreachedSLAsKPIData.getSeveritiesFoundAcrossSprintIssues())) {
					severitiesFoundAcrossAllProjects
							.addAll(defectsBreachedSLAsKPIData.getSeveritiesFoundAcrossSprintIssues());
				}
			});

			if (CollectionUtils.isNotEmpty(severitiesFoundAcrossAllProjects)) {
				kpiDataFromDatabase.forEach((DefectsBreachedSLAsKPIData defectsBreachedSLAsKPIData) -> {
					List<SprintDataForKPI195> sprintDataForKPI195List = defectsBreachedSLAsKPIData
							.getSprintDataForKPI195List();
					sprintDataForKPI195List
							.forEach(sprintDataForKPI195 -> populateDataCountWithKPICalculatedValueForASprint(
									defectsBreachedSLAsKPIData, nodeIdMap, severitiesFoundAcrossAllProjects,
									sprintDataForKPI195, trendValueList, kpiExcelDataList, kpiRequest));
				});
				kpiElement.setExcelData(kpiExcelDataList);
				kpiElement.setExcelColumns(KPIExcelColumn.DEFECTS_BREACHED_SLAS.getColumns());
			}
		}
	}

	private Map<String, SeverityJiraIssuesDefectsBreachedSLAData> constructSeverityByJiraIssueDefectsDataMap(
			SprintDataForKPI195 sprintDataForKPI195, KpiRequest kpiRequest, List<KPIExcelData> kpiExcelDataList) {
		Map<String, SeverityJiraIssuesDefectsBreachedSLAData> severityByJiraIssueDefectsData = new HashMap<>();
		sprintDataForKPI195.getJiraDefectIssueDataForKPI195List()
				.forEach((JiraDefectIssueDataForKPI195 jiraDefectIssueDataForKPI195) -> {
					severityByJiraIssueDefectsData.putIfAbsent(
							jiraDefectIssueDataForKPI195.getSlaData().getGeneralSeverity(),
							new SeverityJiraIssuesDefectsBreachedSLAData());
					SeverityJiraIssuesDefectsBreachedSLAData severityJiraIssuesDefectsBreachedSLAData = severityByJiraIssueDefectsData
							.get(jiraDefectIssueDataForKPI195.getSlaData().getGeneralSeverity());
					severityJiraIssuesDefectsBreachedSLAData
							.setSolvedIssues(severityJiraIssuesDefectsBreachedSLAData.getSolvedIssues() + 1);
					boolean defectBreachedSLA = defectBreachedSLA(jiraDefectIssueDataForKPI195);
					if (defectBreachedSLA) {
						severityJiraIssuesDefectsBreachedSLAData
								.setBreachedIssues(severityJiraIssuesDefectsBreachedSLAData.getBreachedIssues() + 1);
					}
					if (kpiRequest.getRequestTrackerId().toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
						String breachedSlaFlagString = defectBreachedSLA ? "Y" : "N";
						populateKPIExcelDataByJiraDefectIssueAndBreachedSlaFlag(kpiExcelDataList,
								sprintDataForKPI195.getSprintName(), jiraDefectIssueDataForKPI195,
								breachedSlaFlagString);
					}
				});
		return severityByJiraIssueDefectsData;
	}

	private void populateDataCountWithKPICalculatedValueForASprint(
			DefectsBreachedSLAsKPIData defectsBreachedSLAsKPIData, Map<String, Node> nodeIdMap,
			Set<String> severitiesFoundAcrossAllProjects, SprintDataForKPI195 sprintDataForKPI195,
			List<DataCount> trendValueList, List<KPIExcelData> kpiExcelDataList, KpiRequest kpiRequest) {
		if (CollectionUtils.isEmpty(defectsBreachedSLAsKPIData.getSeveritiesFoundAcrossSprintIssues())) {
			return;
		}
		Map<String, List<DataCount>> severityDataCountMap = new HashMap<>();
		Map<String, SeverityJiraIssuesDefectsBreachedSLAData> severityByJiraIssueDefectsData = constructSeverityByJiraIssueDefectsDataMap(
				sprintDataForKPI195, kpiRequest, kpiExcelDataList);

		List<SeverityJiraDefectDrillDownValue> severityJiraDefectDrillDownValueList = new ArrayList<>();

		int totalResolvedDefects = 0;
		int totalBreachedDefects = 0;

		for (String severity : severitiesFoundAcrossAllProjects) {
			String capitalisedSeverity = StringUtils.capitalise(severity);
			DataCount.DataCountBuilder dataCountBuilder = DataCount.builder()
					.sProjectName(defectsBreachedSLAsKPIData.getProjectName())
					.sSprintID(sprintDataForKPI195.getSprintId()).sSprintName(sprintDataForKPI195.getSprintName())
					.kpiGroup(capitalisedSeverity);

			double defectsBreachedSLAForSeverity = 0.0D;
			Map<String, Object> hoverValueMap = new HashMap<>();
			if (severityByJiraIssueDefectsData.containsKey(capitalisedSeverity.toLowerCase())) {
				SeverityJiraIssuesDefectsBreachedSLAData severityJiraIssuesDefectsBreachedSLAData = severityByJiraIssueDefectsData
						.get(severity);

				if (severityJiraIssuesDefectsBreachedSLAData.solvedIssues > 0) {
					defectsBreachedSLAForSeverity = Math.floor((severityJiraIssuesDefectsBreachedSLAData.breachedIssues
							/ (double) severityJiraIssuesDefectsBreachedSLAData.solvedIssues) * 100);
				}
				severityJiraDefectDrillDownValueList
						.add(new SeverityJiraDefectDrillDownValue(severity, defectsBreachedSLAForSeverity));
				totalResolvedDefects += severityJiraIssuesDefectsBreachedSLAData.solvedIssues;
				totalBreachedDefects += severityJiraIssuesDefectsBreachedSLAData.breachedIssues;

				hoverValueMap.put(TOTAL_RESOLVED_ISSUES, severityJiraIssuesDefectsBreachedSLAData.solvedIssues);
				hoverValueMap.put(BREACHED_PERCENTAGE, defectsBreachedSLAForSeverity);
			}
			dataCountBuilder.data(String.valueOf(defectsBreachedSLAForSeverity)).hoverValue(hoverValueMap)
					.value(defectsBreachedSLAForSeverity);
			severityDataCountMap.computeIfAbsent(capitalisedSeverity, k -> new ArrayList<>());
			severityDataCountMap.get(capitalisedSeverity).add(dataCountBuilder.build());
			trendValueList.add(dataCountBuilder.build());
		}

		Map<String, Object> hoverValueMap = new HashMap<>();
		double defectsBreachedSLAForSprint = 0.0;

		if (totalResolvedDefects > 0) {
			defectsBreachedSLAForSprint = Math.floor((totalBreachedDefects / (double) totalResolvedDefects) * 100);
		}

		hoverValueMap.put(TOTAL_RESOLVED_ISSUES, totalBreachedDefects);
		hoverValueMap.put(BREACHED_PERCENTAGE, defectsBreachedSLAForSprint);

		String overallFilter = CommonConstant.OVERALL;

		DataCount dataCount = DataCount.builder().data(String.valueOf(defectsBreachedSLAForSprint))
				.sProjectName(defectsBreachedSLAsKPIData.getProjectName()).sSprintID(sprintDataForKPI195.getSprintId())
				.sSprintName(sprintDataForKPI195.getSprintName()).hoverValue(hoverValueMap)
				.value(defectsBreachedSLAForSprint).drillDown(severityJiraDefectDrillDownValueList)
				.kpiGroup(overallFilter).build();

		severityDataCountMap.computeIfAbsent(overallFilter, k -> new ArrayList<>());
		severityDataCountMap.get(overallFilter).add(dataCount);
		trendValueList.add(dataCount);
		nodeIdMap.get(sprintDataForKPI195.getSprintNodeId()).setValue(severityDataCountMap);
	}

	private List<DefectsBreachedSLAsKPIData> constructProjectsAndSprintsDataFromSprintLeafNodeList(
			List<Node> sprintLeafNodeList) {
		List<DefectsBreachedSLAsKPIData> defectsBreachedSLAsKPIDataList = new ArrayList<>();
		if (CollectionUtils.isEmpty(sprintLeafNodeList)) {
			return defectsBreachedSLAsKPIDataList;
		}
		// Sort the sprints by start date in ascending order
		sprintLeafNodeList.sort(Comparator.comparing(node -> node.getSprintFilter().getStartDate()));

		sprintLeafNodeList.forEach((Node sprintLeafNode) -> {
			String basicProjectConfigStringId = sprintLeafNode.getProjectFilter().getBasicProjectConfigId().toString();
			String sprintId = sprintLeafNode.getSprintFilter().getId();
			String sprintNodeId = sprintLeafNode.getId();

			Optional<DefectsBreachedSLAsKPIData> defectsBreachedSLAsKPIDataOptional = defectsBreachedSLAsKPIDataList
					.stream().filter(defectsBreachedSLAsKPIData -> basicProjectConfigStringId
							.equals(defectsBreachedSLAsKPIData.getBasicProjectConfigId()))
					.findFirst();

			if (defectsBreachedSLAsKPIDataOptional.isPresent()) {
				DefectsBreachedSLAsKPIData defectsBreachedSLAsKPIData = defectsBreachedSLAsKPIDataOptional.get();

				Optional<SprintDataForKPI195> sprintDataForKPI195Optional = defectsBreachedSLAsKPIData
						.getSprintDataForKPI195List().stream()
						.filter(sprintDataForKPI195 -> sprintId.equals(sprintDataForKPI195.getSprintId())).findFirst();

				if (sprintDataForKPI195Optional.isEmpty()) {
					defectsBreachedSLAsKPIData.getSprintDataForKPI195List()
							.add(SprintDataForKPI195.builder().sprintId(sprintId)
									.sprintName(sprintLeafNode.getSprintFilter().getName()).sprintNodeId(sprintNodeId)
									.jiraDefectIssueDataForKPI195List(new ArrayList<>()).build());
				}
			} else {
				DefectsBreachedSLAsKPIData.DefectsBreachedSLAsKPIDataBuilder defectsBreachedSLAsKPIDataBuilder = DefectsBreachedSLAsKPIData
						.builder().basicProjectConfigId(basicProjectConfigStringId)
						.projectName(sprintLeafNode.getProjectFilter().getName())
						.projectKPISettingsFieldMapping(getConfigHelperService().getFieldMappingMap()
								.get(sprintLeafNode.getProjectFilter().getBasicProjectConfigId()));

				List<SprintDataForKPI195> sprintDataForKPI195List = new ArrayList<>();
				sprintDataForKPI195List.add(SprintDataForKPI195.builder().sprintId(sprintId)
						.sprintName(sprintLeafNode.getSprintFilter().getName()).sprintNodeId(sprintNodeId)
						.jiraDefectIssueDataForKPI195List(new ArrayList<>()).build());
				defectsBreachedSLAsKPIDataBuilder.sprintDataForKPI195List(sprintDataForKPI195List);
				defectsBreachedSLAsKPIDataList.add(defectsBreachedSLAsKPIDataBuilder.build());
			}
		});
		return defectsBreachedSLAsKPIDataList;
	}

	private Query constructJiraIssuesQueryApplyingAllKPISettingFilters(
			DefectsBreachedSLAsKPIData defectsBreachedSLAsKPIData, FieldMapping fieldMapping) {
		if (Objects.isNull(defectsBreachedSLAsKPIData) || Objects.isNull(fieldMapping)) {
			return null;
		}
		String basicProjectConfigId = defectsBreachedSLAsKPIData.getBasicProjectConfigId();
		Set<String> sprintIdsSet = defectsBreachedSLAsKPIData.getSprintDataForKPI195List().stream()
				.map(SprintDataForKPI195::getSprintId).collect(Collectors.toSet());

		Map<String, List<String>> severityGeneralValuesMap = getCustomApiConfig().getSeverity();
		List<String> generalSeverityValuesBasedOnIncludedSeveritiesList = new ArrayList<>();
		fieldMapping.getIncludedSeveritySlasKPI195().forEach((BaseFieldMappingStructure.Options severitySlaOption) -> {
			Map<String, Object> severitySlaStructuredValue = (Map<String, Object>) severitySlaOption
					.getStructuredValue();
			String severityValueInLowerCase = ((String) severitySlaStructuredValue.get(SEVERITY)).toLowerCase();
			if (severityGeneralValuesMap.containsKey(severityValueInLowerCase)) {
				generalSeverityValuesBasedOnIncludedSeveritiesList
						.addAll(severityGeneralValuesMap.get(severityValueInLowerCase));
			}
		});

		Criteria criteria = new Criteria();
		criteria = criteria.and(JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature()).is(basicProjectConfigId)
				.and(JiraFeature.SPRINT_ID.getFieldValueInFeature()).in(sprintIdsSet)
				.and(JiraFeature.ISSUE_TYPE.getFieldValueInFeature()).is(NormalizedJira.DEFECT_TYPE.getValue())
				.and(JiraFeature.STATUS.getFieldValueInFeature())
				.in(CommonUtils.convertToPatternList(fieldMapping.getIncludedDefectClosureStatusesKPI195()))
				.and(SEVERITY).in(CommonUtils.convertToPatternList(generalSeverityValuesBasedOnIncludedSeveritiesList));

		if (CollectionUtils.isNotEmpty(fieldMapping.getExcludedDefectPrioritiesKPI195())) {
			Map<String, List<String>> priorityGeneralValuesMap = getCustomApiConfig().getPriority();
			List<String> generalPriorityValuesBasedOnExcludedPriorityList = new ArrayList<>();
			fieldMapping.getExcludedDefectPrioritiesKPI195().forEach((String priority) -> {
				if (priorityGeneralValuesMap.containsKey(priority.toLowerCase())) {
					generalPriorityValuesBasedOnExcludedPriorityList
							.addAll(priorityGeneralValuesMap.get(priority.toLowerCase()));
				}
			});
			criteria = criteria.and(JiraFeature.DEFECT_PRIORITY.getFieldValueInFeature())
					.nin(CommonUtils.convertToPatternList(generalPriorityValuesBasedOnExcludedPriorityList));
		}
		if (CollectionUtils.isNotEmpty(fieldMapping.getExcludedDefectResolutionTypesKPI195())) {
			criteria = criteria.and("resolution")
					.nin(CommonUtils.convertToPatternList(fieldMapping.getExcludedDefectResolutionTypesKPI195()));
		}
		if (CollectionUtils.isNotEmpty(fieldMapping.getIncludedDefectRootCausesKPI195())) {
			criteria = criteria.and("rootCauseList")
					.in(CommonUtils.convertToPatternList(fieldMapping.getIncludedDefectRootCausesKPI195()));
		}
		return new Query(criteria);
	}

	private static Query constructJiraIssueCustomHistoryQueryFromBasicProjectConfigIdAndFilteredJiraIssues(
			String basicProjectConfigId, List<JiraIssue> jiraIssues) {
		if (StringUtils.isEmpty(basicProjectConfigId) || CollectionUtils.isEmpty(jiraIssues)) {
			return null;
		}
		Criteria jiraIssueCustomHistoryCriteria = new Criteria();
		jiraIssueCustomHistoryCriteria = jiraIssueCustomHistoryCriteria
				.and(JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature()).is(basicProjectConfigId)
				.and("storyID").in(jiraIssues.stream().map(JiraIssue::getNumber).collect(Collectors.toSet()));
		return new Query(jiraIssueCustomHistoryCriteria);
	}

	private void populateSprintJiraDefectsDataForProject(DefectsBreachedSLAsKPIData defectsBreachedSLAsKPIData,
			List<JiraIssueCustomHistory> jiraIssueCustomHistoryList,
			List<JiraIssue> jiraIssuesAfterApplyingAllKPISettingFilters) {
		Set<String> severitiesFoundAcrossProject = new HashSet<>();
		jiraIssueCustomHistoryList.forEach((JiraIssueCustomHistory jiraIssueCustomHistory) -> {
			List<JiraHistoryChangeLog> statusUpdateChangelog = jiraIssueCustomHistory.getStatusUpdationLog();
			statusUpdateChangelog.sort(Comparator.comparing(JiraHistoryChangeLog::getUpdatedOn));
			JiraHistoryChangeLog lastStatusUpdate = statusUpdateChangelog.get(statusUpdateChangelog.size() - 1);

			if (defectsBreachedSLAsKPIData.getProjectKPISettingsFieldMapping().getIncludedDefectClosureStatusesKPI195()
					.stream().anyMatch(status -> status.equalsIgnoreCase(lastStatusUpdate.getChangedTo()))) {
				Optional<JiraIssue> jiraIssueOptional = jiraIssuesAfterApplyingAllKPISettingFilters.stream().filter(
						jiraIssue -> jiraIssue.getNumber().equalsIgnoreCase(jiraIssueCustomHistory.getStoryID()))
						.findFirst();
				if (jiraIssueOptional.isPresent()) {
					JiraIssue jiraIssue = jiraIssueOptional.get();
					JiraDefectIssueDataForKPI195.JiraDefectIssueDataForKPI195Builder jiraDefectIssueDataForKPI195Builder = JiraDefectIssueDataForKPI195
							.builder().issueKey(jiraIssueCustomHistory.getStoryID())
							.closedDate(lastStatusUpdate.getUpdatedOn())
							.issueIdsDefectRelatesTo(jiraIssue.getDefectStoryID())
							.timeSpentInHours(String.valueOf(Math.floor(jiraIssue.getTimeSpentInMinutes() / 60.0)));

					LocalDateTime dateTime = LocalDateTime.parse(jiraIssue.getCreatedDate(), FORMATTER);
					jiraDefectIssueDataForKPI195Builder.createdDate(dateTime).url(jiraIssue.getUrl())
							.priority(jiraIssue.getPriority()).status(jiraIssue.getStatus());

					String issueSeverity = jiraIssue.getSeverity();

					SLAData slaData = determineDefectSLABasedOnSeverity(issueSeverity, defectsBreachedSLAsKPIData
							.getProjectKPISettingsFieldMapping().getIncludedSeveritySlasKPI195());

					if (Objects.nonNull(slaData)) {
						severitiesFoundAcrossProject.add(slaData.getGeneralSeverity());

						jiraDefectIssueDataForKPI195Builder.severity(issueSeverity).slaData(slaData);

						Optional<SprintDataForKPI195> sprintDataForKPI195Optional = defectsBreachedSLAsKPIData
								.getSprintDataForKPI195List().stream().filter(sprintDataForKPI195 -> sprintDataForKPI195
										.getSprintId().equals(jiraIssue.getSprintID()))
								.findFirst();
						sprintDataForKPI195Optional.ifPresent(
								sprintDataForKPI195 -> sprintDataForKPI195.getJiraDefectIssueDataForKPI195List()
										.add(jiraDefectIssueDataForKPI195Builder.build()));
					}
				}
			}
		});
		defectsBreachedSLAsKPIData.setSeveritiesFoundAcrossSprintIssues(severitiesFoundAcrossProject);
	}

	private static boolean projectDoesNotContainRequiredKPISettings(FieldMapping fieldMapping) {
		return Objects.isNull(fieldMapping)
				|| CollectionUtils.isEmpty(fieldMapping.getIncludedDefectClosureStatusesKPI195())
				|| CollectionUtils.isEmpty(fieldMapping.getIncludedSeveritySlasKPI195());
	}

	private static boolean defectBreachedSLA(JiraDefectIssueDataForKPI195 jiraDefectIssueDataForKPI195) {
		Duration durationToCloseTheDefect = calculateResolutionTimeForDefect(jiraDefectIssueDataForKPI195);

		SLAData slaData = jiraDefectIssueDataForKPI195.getSlaData();
		long slaInMillis;

		if ("Hours".equalsIgnoreCase(slaData.getTimeUnit())) {
			slaInMillis = (long) (slaData.getSla() * TimeUnit.HOURS.toMillis(1));
			return durationToCloseTheDefect.toMillis() > slaInMillis;
		}
		if ("Days".equalsIgnoreCase(slaData.getTimeUnit())) {
			slaInMillis = (long) (slaData.getSla() * TimeUnit.DAYS.toMillis(1));
			return durationToCloseTheDefect.toMillis() > slaInMillis;
		}

		return false;
	}

	private static Duration calculateResolutionTimeForDefect(
			JiraDefectIssueDataForKPI195 jiraDefectIssueDataForKPI195) {
		LocalDateTime createdDate = jiraDefectIssueDataForKPI195.getCreatedDate();
		LocalDateTime closedDate = jiraDefectIssueDataForKPI195.getClosedDate();

		return Duration.between(createdDate, closedDate);
	}

	private SLAData determineDefectSLABasedOnSeverity(String jiraIssueSeverity,
			List<BaseFieldMappingStructure.Options> includedSeveritySlasKPI195) {
		Map<String, List<String>> severitiesMap = getCustomApiConfig().getSeverity();

		Optional<String> severityOptional = severitiesMap.keySet().stream()
				.filter(severity -> severitiesMap.get(severity).stream()
						.anyMatch(severityMapping -> severityMapping.equalsIgnoreCase(jiraIssueSeverity)))
				.findFirst();
		return severityOptional.flatMap(severity -> includedSeveritySlasKPI195.stream()
				.filter((BaseFieldMappingStructure.Options severitySlaSetByUser) -> {
					Object severitySlaStructuredValue = severitySlaSetByUser.getStructuredValue();
					if (Objects.nonNull(severitySlaStructuredValue)) {
						Map<String, Object> severitySlaStructuredValueMap = (Map<String, Object>) severitySlaStructuredValue;
						return ((String) severitySlaStructuredValueMap.get(SEVERITY)).equalsIgnoreCase(severity);
					}
					return false;
				}).map((BaseFieldMappingStructure.Options severitySlaSetByUser) -> {
					Map<String, Object> severitySlaStructuredValueMap = (Map<String, Object>) severitySlaSetByUser
							.getStructuredValue();

					return new SLAData(((Number) severitySlaStructuredValueMap.get("sla")).doubleValue(),
							(String) severitySlaStructuredValueMap.get(SEVERITY),
							(String) severitySlaStructuredValueMap.get("timeUnit"));
				}).findFirst()).orElse(null);
	}

	private void populateKPIExcelDataByJiraDefectIssueAndBreachedSlaFlag(List<KPIExcelData> kpiExcelDataList,
			String sprintName, JiraDefectIssueDataForKPI195 jiraDefectIssueDataForKPI195,
			String breachedSlaFlagString) {
		Map<String, String> jiraIssueIdAndUrlDefectRelatesToMap = new HashMap<>();
		if (StringUtils.isNotEmpty(jiraDefectIssueDataForKPI195.getUrl())
				&& CollectionUtils.isNotEmpty(jiraDefectIssueDataForKPI195.getIssueIdsDefectRelatesTo())) {
			String jiraDefectUrl = jiraDefectIssueDataForKPI195.getUrl();
			jiraDefectIssueDataForKPI195.getIssueIdsDefectRelatesTo()
					.forEach(issueId -> jiraIssueIdAndUrlDefectRelatesToMap.put(issueId, String.format("%s%s",
							jiraDefectUrl.substring(0, jiraDefectUrl.lastIndexOf("/") + 1), issueId)));
		}
		KPIExcelData.KPIExcelDataBuilder kpiExcelDataBuilder = KPIExcelData.builder().sprintName(sprintName)
				.defectId(Map.of(jiraDefectIssueDataForKPI195.getIssueKey(), jiraDefectIssueDataForKPI195.getUrl()))
				.storyId(jiraIssueIdAndUrlDefectRelatesToMap).defectPriority(jiraDefectIssueDataForKPI195.getPriority())
				.defectSeverity(jiraDefectIssueDataForKPI195.getSeverity())
				.defectStatus(jiraDefectIssueDataForKPI195.getStatus())
				.totalTimeSpent(jiraDefectIssueDataForKPI195.getTimeSpentInHours())
				.defectSLA(String.format("%s %s", jiraDefectIssueDataForKPI195.getSlaData().getSla(),
						jiraDefectIssueDataForKPI195.getSlaData().getTimeUnit()))
				.slaBreached(breachedSlaFlagString);

		Duration resolutionTime = calculateResolutionTimeForDefect(jiraDefectIssueDataForKPI195);
		long totalSeconds = resolutionTime.getSeconds();
		long days = (totalSeconds / (24 * 3600));
		long hours = ((totalSeconds / (24 * 3600)) / 3600);
		long minutes = ((totalSeconds % 3600) / 60);

		kpiExcelDataBuilder.resolutionTime(String.format("%d days %d hours %d minutes", days, hours, minutes));
		kpiExcelDataList.add(kpiExcelDataBuilder.build());
	}
}