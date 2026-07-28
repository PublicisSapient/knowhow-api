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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jenkins.service.JenkinsKPIService;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
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
public class MeanTimeToTestFeedbackServiceImpl
		extends JenkinsKPIService<Double, List<Object>, Map<String, List<Object>>> {

	private static final String TOTAL_BUILDS = "Total Builds";

	private final BuildRepository buildRepository;
	private final ConfigHelperService configHelperService;

	@Override
	public String getQualifierType() {
		return KPICode.MEAN_TIME_TO_TEST_FEEDBACK.name();
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
		calculateAggregatedValueMap(projectNode, nodeWiseKPIValue, KPICode.MEAN_TIME_TO_TEST_FEEDBACK);

		Map<String, List<DataCount>> trendValuesMap =
				getTrendValuesMap(
						kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.MEAN_TIME_TO_TEST_FEEDBACK);

		kpiElement.setTrendValueList(
				DeveloperKpiHelper.prepareDataCountGroups(
						trendValuesMap, KPICode.MEAN_TIME_TO_TEST_FEEDBACK.getKpiId()));
		return kpiElement;
	}

	@Override
	public Double calculateKPIMetrics(Map<String, List<Object>> stringListMap) {
		return 0.0;
	}

	@Override
	public Map<String, List<Object>> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {

		String projectId = leafNodeList.get(0).getProjectFilter().getBasicProjectConfigId().toString();

		List<Build> builds =
				buildRepository.findBuildList(
						new HashMap<>(),
						Collections.singleton(leafNodeList.get(0).getProjectFilter().getBasicProjectConfigId()),
						startDate,
						endDate);

		Map<String, List<Object>> result = new HashMap<>();
		result.put(projectId, new ArrayList<>(builds));
		return result;
	}

	@Override
	public Double calculateKpiValue(List<Double> valueList, String kpiId) {
		return calculateKpiValueForDouble(valueList, kpiId);
	}

	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI219(), KPICode.MEAN_TIME_TO_TEST_FEEDBACK.getKpiId());
	}

	private void calculateProjectKpiTrendData(
			KpiElement kpiElement,
			Map<String, Node> mapTmp,
			Node projectLeafNode,
			KpiRequest kpiRequest) {

		Map<String, List<Object>> dataMap =
				fetchKPIDataFromDb(
						List.of(projectLeafNode),
						LocalDate.now().minusWeeks(12).toString(),
						LocalDate.now().plusDays(1).toString(),
						kpiRequest);

		String projectId = projectLeafNode.getProjectFilter().getBasicProjectConfigId().toString();
		ObjectId basicProjectConfigId = projectLeafNode.getProjectFilter().getBasicProjectConfigId();

		List<Build> allBuilds =
				dataMap.getOrDefault(projectId, Collections.emptyList()).stream()
						.map(Build.class::cast)
						.toList();

		if (CollectionUtils.isEmpty(allBuilds)) {
			mapTmp.get(projectLeafNode.getId()).setValue(null);
			return;
		}

		FieldMapping fieldMapping = configHelperService.getFieldMappingMap().get(basicProjectConfigId);
		List<String> e2eBranchList =
				fieldMapping != null ? fieldMapping.getE2eTestBranchKPI219() : null;
		Set<String> resolvedBranches =
				CollectionUtils.isEmpty(e2eBranchList)
						? Collections.emptySet()
						: new HashSet<>(e2eBranchList);

		List<Build> filtered =
				allBuilds.stream()
						.filter(
								b ->
										resolvedBranches.isEmpty()
												|| resolvedBranches.stream()
														.anyMatch(br -> br.equalsIgnoreCase(b.getBuildBranch())))
						.collect(Collectors.toList());

		if (CollectionUtils.isEmpty(filtered)) {
			mapTmp.get(projectLeafNode.getId()).setValue(null);
			return;
		}

		// keyMetadata maps the filter-visible display label → [workflow, branch]
		Map<String, String[]> keyMetadata = new LinkedHashMap<>();

		Map<String, List<Build>> byWorkflow =
				filtered.stream()
						.collect(Collectors.groupingBy(b -> b.getBuildJob() + "#" + b.getBuildBranch()));

		String trendLineName = projectLeafNode.getProjectFilter().getName();
		Map<String, List<DataCount>> aggDataMap = new LinkedHashMap<>();

		for (Map.Entry<String, List<Build>> entry : byWorkflow.entrySet()) {
			String rawKey = entry.getKey();
			int sep = rawKey.indexOf('#');
			String workflow = sep >= 0 ? rawKey.substring(0, sep) : rawKey;
			String branch = sep >= 0 ? rawKey.substring(sep + 1) : "";
			// filter1 = workflow, filter2 = branch — two independent filter dropdowns
			String displayKey = workflow + "#" + branch;
			keyMetadata.put(displayKey, new String[] {workflow, branch});
			prepareInfoForWorkflow(trendLineName, displayKey, entry.getValue(), aggDataMap);
		}

		mapTmp.get(projectLeafNode.getId()).setValue(aggDataMap);

		// Excel: date-first ordering (oldest week first), all workflows per week
		// together.
		// aggDataMap lists are newest-first (index 0 = this week), so read from the
		// tail.
		List<KPIExcelData> excelData = new ArrayList<>();
		int weekCount = aggDataMap.isEmpty() ? 0 : aggDataMap.values().iterator().next().size();
		for (int weekIdx = weekCount - 1; weekIdx >= 0; weekIdx--) {
			for (Map.Entry<String, List<DataCount>> entry : aggDataMap.entrySet()) {
				String[] meta = keyMetadata.getOrDefault(entry.getKey(), new String[] {entry.getKey(), ""});
				DataCount dc = entry.getValue().get(weekIdx);
				Map<String, Object> hover = dc.getHoverValue();
				if (hover == null) continue;
				int builds = (Integer) hover.getOrDefault(TOTAL_BUILDS, 0);
				if (builds == 0) continue;
				Map<String, Object> extras =
						dc.getSubfilterValues() != null ? dc.getSubfilterValues() : Map.of();
				KPIExcelData row = new KPIExcelData();
				row.setDaysWeeks(dc.getDate());
				row.setWorkflow(meta[0]);
				row.setBranch(meta[1]);
				row.setTotalBuilds(String.valueOf(builds));
				row.setSuccessfulBuilds(String.valueOf(extras.getOrDefault("successCount", 0)));
				row.setFailedBuilds(String.valueOf(extras.getOrDefault("failCount", 0)));
				row.setAvgDuration(dc.getData()); // plain hours value; column header carries the unit
				excelData.add(row);
			}
		}
		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(KPIExcelColumn.MEAN_TIME_TO_TEST_FEEDBACK.getColumns());
	}

	private void prepareInfoForWorkflow(
			String trendLineName,
			String workflowName,
			List<Build> builds,
			Map<String, List<DataCount>> aggDataMap) {

		// Build newest-first so the chart framework's internal reversal yields
		// oldest-left.
		LocalDateTime currentDate = DateUtil.getTodayTime();

		for (int i = 0; i < 12; i++) {
			CustomDateRange range =
					KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, CommonConstant.WEEK);
			LocalDate monday = range.getStartDate();
			LocalDate sunday = range.getEndDate();
			String dateLabel = KpiHelperService.getDateRange(range, CommonConstant.WEEK);

			int buildCount = 0;
			int successCount = 0;
			int failCount = 0;
			long totalMs = 0;

			for (Build b : builds) {
				LocalDate buildDate =
						Instant.ofEpochMilli(b.getStartTime()).atZone(ZoneId.systemDefault()).toLocalDate();
				boolean inRange =
						(buildDate.isAfter(monday) || buildDate.isEqual(monday))
								&& (buildDate.isBefore(sunday) || buildDate.isEqual(sunday));
				if (!inRange) continue;
				totalMs += b.getDuration();
				buildCount++;
				if (BuildStatus.SUCCESS == b.getBuildStatus()) {
					successCount++;
				} else {
					failCount++;
				}
			}

			double avgMinutes = buildCount > 0 ? (totalMs / (double) buildCount) / 60_000.0 : 0.0;
			// Chart value always in hours (kpiUnit = "Hours"); tooltip stays adaptive.
			double avgHours = Math.round((avgMinutes / 60.0) * 100.0) / 100.0;
			String tooltipDisplay =
					avgMinutes >= 60.0
							? String.format("%.2f Hrs", avgHours)
							: String.format("%.2f Mins", Math.round(avgMinutes * 100.0) / 100.0);

			aggDataMap.putIfAbsent(workflowName, new ArrayList<>());
			DataCount dc = new DataCount();
			dc.setData(String.format("%.2f", avgHours));
			dc.setSProjectName(trendLineName);
			dc.setDate(dateLabel);
			dc.setValue(avgHours);

			Map<String, Object> hover = new HashMap<>();
			hover.put(TOTAL_BUILDS, buildCount);
			hover.put("Avg Duration", tooltipDisplay);
			dc.setHoverValue(hover);

			// Store for Excel-only columns (not displayed in tooltip)
			Map<String, Object> excelExtras = new HashMap<>();
			excelExtras.put("successCount", successCount);
			excelExtras.put("failCount", failCount);
			dc.setSubfilterValues(excelExtras);

			aggDataMap.get(workflowName).add(dc);
			currentDate = DeveloperKpiHelper.getNextRangeDate(CommonConstant.WEEK, currentDate);
		}
	}
}
