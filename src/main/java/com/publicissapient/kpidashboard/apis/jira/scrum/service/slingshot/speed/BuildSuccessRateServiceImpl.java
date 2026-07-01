package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.speed;

import static com.publicissapient.kpidashboard.common.constant.CommonConstant.HIERARCHY_LEVEL_ID_PROJECT;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jenkins.service.JenkinsKPIService;
import com.publicissapient.kpidashboard.apis.model.BuildFrequencyInfo;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.BuildStatus;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.Build;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.repository.application.BuildRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuildSuccessRateServiceImpl
		extends JenkinsKPIService<Long, List<Object>, Map<String, List<Object>>> {

	private final BuildRepository buildRepository;

	/** {@inheritDoc} */
	@Override
	public String getQualifierType() {
		return KPICode.BUILD_SUCCESS_RATE.name();
	}

	@Override
	public KpiElement getKpiData(
			KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
			throws ApplicationException {
		Node projectNode =
				treeAggregatorDetail.getMapOfListOfProjectNodes().get(HIERARCHY_LEVEL_ID_PROJECT).get(0);
		kpiRequest.setXAxisDataPoints(12);
		kpiRequest.setDuration(CommonConstant.WEEK);
		Map<String, Node> nodeMap = Map.of(projectNode.getId(), projectNode);
		calculateProjectKpiTrendData(kpiElement, nodeMap, projectNode, kpiRequest);

		log.debug(
				"[PROJECT-WISE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				projectNode);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValueMap(projectNode, nodeWiseKPIValue, KPICode.BUILD_SUCCESS_RATE);

		Map<String, List<DataCount>> trendValuesMap =
				getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.BUILD_SUCCESS_RATE);

		kpiElement.setTrendValueList(
				DeveloperKpiHelper.prepareDataCountGroups(
						trendValuesMap, KPICode.BUILD_SUCCESS_RATE.getKpiId()));
		return kpiElement;
	}

	@Override
	public Long calculateKPIMetrics(Map<String, List<Object>> stringListMap) {
		return 0L;
	}

	/** {@inheritDoc} */
	@Override
	public Map<String, List<Object>> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
		List<Build> buildList =
				buildRepository.findBuildList(
						new HashMap<>(),
						Collections.singleton(leafNodeList.get(0).getProjectFilter().getBasicProjectConfigId()),
						startDate,
						endDate);

		Map<String, List<Object>> projectData = new HashMap<>();
		projectData.put(
				leafNodeList.get(0).getProjectFilter().getBasicProjectConfigId().toString(),
				new ArrayList<>(buildList));

		return projectData;
	}

	/** {@inheritDoc} */
	@Override
	public Long calculateKpiValue(List<Long> valueList, String kpiId) {
		return calculateKpiValueForLong(valueList, kpiId);
	}

	/** {@inheritDoc} */
	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI212(), KPICode.BUILD_SUCCESS_RATE.getKpiId());
	}

	/**
	 * Populates KPI value to project leaf nodes. It also gives the trend analysis project wise.
	 *
	 * @param kpiElement kpi element
	 * @param mapTmp node map
	 * @param projectLeafNode leaf node of project
	 * @param kpiRequest kpi request
	 */
	@SuppressWarnings("unchecked")
	private void calculateProjectKpiTrendData(
			KpiElement kpiElement,
			Map<String, Node> mapTmp,
			Node projectLeafNode,
			KpiRequest kpiRequest) {
		String requestTrackerId = getRequestTrackerId();

		Map<String, List<Object>> scmDataMap =
				fetchKPIDataFromDb(
						List.of(projectLeafNode),
						LocalDate.now().minusWeeks(12).toString(),
						LocalDate.now().toString(),
						kpiRequest);

		String projectId = projectLeafNode.getProjectFilter().getBasicProjectConfigId().toString();

		List<Build> allBuilds =
				scmDataMap.getOrDefault(projectId, Collections.emptyList()).stream()
						.map(Build.class::cast)
						.toList();

		List<KPIExcelData> excelData = new ArrayList<>();
		String trendLineName = projectLeafNode.getProjectFilter().getName();

		BuildFrequencyInfo buildFrequencyInfo = new BuildFrequencyInfo();

		Map<String, List<DataCount>> aggDataMap = new HashMap<>();
		List<Build> aggBuildList = new ArrayList<>();
		Map<String, List<Build>> buildMapJobWise =
				allBuilds.stream()
						.collect(
								Collectors.groupingBy(
										build -> build.getBuildJob() + "#" + build.getBuildBranch(),
										Collectors.toList()));
		for (Map.Entry<String, List<Build>> entry : buildMapJobWise.entrySet()) {
			List<Build> buildList = entry.getValue();
			aggBuildList.addAll(buildList);
			prepareInfoForBuild(buildFrequencyInfo, buildList, trendLineName, entry.getKey(), aggDataMap);
		}

		if (CollectionUtils.isEmpty(aggBuildList)) {
			mapTmp.get(projectLeafNode.getId()).setValue(null);
			return;
		}
		mapTmp.get(projectLeafNode.getId()).setValue(aggDataMap);
		populateValidationDataObject(requestTrackerId, excelData, trendLineName, buildFrequencyInfo);
		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(KPIExcelColumn.BUILD_FREQUENCY.getColumns());
	}

	private boolean checkDateIsInWeeks(LocalDate monday, LocalDate sunday, Build build) {
		LocalDate buildTime =
				Instant.ofEpochMilli(build.getStartTime()).atZone(ZoneId.systemDefault()).toLocalDate();
		return (buildTime.isAfter(monday) || buildTime.isEqual(monday))
				&& (buildTime.isBefore(sunday) || buildTime.isEqual(sunday));
	}

	/**
	 * @param buildFrequencyInfo buildFrequencyInfo
	 * @param buildList buildList
	 * @param trendLineName trendLineName
	 * @param jobName jobName
	 * @param aggDataMap aggDataMap
	 */
	private void prepareInfoForBuild(
			BuildFrequencyInfo buildFrequencyInfo,
			List<Build> buildList,
			String trendLineName,
			String jobName,
			Map<String, List<DataCount>> aggDataMap) {
		LocalDateTime currentDate = DateUtil.getTodayTime();

		Map<String, Double> weekRange = new LinkedHashMap<>();
		for (int i = 0; i < 12; i++) {
			CustomDateRange periodRange =
					KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, CommonConstant.WEEK);
			LocalDate monday = periodRange.getStartDate();
			LocalDate sunday = periodRange.getEndDate();
			String date = KpiHelperService.getDateRange(periodRange, CommonConstant.WEEK);
			int totalBuilds = 0;
			int successBuilds = 0;
			double buildSuccess = 0.0;
			for (Build build : buildList) {
				if (checkDateIsInWeeks(monday, sunday, build)) {
					totalBuilds++;
					if (BuildStatus.SUCCESS.name().equalsIgnoreCase(build.getBuildStatus().name()))
						successBuilds++;
					buildFrequencyInfo(buildFrequencyInfo, build, date);
				}
			}
			if (totalBuilds > 0) buildSuccess = ((double) successBuilds / totalBuilds) * 100;
			weekRange.put(date, buildSuccess);
			currentDate = DeveloperKpiHelper.getNextRangeDate(CommonConstant.WEEK, currentDate);
		}
		aggDataMap.putIfAbsent(jobName, new ArrayList<>());
		weekRange.forEach(
				(date, valueForCurrentLeaf) -> {
					DataCount dataCount = createDataCount(trendLineName, valueForCurrentLeaf, date);
					aggDataMap.get(jobName).add(dataCount);
				});
	}

	/**
	 * @param buildFrequencyInfo buildFrequencyInfo
	 * @param build build
	 * @param date date
	 */
	private void buildFrequencyInfo(BuildFrequencyInfo buildFrequencyInfo, Build build, String date) {
		if (null != buildFrequencyInfo) {

			if (StringUtils.isNotEmpty(build.getBuildJob())) {
				buildFrequencyInfo.addBuildJobNameList(build.getBuildJob());
			} else if (StringUtils.isNotEmpty(build.getJobFolder())) {
				buildFrequencyInfo.addBuildJobNameList(build.getJobFolder());
			} else {
				buildFrequencyInfo.addBuildJobNameList(build.getPipelineName());
			}
			buildFrequencyInfo.addBuildUrl(build.getBuildUrl());
			buildFrequencyInfo.addBuildStartTime(DateUtil.dateConverter(new Date(build.getStartTime())));
			buildFrequencyInfo.addWeeks(date);
			buildFrequencyInfo.addStatuses(build.getBuildStatus().name());
			buildFrequencyInfo.addBuildBranch(build.getBuildBranch());
		}
	}

	private DataCount createDataCount(String trendLineName, double valueForCurrentLeaf, String date) {
		DataCount dataCount = new DataCount();
		dataCount.setData(String.valueOf(valueForCurrentLeaf));
		dataCount.setSProjectName(trendLineName);
		dataCount.setDate(date);
		dataCount.setHoverValue(new HashMap<>());
		dataCount.setValue(valueForCurrentLeaf);
		return dataCount;
	}

	private void populateValidationDataObject(
			String requestTrackerId,
			List<KPIExcelData> excelData,
			String projectName,
			BuildFrequencyInfo buildFrequencyInfo) {

		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			KPIExcelUtility.populateBuildSuccessRate(excelData, projectName, buildFrequencyInfo);
		}
	}
}
