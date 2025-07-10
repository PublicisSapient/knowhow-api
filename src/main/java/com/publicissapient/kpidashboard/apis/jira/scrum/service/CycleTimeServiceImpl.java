package com.publicissapient.kpidashboard.apis.jira.scrum.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.jira.service.JiraKPIService;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.DataValue;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.JiraFeatureHistory;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.model.IterationKpiFilters;
import com.publicissapient.kpidashboard.apis.model.IterationKpiFiltersOptions;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.util.AggregationUtils;
import com.publicissapient.kpidashboard.apis.util.BacklogKpiHelper;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.CycleTime;
import com.publicissapient.kpidashboard.common.model.application.CycleTimeValidationData;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

import static com.publicissapient.kpidashboard.common.constant.CommonConstant.HIERARCHY_LEVEL_ID_PROJECT;

@Component
@Slf4j
public class CycleTimeServiceImpl extends JiraKPIService<Double, List<Object>, Map<String, Object>> {
	private static final String DAYS = "d";
	private static final String ISSUES = "issues";
	private static final String OVERALL = "#Overall";
	private static final String INTAKE_TO_DOR = "Intake - DOR";
	private static final String DOR_TO_DOD = "DOR - DOD";
	private static final String DOD_TO_LIVE = "DOD - Live";
	private static final String INTAKE_TO_DOR_KPI = "Intake to DOR";
	private static final String DOR_TO_DOD_KPI = "DOR to DOD";
	private static final String DOD_TO_LIVE_KPI = "DOD to Live";
	private static final String LEAD_TIME_KPI = "LEAD TIME";
	private static final String SEARCH_BY_ISSUE_TYPE = "Issue Type";
	private static final String SEARCH_BY_DURATION = "Duration";
	private static final Set<String> durationFilter = new LinkedHashSet<>(
			Arrays.asList("Past Week", "Past 2 Weeks", "Past Month", "Past 3 Months", "Past 6 Months"));
	@Autowired
	private JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;

