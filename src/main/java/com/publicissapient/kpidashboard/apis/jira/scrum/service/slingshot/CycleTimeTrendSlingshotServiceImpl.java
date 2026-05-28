package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot;

import static com.publicissapient.kpidashboard.common.constant.CommonConstant.HIERARCHY_LEVEL_ID_PROJECT;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.enums.JiraFeatureHistory;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.service.JiraKPIService;
import com.publicissapient.kpidashboard.apis.jira.service.SprintDetailsService;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.BacklogKpiHelper;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.dto.CycleTimeGroup;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CycleTimeTrendSlingshotServiceImpl
		extends JiraKPIService<Long, List<Object>, Map<String, Object>> {

	private static final String HISTORY = "history";
	public static final String TREND_SLINGSHOT_DURATION_RANGE_SERVICE_IMPL =
			"cycleTimeTrendSlingshotDurationRangeServiceImpl";
	public static final String TREND_SLINGSHOT_SPRINTS_SERVICE_IMPL =
			"cycleTimeTrendSlingshotSprintsServiceImpl";
	private List<String> sprintIdList = Collections.synchronizedList(new ArrayList<>());

	@Autowired ConfigHelperService configHelperService;

	@Autowired CustomApiConfig customApiConfig;

	@Autowired JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;

	@Autowired SprintDetailsService sprintDetailsService;

	@Autowired KpiHelperService kpiHelperService;

	private final Map<String, CycleTimeTrendSlingshotStrategy> strategyMap;

	public CycleTimeTrendSlingshotServiceImpl(
			Map<String, CycleTimeTrendSlingshotStrategy> strategyMap) {
		this.strategyMap = strategyMap;
	}

	@Override
	public String getQualifierType() {
		return KPICode.CYCLE_TIME_TREND_SLINGSHOT.name();
	}

	@Override
	public KpiElement getKpiData(
			KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
			throws ApplicationException {
		log.info("CYCLE TIME SLINGSHOT -> requestTrackerId[{}]", kpiRequest.getRequestTrackerId());
		List<Node> projectList =
				treeAggregatorDetail.getMapOfListOfProjectNodes().get(HIERARCHY_LEVEL_ID_PROJECT);
		sprintIdList =
				treeAggregatorDetail.getMapOfListOfLeafNodes().get(CommonConstant.SPRINT_MASTER).stream()
						.map(node -> node.getSprintFilter().getId())
						.toList();
		// in case if only projects or sprint filters are applied
		if (CollectionUtils.isNotEmpty(projectList)) {
			String startDate = DateUtil.getTodayDate().minusMonths(6).toString();
			String endDate = DateUtil.getTodayDate().toString();
			Map<String, Object> resultMap =
					fetchKPIDataFromDb(projectList, startDate, endDate, kpiRequest);
			if (kpiElement.isKpiSprintSwitch()) {
				log.info(
						"CYCLE TIME SLINGSHOT SPRINT WISE -> requestTrackerId[{}]",
						kpiRequest.getRequestTrackerId());
				strategyMap
						.get(TREND_SLINGSHOT_SPRINTS_SERVICE_IMPL)
						.projectWiseLeafNodeValue(kpiElement, projectList.get(0), resultMap);
			} else {
				log.info(
						"CYCLE TIME SLINGSHOT DURATION RANGE WISE -> requestTrackerId[{}]",
						kpiRequest.getRequestTrackerId());
				strategyMap
						.get(TREND_SLINGSHOT_DURATION_RANGE_SERVICE_IMPL)
						.projectWiseLeafNodeValue(kpiElement, projectList.get(0), resultMap);
			}
		}

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new LinkedHashMap<>();
		calculateAggregatedMultipleValueGroupMap(
				projectList.get(0), nodeWiseKPIValue, KPICode.CYCLE_TIME_TREND_SLINGSHOT);
		Map<String, List<DataCount>> trendValuesMap =
				getTrendValuesMapUnSorted(
						kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.CYCLE_TIME_TREND_SLINGSHOT);

		Map<String, Map<String, List<DataCount>>> priorityTypeProjectWiseDc = new LinkedHashMap<>();
		trendValuesMap.forEach(
				(priority, dataCounts) -> {
					Map<String, List<DataCount>> projectWiseDc =
							dataCounts.stream().collect(Collectors.groupingBy(DataCount::getData));
					priorityTypeProjectWiseDc.put(priority, projectWiseDc);
				});

		List<DataCountGroup> dataCountGroups = new ArrayList<>();
		priorityTypeProjectWiseDc.forEach(
				(filter, projectWiseDc) -> {
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

	@Override
	public Long calculateKPIMetrics(Map<String, Object> stringObjectMap) {
		return 0L;
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
		Map<String, Object> resultListMap = new HashMap<>();
		leafNodeList.forEach(
				leafNode -> {
					ObjectId basicProjectConfigId = leafNode.getProjectFilter().getBasicProjectConfigId();
					Map<String, Map<String, Object>> uniqueProjectMap =
							getUniqueProjectMap(basicProjectConfigId);

					List<JiraIssueCustomHistory> jiraIssueCustomHistoryList =
							jiraIssueCustomHistoryRepository.findByBasicProjectConfigIdIn(
									basicProjectConfigId.toString());
					List<JiraIssueCustomHistory> filteredProjectHistory =
							BacklogKpiHelper.filterProjectHistories(
									jiraIssueCustomHistoryList, uniqueProjectMap, startDate, endDate);

					List<SprintDetails> sprintDetailsList =
							sprintDetailsService.getSprintDetailsByIds(sprintIdList);
					List<SprintDetails> limitedSprintList =
							sprintDetailsList.stream().skip(Math.max(0, sprintDetailsList.size() - 5)).toList();
					resultListMap.put(HISTORY, filteredProjectHistory);
					resultListMap.put("sprints", limitedSprintList);
				});
		return resultListMap;
	}

	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI202(), KPICode.CYCLE_TIME_TREND_SLINGSHOT.getKpiId());
	}

	private Map<String, Map<String, Object>> getUniqueProjectMap(ObjectId basicProjectConfigId) {
		Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();
		Map<String, Object> mapOfProjectFilters = new LinkedHashMap<>();

		FieldMapping fieldMapping = configHelperService.getFieldMappingMap().get(basicProjectConfigId);

		if (Optional.ofNullable(fieldMapping.getJiraIssueTypeKPI202()).isPresent()) {

			KpiDataHelper.prepareFieldMappingDefectTypeTransformation(
					mapOfProjectFilters,
					fieldMapping.getJiradefecttype(),
					fieldMapping.getJiraIssueTypeKPI202(),
					JiraFeatureHistory.STORY_TYPE.getFieldValueInFeature());
			uniqueProjectMap.put(basicProjectConfigId.toString(), mapOfProjectFilters);
		}

		List<CycleTimeGroup> issueTypesByGroups =
				fieldMapping.getJiraIssueStatusGroupByCategoryKPI202();

		List<String> status =
				new ArrayList<>(
						issueTypesByGroups.stream()
								.map(CycleTimeGroup::getStatuses)
								.flatMap(Collection::stream)
								.toList());
		mapOfProjectFilters.put(
				"statusUpdationLog.story.changedTo", CommonUtils.convertToPatternList(status));
		uniqueProjectMap.put(basicProjectConfigId.toString(), mapOfProjectFilters);
		return uniqueProjectMap;
	}
}
