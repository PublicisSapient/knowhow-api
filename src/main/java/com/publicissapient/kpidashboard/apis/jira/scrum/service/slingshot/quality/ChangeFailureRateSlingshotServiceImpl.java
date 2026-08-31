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

package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.quality;

import static com.publicissapient.kpidashboard.common.constant.CommonConstant.HIERARCHY_LEVEL_ID_PROJECT;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jenkins.service.JenkinsKPIService;
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

/**
 * Slingshot Change Failure Rate (kpi221).
 *
 * <p>Measures the percentage of CI/CD pipeline builds that fail per job/branch per week, used as a
 * proxy for the DORA Change Failure Rate — the proportion of deployments causing a production
 * incident (rollback, hotfix, or revert). Only SUCCESS and FAILURE build statuses are included in
 * the denominator; aborted or unstable builds are excluded so that infrastructure noise does not
 * skew the metric.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeFailureRateSlingshotServiceImpl
		extends JenkinsKPIService<Double, List<Object>, Map<String, List<Object>>> {

	private static final String TOTAL_BUILDS = "Total Builds";
	private static final String FAILED_BUILDS = "Failed Builds";
	private static final String SUCCESSFUL_BUILDS = "Successful Builds";
	private static final int WEEK_COUNT = 12;
	private static final String JOB_BRANCH_SEPARATOR = "#";

	private final BuildRepository buildRepository;

	@Override
	public String getQualifierType() {
		return KPICode.CHANGE_FAILURE_RATE_SLINGSHOT.name();
	}

	@Override
	public KpiElement getKpiData(
			KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
			throws ApplicationException {

		Node projectNode =
				treeAggregatorDetail.getMapOfListOfProjectNodes().get(HIERARCHY_LEVEL_ID_PROJECT).get(0);
		kpiRequest.setXAxisDataPoints(WEEK_COUNT);
		kpiRequest.setDuration(CommonConstant.WEEK);
		Map<String, Node> nodeMap = Map.of(projectNode.getId(), projectNode);
		calculateProjectKpiTrendData(kpiElement, nodeMap, projectNode, kpiRequest);

		log.debug(
				"[CHANGE-FAILURE-RATE-SLINGSHOT][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				projectNode);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValueMap(
				projectNode, nodeWiseKPIValue, KPICode.CHANGE_FAILURE_RATE_SLINGSHOT);

		Map<String, List<DataCount>> trendValuesMap =
				getTrendValuesMap(
						kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.CHANGE_FAILURE_RATE_SLINGSHOT);

		kpiElement.setTrendValueList(
				DeveloperKpiHelper.prepareDataCountGroups(
						trendValuesMap, KPICode.CHANGE_FAILURE_RATE_SLINGSHOT.getKpiId()));

		OptionalDouble overallAvg =
				trendValuesMap.values().stream()
						.filter(list -> !list.isEmpty())
						.mapToDouble(
								list -> {
									try {
										return Double.parseDouble(list.get(0).getData());
									} catch (NumberFormatException | NullPointerException e) {
										return 0.0;
									}
								})
						.average();
		if (overallAvg.isPresent()) {
			String kpiId = KPICode.CHANGE_FAILURE_RATE_SLINGSHOT.getKpiId();
			double val = round(overallAvg.getAsDouble());
			kpiElement.setOverallMaturity(
					calculateMaturity(getMaturityRange(kpiId), kpiId, String.valueOf(val)));
			kpiElement.setOverAllMaturityValue(String.valueOf(val));
		}
		return kpiElement;
	}

	@Override
	public Double calculateKPIMetrics(Map<String, List<Object>> stringListMap) {
		return 0.0;
	}

	/**
	 * Fetches builds in the date window, restricted to SUCCESS and FAILURE statuses only.
	 * Aborted/unstable builds are excluded so they do not inflate the denominator and skew the
	 * failure rate.
	 */
	@Override
	public Map<String, List<Object>> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {

		Map<String, List<String>> statusFilter = new HashMap<>();
		statusFilter.put(
				"buildStatus", List.of(BuildStatus.SUCCESS.name(), BuildStatus.FAILURE.name()));

		List<Build> buildList =
				buildRepository.findBuildList(
						statusFilter,
						Collections.singleton(leafNodeList.get(0).getProjectFilter().getBasicProjectConfigId()),
						startDate,
						endDate);

		Map<String, List<Object>> result = new HashMap<>();
		result.put(
				leafNodeList.get(0).getProjectFilter().getBasicProjectConfigId().toString(),
				new ArrayList<>(buildList));
		return result;
	}

	@Override
	public Double calculateKpiValue(List<Double> valueList, String kpiId) {
		return calculateKpiValueForDouble(valueList, kpiId);
	}

	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI221(), KPICode.CHANGE_FAILURE_RATE_SLINGSHOT.getKpiId());
	}

	@SuppressWarnings("unchecked")
	private void calculateProjectKpiTrendData(
			KpiElement kpiElement,
			Map<String, Node> mapTmp,
			Node projectLeafNode,
			KpiRequest kpiRequest) {

		String requestTrackerId = getRequestTrackerId();

		Map<String, List<Object>> buildData =
				fetchKPIDataFromDb(
						List.of(projectLeafNode),
						LocalDate.now().minusWeeks(WEEK_COUNT).toString(),
						LocalDate.now().plusDays(1).toString(),
						kpiRequest);

		String projectId = projectLeafNode.getProjectFilter().getBasicProjectConfigId().toString();
		List<Build> allBuilds =
				buildData.getOrDefault(projectId, Collections.emptyList()).stream()
						.map(Build.class::cast)
						.toList();

		if (CollectionUtils.isEmpty(allBuilds)) {
			mapTmp.get(projectLeafNode.getId()).setValue(null);
			return;
		}

		String trendLineName = projectLeafNode.getProjectFilter().getName();
		List<KPIExcelData> excelData = new ArrayList<>();
		Map<String, List<DataCount>> aggDataMap = new HashMap<>();

		Map<String, List<Build>> buildsByJobBranch =
				allBuilds.stream()
						.collect(
								Collectors.groupingBy(
										b -> b.getBuildJob() + JOB_BRANCH_SEPARATOR + b.getBuildBranch()));

		for (Map.Entry<String, List<Build>> entry : buildsByJobBranch.entrySet()) {
			prepareWeeklyData(
					entry.getKey(), entry.getValue(), trendLineName, aggDataMap, excelData, requestTrackerId);
		}

		mapTmp.get(projectLeafNode.getId()).setValue(aggDataMap);
		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
		excelData.sort(
				Comparator.comparing(
						row -> LocalDate.parse(row.getDaysWeeks().split(" to ")[0].trim(), fmt)));
		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(KPIExcelColumn.CHANGE_FAILURE_RATE_SLINGSHOT.getColumns());
	}

	private void prepareWeeklyData(
			String jobBranchKey,
			List<Build> builds,
			String trendLineName,
			Map<String, List<DataCount>> aggDataMap,
			List<KPIExcelData> excelData,
			String requestTrackerId) {

		String[] parts = jobBranchKey.split(JOB_BRANCH_SEPARATOR, 2);
		String jobName = parts[0];
		String branchName = parts.length > 1 ? parts[1] : "";

		LocalDateTime cursor = DateUtil.getTodayTime();
		boolean collectExcel =
				requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase());
		List<KPIExcelData> groupExcelRows = collectExcel ? new ArrayList<>() : null;

		for (int i = 0; i < WEEK_COUNT; i++) {
			CustomDateRange range =
					KpiDataHelper.getStartAndEndDateTimeForDataFiltering(cursor, CommonConstant.WEEK);
			LocalDate monday = range.getStartDate();
			LocalDate sunday = range.getEndDate();
			String dateLabel = KpiHelperService.getDateRange(range, CommonConstant.WEEK);

			int totalBuilds = 0;
			int successBuilds = 0;
			int failedBuilds = 0;

			for (Build build : builds) {
				LocalDate buildDate =
						Instant.ofEpochMilli(build.getStartTime())
								.atZone(java.time.ZoneId.systemDefault())
								.toLocalDate();
				if (!buildDate.isBefore(monday) && !buildDate.isAfter(sunday)) {
					totalBuilds++;
					if (BuildStatus.FAILURE.equals(build.getBuildStatus())) {
						failedBuilds++;
					} else {
						successBuilds++;
					}
				}
			}

			double cfr =
					totalBuilds > 0
							? Math.round((double) failedBuilds / totalBuilds * 100.0 * 100.0) / 100.0
							: 0.0;

			aggDataMap.putIfAbsent(jobBranchKey, new ArrayList<>());
			aggDataMap
					.get(jobBranchKey)
					.add(
							createDataCount(
									trendLineName, cfr, dateLabel, totalBuilds, successBuilds, failedBuilds));

			if (collectExcel) {
				KPIExcelUtility.populateChangeFailureRateSlingshotExcelData(
						groupExcelRows,
						trendLineName,
						dateLabel,
						jobName,
						branchName,
						totalBuilds,
						successBuilds,
						failedBuilds,
						cfr);
			}

			cursor = DeveloperKpiHelper.getNextRangeDate(CommonConstant.WEEK, cursor);
		}

		// Loop runs newest→oldest; reverse so excel rows are oldest→newest within each
		// group.
		if (collectExcel) {
			Collections.reverse(groupExcelRows);
			excelData.addAll(groupExcelRows);
		}
	}

	private DataCount createDataCount(
			String trendLineName,
			double cfr,
			String date,
			int totalBuilds,
			int successBuilds,
			int failedBuilds) {
		DataCount dc = new DataCount();
		dc.setSProjectName(trendLineName);
		dc.setDate(date);
		dc.setData(String.format("%.2f", cfr));
		dc.setValue(cfr);
		Map<String, Object> hover = new HashMap<>();
		hover.put(TOTAL_BUILDS, totalBuilds);
		hover.put(SUCCESSFUL_BUILDS, successBuilds);
		hover.put(FAILED_BUILDS, failedBuilds);
		dc.setHoverValue(hover);
		return dc;
	}
}