	@Autowired
	private ConfigHelperService configHelperService;

	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement,
			TreeAggregatorDetail treeAggregatorDetail) throws ApplicationException {

		log.info("LEAD-TIME -> requestTrackerId[{}]", kpiRequest.getRequestTrackerId());
		List<Node> projectList = treeAggregatorDetail.getMapOfListOfProjectNodes().get(HIERARCHY_LEVEL_ID_PROJECT);
		Map<String, Node> mapTmp = treeAggregatorDetail.getMapTmp();

		// in case if only projects or sprint filters are applied
		projectWiseLeafNodeValue(kpiElement, mapTmp, projectList, kpiRequest);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedMultipleValueGroup(treeAggregatorDetail.getRoot(), nodeWiseKPIValue, KPICode.CYCLE_TIME);
		Map<String, List<DataCount>> trendValuesMap = getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue,
				KPICode.CYCLE_TIME);

		Map<String, Map<String, List<DataCount>>> priorityTypeProjectWiseDc = new LinkedHashMap<>();
		trendValuesMap.forEach((priority, dataCounts) -> {
			Map<String, List<DataCount>> projectWiseDc = dataCounts.stream()
					.collect(Collectors.groupingBy(DataCount::getData));
			priorityTypeProjectWiseDc.put(priority, projectWiseDc);
		});

		List<DataCountGroup> dataCountGroups = new ArrayList<>();
		priorityTypeProjectWiseDc.forEach((filter, projectWiseDc) -> {
			DataCountGroup dataCountGroup = new DataCountGroup();
			List<DataCount> dataList = new ArrayList<>();
			projectWiseDc.forEach((key, value) -> dataList.addAll(value));
			// split for filters
			String[] issueFilter = filter.split("#");
			dataCountGroup.setFilter1(issueFilter[0]);
			dataCountGroup.setFilter2(issueFilter[1]);
			dataCountGroup.setValue(dataList);
			dataCountGroups.add(dataCountGroup);
		});
		kpiElement.setTrendValueList(dataCountGroups);
		kpiElement.setNodeWiseKPIValue(nodeWiseKPIValue);

		return kpiElement;
	}

	@SuppressWarnings("unchecked")
	private void projectWiseLeafNodeValue(KpiElement kpiElement, Map<String, Node> mapTmp, List<Node> leafNodeList,
			KpiRequest kpiRequest) {
		KpiElement leadTimeReq = kpiRequest.getKpiList().stream().filter(k -> k.getKpiId().equalsIgnoreCase("kpi171"))
				.findFirst().orElse(new KpiElement());

		LinkedHashMap<String, Object> filterDuration = (LinkedHashMap<String, Object>) leadTimeReq.getFilterDuration();
		int value; // Default value for 'value'
		String duration; // Default value for 'duration'
		String startDate = null;
		String endDate = DateUtil.getTodayDate().toString();

		if (filterDuration != null) {
			value = (int) filterDuration.getOrDefault("value", 6);
			duration = (String) filterDuration.getOrDefault("duration", CommonConstant.MONTH);
		} else {
			value = 2;
			duration = CommonConstant.WEEK;
		}
		if (duration.equalsIgnoreCase(CommonConstant.WEEK)) {
			startDate = DateUtil.getTodayDate().minusWeeks(value).toString();
		} else if (duration.equalsIgnoreCase(CommonConstant.MONTH)) {
			startDate = DateUtil.getTodayDate().minusMonths(value).toString();
		}
		Map<String, Map<Integer, String>> durationLabels = new HashMap<>();
		durationLabels.put(CommonConstant.WEEK, Map.of(1, "Past Week", 2, "Past 2 Weeks"));
		durationLabels.put(CommonConstant.MONTH, Map.of(1, "Past Month", 3, "Past 3 Months", 6, "Past 6 Months"));

		String label = Optional.ofNullable(durationLabels.get(duration)).map(m -> m.get(value)).orElse("Unknown");
		Map<String, Object> resultMap = fetchKPIDataFromDb(leafNodeList, startDate, endDate, kpiRequest);
		List<CycleTimeValidationData> cycleTimeList = new ArrayList<>();
		List<KPIExcelData> excelData = new ArrayList<>();
		leafNodeList.forEach(leafNode -> {
			List<JiraIssueCustomHistory> issueCustomHistoryList = (List<JiraIssueCustomHistory>) resultMap
					.get(leafNode.getProjectFilter().getBasicProjectConfigId().toString());

			FieldMapping fieldMapping = configHelperService.getFieldMappingMap()
					.get(leafNode.getProjectFilter().getBasicProjectConfigId());
			Map<String, List<DataValue>> cycleTimeDataCount = getCycleTimeDataCount(issueCustomHistoryList,
					fieldMapping, cycleTimeList, kpiElement);
			Map<String, List<DataCount>> dataCountMap = getDataCountObject(leafNode.getProjectFilter().getName(),
					cycleTimeDataCount, label);
			mapTmp.get(leafNode.getId()).setValue(dataCountMap);
		});
		populateExcelDataObject(kpiRequest.getRequestTrackerId(), cycleTimeList, excelData);
		kpiElement.setExcelData(excelData);
		kpiElement.setModalHeads(KPIExcelColumn.CYCLE_TIME.getColumns());
	}

	@Override
	public Double calculateKPIMetrics(Map<String, Object> stringObjectMap) {
		return 0.0;
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(List<Node> leafNodeList, String startDate, String endDate,
			KpiRequest kpiRequest) {
		Map<String, Object> resultListMap = new HashMap<>();
		leafNodeList.forEach(leafNode -> {
			Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();

			ObjectId basicProjectConfigId = leafNode.getProjectFilter().getBasicProjectConfigId();
			Map<String, Object> mapOfProjectFilters = new LinkedHashMap<>();

			FieldMapping fieldMapping = configHelperService.getFieldMappingMap().get(basicProjectConfigId);

			if (Optional.ofNullable(fieldMapping.getJiraIssueTypeKPI171()).isPresent()) {

				KpiDataHelper.prepareFieldMappingDefectTypeTransformation(mapOfProjectFilters,
						fieldMapping.getJiradefecttype(), fieldMapping.getJiraIssueTypeKPI171(),
						JiraFeatureHistory.STORY_TYPE.getFieldValueInFeature());
				uniqueProjectMap.put(basicProjectConfigId.toString(), mapOfProjectFilters);
			}
			List<String> status = new ArrayList<>();
			if (Optional.ofNullable(fieldMapping.getJiraDodKPI171()).isPresent()) {
				status.addAll(fieldMapping.getJiraDodKPI171());
			}

			if (Optional.ofNullable(fieldMapping.getJiraDorKPI171()).isPresent()) {
				status.addAll(fieldMapping.getJiraDorKPI171());
			}

			if (Optional.ofNullable(fieldMapping.getJiraLiveStatusKPI171()).isPresent()) {
				status.addAll(fieldMapping.getJiraLiveStatusKPI171());
			}
			mapOfProjectFilters.put("statusUpdationLog.story.changedTo", CommonUtils.convertToPatternList(status));
			uniqueProjectMap.put(basicProjectConfigId.toString(), mapOfProjectFilters);

			List<JiraIssueCustomHistory> jiraIssueCustomHistoryList = jiraIssueCustomHistoryRepository
					.findByBasicProjectConfigIdIn(basicProjectConfigId.toString());
			List<JiraIssueCustomHistory> filteredProjectHistory = BacklogKpiHelper
					.filterProjectHistories(jiraIssueCustomHistoryList, uniqueProjectMap, startDate, endDate);
			resultListMap.put(basicProjectConfigId.toString(), filteredProjectHistory);
		});
		return resultListMap;
	}

	@Override
	public String getQualifierType() {
		return KPICode.CYCLE_TIME.name();
	}

	public Map<String, List<DataValue>> getCycleTimeDataCount(List<JiraIssueCustomHistory> jiraIssueCustomHistoriesList,
			FieldMapping fieldMapping, List<CycleTimeValidationData> cycleTimeList, KpiElement kpiElement) {

		Set<String> issueTypeFilter = new LinkedHashSet<>();
		Map<String, List<DataValue>> cycleMap = new LinkedHashMap<>();

		if (CollectionUtils.isNotEmpty(jiraIssueCustomHistoriesList)) {
			List<Long> overAllIntakeDorTime = new ArrayList<>();
			List<Long> overAllDorDodTime = new ArrayList<>();
			List<Long> overAllDodLiveTime = new ArrayList<>();
			List<Long> overAllLeadTimeList = new ArrayList<>();

			List<JiraIssueCustomHistory> overAllIntakeDorModalValues = new ArrayList<>();
			List<JiraIssueCustomHistory> overAllDorDodModalValues = new ArrayList<>();
			List<JiraIssueCustomHistory> overAllDodLiveModalValues = new ArrayList<>();
			List<JiraIssueCustomHistory> overAllLeadTimeModalValues = new ArrayList<>();

			Long overAllIntakeDor;
			Long overAllDorDod;
			Long overAllDodLive;

			Map<String, List<JiraIssueCustomHistory>> typeWiseIssues = jiraIssueCustomHistoriesList.stream()
					.collect(Collectors.groupingBy(JiraIssueCustomHistory::getStoryType));
			typeWiseIssues.forEach((type, jiraIssueCustomHistories) -> {
				List<Long> intakeDorTime = new ArrayList<>();
				List<Long> dorDodTime = new ArrayList<>();
				List<Long> dodLiveTime = new ArrayList<>();
				List<Long> leadTimeList = new ArrayList<>();

				Long intakeDor = 0L;
				Long dorDod = 0L;
				Long dodLive = 0L;

				List<JiraIssueCustomHistory> intakeDorModalValues = new ArrayList<>();
				List<JiraIssueCustomHistory> dorDodModalValues = new ArrayList<>();
				List<JiraIssueCustomHistory> dodLiveModalValues = new ArrayList<>();
				List<JiraIssueCustomHistory> leadTimeModalValues = new ArrayList<>();

				if (CollectionUtils.isNotEmpty(jiraIssueCustomHistories)) {
					// in below loop create list of day difference between Intake and
					// DOR. Here Intake is created date of issue.
					issueTypeFilter.add(type);
					for (JiraIssueCustomHistory jiraIssueCustomHistory : jiraIssueCustomHistories) {
						CycleTimeValidationData cycleTimeValidationData = new CycleTimeValidationData();
						cycleTimeValidationData.setIssueNumber(jiraIssueCustomHistory.getStoryID());
						cycleTimeValidationData.setUrl(jiraIssueCustomHistory.getUrl());
						cycleTimeValidationData.setIssueDesc(jiraIssueCustomHistory.getDescription());
						CycleTime cycleTime = new CycleTime();
						cycleTime.setIntakeTime(jiraIssueCustomHistory.getCreatedDate());
						cycleTimeValidationData.setIntakeDate(jiraIssueCustomHistory.getCreatedDate());
						Map<String, DateTime> dodStatusDateMap = new HashMap<>();
						List<String> liveStatus = Optional.ofNullable(fieldMapping.getJiraLiveStatusKPI171())
								.orElse(Collections.emptyList()).stream().filter(Objects::nonNull)
								.map(String::toLowerCase).collect(Collectors.toList());
						List<String> dodStatus = fieldMapping.getJiraDodKPI171().stream().filter(Objects::nonNull)
								.map(String::toLowerCase).collect(Collectors.toList());
						String storyFirstStatus = fieldMapping.getStoryFirstStatusKPI171();
						List<String> dor = fieldMapping.getJiraDorKPI171().stream().filter(Objects::nonNull)
								.map(String::toLowerCase).collect(Collectors.toList());

						jiraIssueCustomHistory.getStatusUpdationLog().forEach(statusUpdateLog -> {
							DateTime updateTime = DateTime.parse(statusUpdateLog.getUpdatedOn().toString());
							BacklogKpiHelper.setLiveTime(cycleTimeValidationData, cycleTime, statusUpdateLog,
									updateTime, liveStatus);
							BacklogKpiHelper.setReadyTime(cycleTimeValidationData, cycleTime, statusUpdateLog,
									updateTime, dor);
							BacklogKpiHelper.setDODTime(statusUpdateLog, updateTime, dodStatus, storyFirstStatus,
									dodStatusDateMap);
						});
						DateTime minUpdatedOn = CollectionUtils.isNotEmpty(dodStatusDateMap.values())
								? Collections.min(dodStatusDateMap.values())
								: null;
						cycleTime.setDeliveryTime(minUpdatedOn);
						cycleTime.setDeliveryLocalDateTime(DateUtil.convertDateTimeToLocalDateTime(minUpdatedOn));
						cycleTimeValidationData.setDodDate(minUpdatedOn);

						String intakeToReady = BacklogKpiHelper.setValueInCycleTime(cycleTime.getIntakeTime(),
								cycleTime.getReadyTime(), INTAKE_TO_DOR_KPI, cycleTimeValidationData, null);
						String readyToDeliver = BacklogKpiHelper.setValueInCycleTime(cycleTime.getReadyTime(),
								cycleTime.getDeliveryTime(), DOR_TO_DOD_KPI, cycleTimeValidationData, null);
						String deliverToLive = BacklogKpiHelper.setValueInCycleTime(cycleTime.getDeliveryTime(),
								cycleTime.getLiveTime(), DOD_TO_LIVE_KPI, cycleTimeValidationData, null);
						String leadTime = BacklogKpiHelper.setValueInCycleTime(cycleTime.getIntakeTime(),
								cycleTime.getLiveTime(), LEAD_TIME_KPI, cycleTimeValidationData, null);
						transitionExist(overAllIntakeDorTime, overAllIntakeDorModalValues, intakeDorTime,
								intakeDorModalValues, jiraIssueCustomHistory, intakeToReady);
						transitionExist(overAllDorDodTime, overAllDorDodModalValues, dorDodTime, dorDodModalValues,
								jiraIssueCustomHistory, readyToDeliver);
						transitionExist(overAllDodLiveTime, overAllDodLiveModalValues, dodLiveTime, dodLiveModalValues,
								jiraIssueCustomHistory, deliverToLive);
						transitionExist(overAllLeadTimeList, overAllLeadTimeModalValues, leadTimeList,
								leadTimeModalValues, jiraIssueCustomHistory, leadTime);

						cycleTimeList.add(cycleTimeValidationData);
					}

					intakeDor = AggregationUtils.averageLong(intakeDorTime);
					cycleMap.put(INTAKE_TO_DOR + "#" + type,
							Arrays.asList(getDataValue(ObjectUtils.defaultIfNull(intakeDor, 0L) / 480, DAYS),
									getDataValue(intakeDor, ISSUES)));
					dorDod = AggregationUtils.averageLong(dorDodTime);
					cycleMap.put(DOR_TO_DOD + "#" + type,
							Arrays.asList(getDataValue(ObjectUtils.defaultIfNull(dorDod, 0L) / 480, DAYS),
									getDataValue(dorDod, ISSUES)));
					dodLive = AggregationUtils.averageLong(dodLiveTime);
					cycleMap.put(DOD_TO_LIVE + "#" + type,
							Arrays.asList(getDataValue(ObjectUtils.defaultIfNull(dodLive, 0L) / 480, DAYS),
									getDataValue(dodLive, ISSUES)));

				}
			});
			overAllIntakeDor = ObjectUtils.defaultIfNull(AggregationUtils.averageLong(overAllIntakeDorTime), 0)
					.longValue();
			cycleMap.put(INTAKE_TO_DOR + OVERALL,
					Arrays.asList(getDataValue(ObjectUtils.defaultIfNull(overAllIntakeDor, 0L) / 480, DAYS),
							getDataValue(overAllIntakeDor, ISSUES)));
			overAllDorDod = ObjectUtils.defaultIfNull(AggregationUtils.averageLong(overAllDorDodTime), 0).longValue();
			cycleMap.put(DOR_TO_DOD + OVERALL,
					Arrays.asList(getDataValue(ObjectUtils.defaultIfNull(overAllDorDod, 0L) / 480, DAYS),
							getDataValue(overAllDorDod, ISSUES)));
			overAllDodLive = ObjectUtils.defaultIfNull(AggregationUtils.averageLong(overAllDodLiveTime), 0).longValue();
			cycleMap.put(DOD_TO_LIVE + OVERALL,
					Arrays.asList(getDataValue(ObjectUtils.defaultIfNull(overAllDodLive, 0L) / 480, DAYS),
							getDataValue(overAllDodLive, ISSUES)));
		}
		IterationKpiFiltersOptions filter1 = new IterationKpiFiltersOptions(SEARCH_BY_DURATION, durationFilter);
		IterationKpiFiltersOptions filter2 = new IterationKpiFiltersOptions(SEARCH_BY_ISSUE_TYPE, issueTypeFilter);
		IterationKpiFilters iterationKpiFilters = new IterationKpiFilters(filter1, filter2);
		// Modal Heads Options
		kpiElement.setFilters(iterationKpiFilters);

		return cycleMap;

	}

	private Map<String, List<DataCount>> getDataCountObject(String trendLineName, Map<String, List<DataValue>> cycleMap,
			String duration) {
		Map<String, List<DataCount>> dataCountMap = new HashMap<>();
		cycleMap.forEach((key, value) -> {
			String[] issueFilter = key.split("#");
			String filterKey = duration + "#" + issueFilter[1];
			DataCount dataCount = new DataCount();
			dataCount.setSProjectName(trendLineName);
			dataCount.setDataValue(value);
			dataCount.setKpiGroup(filterKey);
			dataCount.setSubFilter(issueFilter[0]);
			dataCount.setHoverValue(new HashMap<>());
			dataCountMap.computeIfAbsent(filterKey, k -> new ArrayList<>()).add(dataCount);
		});
		return dataCountMap;
	}

	private DataValue getDataValue(Long overAllCount, String subFilter) {
		DataValue dataValue = new DataValue();
		dataValue.setData(String.valueOf(overAllCount));
		dataValue.setValue(overAllCount);
		dataValue.setName(subFilter);
		return dataValue;

	}

	private void populateExcelDataObject(String requestTrackerId, List<CycleTimeValidationData> cycleTimeList,
			List<KPIExcelData> excelData) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			KPIExcelUtility.populateCycleTime(cycleTimeList, excelData);
		}
	}

	private void transitionExist(List<Long> overAllTimeList, List<JiraIssueCustomHistory> overAllTransitionModalValues,
			List<Long> filterTimeList, List<JiraIssueCustomHistory> transitionModalValues,
			JiraIssueCustomHistory jiraIssueCustomHistory, String transitionTime) {
		if (!transitionTime.equalsIgnoreCase(Constant.NOT_AVAILABLE)) {
			long time = KpiDataHelper.calculateTimeInDays(Long.parseLong(transitionTime));
			filterTimeList.add(time);
			overAllTimeList.add(time);
			transitionModalValues.add(jiraIssueCustomHistory);
			overAllTransitionModalValues.add(jiraIssueCustomHistory);
		}
	}
}
