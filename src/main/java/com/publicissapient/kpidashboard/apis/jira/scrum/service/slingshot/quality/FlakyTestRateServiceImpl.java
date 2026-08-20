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
public class FlakyTestRateServiceImpl
		extends JenkinsKPIService<Double, List<Object>, Map<String, List<Object>>> {

	private static final String TOTAL_SUITES = "Total Suites";
	private static final String FLAKY_SUITES = "Flaky Suites";
	private static final String SUITES = "Suites";

	private final TestSuiteExecutionRepository testSuiteExecutionRepository;
	private final ConfigHelperService configHelperService;

	@Override
	public String getQualifierType() {
		return KPICode.FLAKY_TEST_RATE.name();
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
		calculateAggregatedValueMap(projectNode, nodeWiseKPIValue, KPICode.FLAKY_TEST_RATE);

		Map<String, List<DataCount>> trendValuesMap =
				getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.FLAKY_TEST_RATE);

		kpiElement.setTrendValueList(
				DeveloperKpiHelper.prepareDataCountGroups(
						trendValuesMap, KPICode.FLAKY_TEST_RATE.getKpiId()));
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
		long startEpoch =
				LocalDate.parse(startDate).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

		List<TestSuiteExecution> records =
				testSuiteExecutionRepository.findByBasicProjectConfigIdInAndBuildTimestampGreaterThanEqual(
						List.of(projectId), startEpoch);

		Map<String, List<Object>> result = new HashMap<>();
		result.put(projectId, new ArrayList<>(records));
		return result;
	}

	@Override
	public Double calculateKpiValue(List<Double> valueList, String kpiId) {
		return calculateKpiValueForDouble(valueList, kpiId);
	}

	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI220(), KPICode.FLAKY_TEST_RATE.getKpiId());
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

		List<TestSuiteExecution> allRecords =
				dataMap.getOrDefault(projectId, Collections.emptyList()).stream()
						.map(TestSuiteExecution.class::cast)
						.toList();

		if (CollectionUtils.isEmpty(allRecords)) {
			mapTmp.get(projectLeafNode.getId()).setValue(null);
			return;
		}

		FieldMapping fieldMapping = configHelperService.getFieldMappingMap().get(basicProjectConfigId);
		List<String> e2eBranchList =
				fieldMapping != null ? fieldMapping.getE2eTestBranchKPI220() : null;
		Set<String> resolvedBranches =
				CollectionUtils.isEmpty(e2eBranchList)
						? Collections.emptySet()
						: new HashSet<>(e2eBranchList);

		List<TestSuiteExecution> filtered =
				allRecords.stream()
						.filter(
								r ->
										resolvedBranches.isEmpty()
												|| resolvedBranches.stream()
														.anyMatch(br -> br.equalsIgnoreCase(r.getBuildBranch())))
						.collect(Collectors.toList());

		if (CollectionUtils.isEmpty(filtered)) {
			mapTmp.get(projectLeafNode.getId()).setValue(null);
			return;
		}

		String trendLineName = projectLeafNode.getProjectFilter().getName();
		Map<String, List<DataCount>> aggDataMap = new LinkedHashMap<>();
		Map<String, String[]> keyMetadata = new LinkedHashMap<>();

		Map<String, List<TestSuiteExecution>> byWorkflowBranch =
				filtered.stream()
						.collect(
								Collectors.groupingBy(
										r ->
												(r.getJobName() != null ? r.getJobName() : "")
														+ "#"
														+ (r.getBuildBranch() != null ? r.getBuildBranch() : "")));

		for (Map.Entry<String, List<TestSuiteExecution>> entry : byWorkflowBranch.entrySet()) {
			String rawKey = entry.getKey();
			int sep = rawKey.indexOf('#');
			String workflow = sep >= 0 ? rawKey.substring(0, sep) : rawKey;
			String branch = sep >= 0 ? rawKey.substring(sep + 1) : "";
			// Use "default" when branch is empty so the filter2 dropdown has a selectable
			// value
			String branchDisplay = (branch != null && !branch.isEmpty()) ? branch : "default";
			String displayKey = workflow + "#" + branchDisplay;
			keyMetadata.put(displayKey, new String[] {workflow, branch});
			prepareInfoForFlakiness(trendLineName, displayKey, entry.getValue(), aggDataMap);
		}

		mapTmp.get(projectLeafNode.getId()).setValue(aggDataMap);

		List<KPIExcelData> excelData = new ArrayList<>();
		int weekCount = aggDataMap.isEmpty() ? 0 : aggDataMap.values().iterator().next().size();
		for (int weekIdx = weekCount - 1; weekIdx >= 0; weekIdx--) {
			for (Map.Entry<String, List<DataCount>> entry : aggDataMap.entrySet()) {
				String[] meta = keyMetadata.getOrDefault(entry.getKey(), new String[] {entry.getKey(), ""});
				DataCount dc = entry.getValue().get(weekIdx);
				Map<String, Object> extras = dc.getSubfilterValues();
				if (extras == null) continue;
				@SuppressWarnings("unchecked")
				Map<String, String> suiteDetail =
						(Map<String, String>) extras.getOrDefault(SUITES, Collections.emptyMap());
				for (Map.Entry<String, String> suiteEntry : suiteDetail.entrySet()) {
					String[] parts = suiteEntry.getValue().split(" / ");
					String passingStr = parts.length > 0 ? parts[0].replace(" pass", "").trim() : "0";
					String failingStr = parts.length > 1 ? parts[1].replace(" fail", "").trim() : "0";
					int passing = parseSafe(passingStr);
					int failing = parseSafe(failingStr);
					int total = passing + failing;
					boolean isFlaky = passing > 0 && failing > 0 && total >= 2;
					KPIExcelData row = new KPIExcelData();
					row.setDaysWeeks(dc.getDate());
					row.setWorkflow(meta[0]);
					row.setBranch(meta[1]);
					row.setSuiteName(suiteEntry.getKey());
					row.setTotalBuilds(String.valueOf(total));
					row.setPassingRuns(String.valueOf(passing));
					row.setFailingRuns(String.valueOf(failing));
					row.setFlaky(isFlaky ? "Yes" : "No");
					row.setFlakyRate(dc.getData());
					excelData.add(row);
				}
			}
		}
		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(KPIExcelColumn.FLAKY_TEST_RATE.getColumns());
	}

	private void prepareInfoForFlakiness(
			String trendLineName,
			String key,
			List<TestSuiteExecution> allFiltered,
			Map<String, List<DataCount>> aggDataMap) {

		LocalDateTime currentDate = DateUtil.getTodayTime();

		for (int i = 0; i < 12; i++) {
			CustomDateRange range =
					KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, CommonConstant.WEEK);
			LocalDate monday = range.getStartDate();
			LocalDate sunday = range.getEndDate();
			String dateLabel = KpiHelperService.getDateRange(range, CommonConstant.WEEK);

			long weekStart = monday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
			long weekEnd =
					sunday.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

			List<TestSuiteExecution> weekRecords =
					allFiltered.stream()
							.filter(
									r ->
											r.getBuildTimestamp() != null
													&& r.getBuildTimestamp() >= weekStart
													&& r.getBuildTimestamp() < weekEnd)
							.collect(Collectors.toList());

			// Compute bySuite once — used for rate, hover scalars, and suite detail
			Map<String, List<TestSuiteExecution>> bySuite =
					weekRecords.stream()
							.filter(r -> r.getSuiteName() != null)
							.collect(Collectors.groupingBy(TestSuiteExecution::getSuiteName));

			int suitesWithEnoughRuns = 0;
			int flakySuitesCount = 0;
			Map<String, String> suiteDetail = new LinkedHashMap<>();

			for (Map.Entry<String, List<TestSuiteExecution>> e : bySuite.entrySet()) {
				List<TestSuiteExecution> runs = e.getValue();
				long passing =
						runs.stream()
								.filter(r -> r.getFailedTests() != null && r.getFailedTests() == 0)
								.count();
				long failing =
						runs.stream().filter(r -> r.getFailedTests() != null && r.getFailedTests() > 0).count();
				suiteDetail.put(e.getKey(), passing + " pass / " + failing + " fail");

				if (runs.size() < 2) continue;
				suitesWithEnoughRuns++;
				boolean hasPass =
						runs.stream()
								.anyMatch(
										r ->
												r.getFailedTests() != null
														&& r.getFailedTests() == 0
														&& r.getPassedTests() != null
														&& r.getPassedTests() > 0);
				boolean hasFail =
						runs.stream().anyMatch(r -> r.getFailedTests() != null && r.getFailedTests() > 0);
				if (hasPass && hasFail) flakySuitesCount++;
			}

			double rate =
					suitesWithEnoughRuns > 0 ? ((double) flakySuitesCount / suitesWithEnoughRuns) * 100 : 0.0;
			double flakyRate = Math.round(rate * 100.0) / 100.0;

			// Hover contains only scalar values — nested Maps show as "Object" in the UI
			Map<String, Object> hover = new LinkedHashMap<>();
			hover.put(TOTAL_SUITES, bySuite.size());
			hover.put(FLAKY_SUITES, flakySuitesCount);

			// Suite-level detail is Excel-only; store separately so it doesn't reach the
			// tooltip
			Map<String, Object> extras = new LinkedHashMap<>();
			extras.put(SUITES, suiteDetail);

			aggDataMap.putIfAbsent(key, new ArrayList<>());
			DataCount dc = new DataCount();
			dc.setData(String.valueOf(flakyRate));
			dc.setSProjectName(trendLineName);
			dc.setDate(dateLabel);
			dc.setValue(flakyRate);
			dc.setHoverValue(hover);
			dc.setSubfilterValues(extras);
			aggDataMap.get(key).add(dc);

			currentDate = DeveloperKpiHelper.getNextRangeDate(CommonConstant.WEEK, currentDate);
		}
	}

	private int parseSafe(String s) {
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
