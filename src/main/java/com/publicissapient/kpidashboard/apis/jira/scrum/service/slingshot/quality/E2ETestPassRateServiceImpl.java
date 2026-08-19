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
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

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
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.TestSuiteExecution;
import com.publicissapient.kpidashboard.common.repository.application.TestSuiteExecutionRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class E2ETestPassRateServiceImpl
		extends JenkinsKPIService<Double, List<Object>, Map<String, List<Object>>> {

	private static final String TOTAL_BUILDS = "Total Builds";
	private static final String AVG_PASSED = "Avg Passed";
	private static final String AVG_FAILED = "Avg Failed";
	private static final String PASS_RATE = "Pass Rate %";

	private final TestSuiteExecutionRepository testSuiteExecutionRepository;

	@Override
	public String getQualifierType() {
		return KPICode.E2E_TEST_PASS_RATE.name();
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
		calculateAggregatedValueMap(projectNode, nodeWiseKPIValue, KPICode.E2E_TEST_PASS_RATE);

		Map<String, List<DataCount>> trendValuesMap =
				getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.E2E_TEST_PASS_RATE);

		kpiElement.setTrendValueList(
				DeveloperKpiHelper.prepareDataCountGroups(
						trendValuesMap, KPICode.E2E_TEST_PASS_RATE.getKpiId()));

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
			String kpiId = KPICode.E2E_TEST_PASS_RATE.getKpiId();
			kpiElement.setOverallMaturity(
					calculateMaturity(
							getMaturityRange(kpiId), kpiId, String.valueOf(round(overallAvg.getAsDouble()))));
		}
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

		long startEpochMs =
				LocalDate.parse(startDate).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

		List<TestSuiteExecution> records =
				testSuiteExecutionRepository.findByBasicProjectConfigIdInAndBuildTimestampGreaterThanEqual(
						List.of(projectId), startEpochMs);

		Map<String, List<Object>> projectData = new HashMap<>();
		projectData.put(projectId, new ArrayList<>(records));
		return projectData;
	}

	@Override
	public Double calculateKpiValue(List<Double> valueList, String kpiId) {
		return calculateKpiValueForDouble(valueList, kpiId);
	}

	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI218(), KPICode.E2E_TEST_PASS_RATE.getKpiId());
	}

	@SuppressWarnings("unchecked")
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

		List<TestSuiteExecution> allRecords =
				dataMap.getOrDefault(projectId, Collections.emptyList()).stream()
						.map(TestSuiteExecution.class::cast)
						.toList();

		if (CollectionUtils.isEmpty(allRecords)) {
			mapTmp.get(projectLeafNode.getId()).setValue(null);
			return;
		}

		String trendLineName = projectLeafNode.getProjectFilter().getName();
		Map<String, List<DataCount>> aggDataMap = new LinkedHashMap<>();

		// keyMetadata maps the filter-visible display label → [workflow, suite, branch]
		Map<String, String[]> keyMetadata = new LinkedHashMap<>();

		Map<String, List<TestSuiteExecution>> bySuite =
				allRecords.stream()
						.collect(
								Collectors.groupingBy(
										r -> r.getJobName() + "#" + r.getSuiteName() + "#" + r.getBuildBranch()));

		for (Map.Entry<String, List<TestSuiteExecution>> entry : bySuite.entrySet()) {
			String rawKey = entry.getKey();
			int sep1 = rawKey.indexOf('#');
			int sep2 = rawKey.lastIndexOf('#');
			String workflow = sep1 >= 0 ? rawKey.substring(0, sep1) : rawKey;
			String suite = (sep1 >= 0 && sep2 > sep1) ? rawKey.substring(sep1 + 1, sep2) : rawKey;
			String branch = sep2 > sep1 ? rawKey.substring(sep2 + 1) : "";
			// filter1 = workflow (first dropdown), filter2 = "suiteName (branch)" (second)
			// prepareDataCountGroups splits on first "#" to produce the two filter values
			String displayKey = workflow + "#" + suite + " (" + branch + ")";
			keyMetadata.put(displayKey, new String[] {workflow, suite, branch});
			prepareInfoForSuites(trendLineName, displayKey, entry.getValue(), aggDataMap);
		}

		mapTmp.get(projectLeafNode.getId()).setValue(aggDataMap);

		List<KPIExcelData> excelData = new ArrayList<>();
		for (Map.Entry<String, List<DataCount>> entry : aggDataMap.entrySet()) {
			String[] meta =
					keyMetadata.getOrDefault(entry.getKey(), new String[] {"", entry.getKey(), ""});
			for (DataCount dc : entry.getValue()) {
				Map<String, Object> hover = dc.getHoverValue();
				if (hover == null) continue;
				int builds = (Integer) hover.getOrDefault(TOTAL_BUILDS, 0);
				if (builds == 0) continue;
				Map<String, Object> extras =
						dc.getSubfilterValues() != null ? dc.getSubfilterValues() : Map.of();
				int totalTests = (Integer) extras.getOrDefault("totalTests", 0);
				int totalSkipped = (Integer) extras.getOrDefault("totalSkipped", 0);
				KPIExcelData row = new KPIExcelData();
				row.setDaysWeeks(dc.getDate());
				row.setWorkflow(meta[0]);
				row.setSuiteName(meta[1]);
				row.setBranch(meta[2]);
				row.setTotalBuilds(String.valueOf(builds));
				row.setAvgTestsPerBuild(
						builds > 0 ? String.valueOf((int) Math.round((double) totalTests / builds)) : "0");
				row.setAvgPassedTests(String.valueOf(hover.getOrDefault(AVG_PASSED, 0)));
				row.setAvgFailedTests(String.valueOf(hover.getOrDefault(AVG_FAILED, 0)));
				row.setAvgSkippedTests(
						builds > 0 ? String.valueOf((int) Math.round((double) totalSkipped / builds)) : "0");
				row.setPassRatePercentage((String) hover.getOrDefault(PASS_RATE, "0.0%"));
				excelData.add(row);
			}
		}
		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(KPIExcelColumn.E2E_TEST_PASS_RATE.getColumns());
	}

	private void prepareInfoForSuites(
			String trendLineName,
			String suiteName,
			List<TestSuiteExecution> suiteRecords,
			Map<String, List<DataCount>> aggDataMap) {

		LocalDateTime currentDate = DateUtil.getTodayTime();

		for (int i = 0; i < 12; i++) {
			CustomDateRange periodRange =
					KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, CommonConstant.WEEK);
			LocalDate monday = periodRange.getStartDate();
			LocalDate sunday = periodRange.getEndDate();
			String dateLabel = KpiHelperService.getDateRange(periodRange, CommonConstant.WEEK);

			int totalPassed = 0;
			int totalFailed = 0;
			int totalSkipped = 0;
			int buildCount = 0;

			for (TestSuiteExecution rec : suiteRecords) {
				LocalDate buildDate =
						Instant.ofEpochMilli(rec.getBuildTimestamp())
								.atZone(ZoneId.systemDefault())
								.toLocalDate();
				boolean inRange =
						(buildDate.isAfter(monday) || buildDate.isEqual(monday))
								&& (buildDate.isBefore(sunday) || buildDate.isEqual(sunday));
				if (!inRange) continue;

				totalPassed += rec.getPassedTests() != null ? rec.getPassedTests() : 0;
				totalFailed += rec.getFailedTests() != null ? rec.getFailedTests() : 0;
				totalSkipped += rec.getSkippedTests() != null ? rec.getSkippedTests() : 0;
				buildCount++;
			}

			double passRate = 0.0;
			int denominator = totalPassed + totalFailed;
			if (denominator > 0) {
				passRate = ((double) totalPassed / denominator) * 100;
			}

			aggDataMap.putIfAbsent(suiteName, new ArrayList<>());
			DataCount dataCount = new DataCount();
			dataCount.setData(String.format("%.2f", passRate));
			dataCount.setSProjectName(trendLineName);
			dataCount.setDate(dateLabel);
			dataCount.setValue(Math.round(passRate * 100.0) / 100.0);

			Map<String, Object> hoverMap = new HashMap<>();
			hoverMap.put(TOTAL_BUILDS, buildCount);
			hoverMap.put(AVG_PASSED, buildCount > 0 ? Math.round((double) totalPassed / buildCount) : 0);
			hoverMap.put(AVG_FAILED, buildCount > 0 ? Math.round((double) totalFailed / buildCount) : 0);
			hoverMap.put(PASS_RATE, String.format("%.2f%%", passRate));
			dataCount.setHoverValue(hoverMap);

			// Store raw totals for Excel-only columns (not displayed in tooltip)
			Map<String, Object> excelExtras = new HashMap<>();
			excelExtras.put("totalTests", totalPassed + totalFailed + totalSkipped);
			excelExtras.put("totalSkipped", totalSkipped);
			dataCount.setSubfilterValues(excelExtras);

			aggDataMap.get(suiteName).add(dataCount);
			currentDate = DeveloperKpiHelper.getNextRangeDate(CommonConstant.WEEK, currentDate);
		}
	}
}
