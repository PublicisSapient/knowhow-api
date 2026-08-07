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
import java.util.Comparator;
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
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
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
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
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
	private static final String STRATEGY_BUILD = "BUILD";
	private static final String STRATEGY_COMMIT = "COMMIT";

	private final BuildRepository buildRepository;
	private final ConfigHelperService configHelperService;
	private final ScmKpiHelperService scmKpiHelperService;

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

	String resolveCalculationStrategy(ObjectId basicProjectConfigId) {
		FieldMapping fieldMapping = configHelperService.getFieldMappingMap().get(basicProjectConfigId);
		if (fieldMapping == null) return STRATEGY_BUILD;
		String strategy = fieldMapping.getCalculationStrategyKPI219();
		return STRATEGY_COMMIT.equalsIgnoreCase(strategy) ? STRATEGY_COMMIT : STRATEGY_BUILD;
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

		String strategy = resolveCalculationStrategy(basicProjectConfigId);
		boolean isCommitStrategy = STRATEGY_COMMIT.equals(strategy);

		List<ScmCommits> commits = Collections.emptyList();
		List<ScmMergeRequests> mergeRequests = Collections.emptyList();
		if (isCommitStrategy) {
			CustomDateRange dateRange = new CustomDateRange();
			dateRange.setStartDate(LocalDate.now().minusWeeks(12));
			dateRange.setEndDate(LocalDate.now().plusDays(1));
			commits = scmKpiHelperService.getCommitDetails(basicProjectConfigId, dateRange);
			mergeRequests = scmKpiHelperService.getMergeRequests(basicProjectConfigId, dateRange);
		}

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
			String displayKey = workflow + "#" + branch;
			keyMetadata.put(displayKey, new String[] {workflow, branch});

			if (isCommitStrategy) {
				prepareInfoForWorkflowCommit(
						trendLineName, displayKey, entry.getValue(), aggDataMap, commits, mergeRequests);
			} else {
				prepareInfoForWorkflow(trendLineName, displayKey, entry.getValue(), aggDataMap);
			}
		}

		mapTmp.get(projectLeafNode.getId()).setValue(aggDataMap);

		// Excel — oldest week first
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
				row.setAvgDuration(dc.getData());

				row.setSuccessfulBuilds(String.valueOf(extras.getOrDefault("successCount", 0)));
				row.setFailedBuilds(String.valueOf(extras.getOrDefault("failCount", 0)));
				row.setBuildsSkipped(String.valueOf(extras.getOrDefault("skippedCount", 0)));
				row.setPrsInWindow(String.valueOf(extras.getOrDefault("prsInWindow", "")));
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

			Map<String, Object> excelExtras = new HashMap<>();
			excelExtras.put("successCount", successCount);
			excelExtras.put("failCount", failCount);
			excelExtras.put("skippedCount", 0);
			excelExtras.put("prsInWindow", "");
			dc.setSubfilterValues(excelExtras);

			aggDataMap.get(workflowName).add(dc);
			currentDate = DeveloperKpiHelper.getNextRangeDate(CommonConstant.WEEK, currentDate);
		}
	}

	private void prepareInfoForWorkflowCommit(
			String trendLineName,
			String workflowName,
			List<Build> builds,
			Map<String, List<DataCount>> aggDataMap,
			List<ScmCommits> allCommits,
			List<ScmMergeRequests> allMergeRequests) {

		// Sort all builds for this workflow+branch oldest-first for window computation
		List<Build> sorted =
				builds.stream()
						.sorted(Comparator.comparingLong(Build::getStartTime))
						.collect(Collectors.toList());

		// Pre-compute per-build result: null = skipped, non-null = [durationHours]
		List<double[]> durationPerBuild = new ArrayList<>(sorted.size());
		List<String> prsPerBuild = new ArrayList<>(sorted.size());

		for (int i = 0; i < sorted.size(); i++) {
			Build build = sorted.get(i);
			long windowStart = i == 0 ? 0L : sorted.get(i - 1).getStartTime();
			long windowEnd = build.getStartTime();
			String buildBranch = build.getBuildBranch();

			List<ScmCommits> commitsInWindow =
					allCommits.stream()
							.filter(
									c ->
											c.getCommitTimestamp() != null
													&& c.getCommitTimestamp() > windowStart
													&& c.getCommitTimestamp() < windowEnd
													&& matchesBranch(c, buildBranch))
							.collect(Collectors.toList());

			if (commitsInWindow.isEmpty()) {
				durationPerBuild.add(null);
				prsPerBuild.add(null);
				continue;
			}

			long earliestCommitMs =
					commitsInWindow.stream().mapToLong(ScmCommits::getCommitTimestamp).min().getAsLong();
			long buildEndMs = build.getStartTime() + build.getDuration();
			double hours = (buildEndMs - earliestCommitMs) / 3_600_000.0;
			if (hours < 0) {
				durationPerBuild.add(null);
				prsPerBuild.add(null);
				continue;
			}
			durationPerBuild.add(new double[] {Math.round(hours * 100.0) / 100.0});

			String prStr =
					allMergeRequests.stream()
							.filter(
									mr ->
											mr.getMergedAt() != null
													&& mr.getToBranch() != null
													&& mr.getToBranch().equalsIgnoreCase(buildBranch))
							.filter(
									mr -> {
										long mergedMs =
												mr.getMergedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
										return mergedMs > windowStart && mergedMs < windowEnd;
									})
							.map(mr -> "#" + mr.getExternalId())
							.collect(Collectors.joining(", "));
			prsPerBuild.add(prStr);
		}

		// Bucket by week (newest-first, matching the BUILD path ordering)
		LocalDateTime currentDate = DateUtil.getTodayTime();
		aggDataMap.putIfAbsent(workflowName, new ArrayList<>());

		for (int i = 0; i < 12; i++) {
			CustomDateRange range =
					KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, CommonConstant.WEEK);
			LocalDate monday = range.getStartDate();
			LocalDate sunday = range.getEndDate();
			String dateLabel = KpiHelperService.getDateRange(range, CommonConstant.WEEK);

			int buildsWithCommits = 0;
			int buildsSkipped = 0;
			int successCount = 0;
			int failCount = 0;
			double totalHours = 0.0;
			StringBuilder prsBuilder = new StringBuilder();

			for (int j = 0; j < sorted.size(); j++) {
				LocalDate buildDate =
						Instant.ofEpochMilli(sorted.get(j).getStartTime())
								.atZone(ZoneId.systemDefault())
								.toLocalDate();
				boolean inRange =
						(buildDate.isAfter(monday) || buildDate.isEqual(monday))
								&& (buildDate.isBefore(sunday) || buildDate.isEqual(sunday));
				if (!inRange) continue;

				if (durationPerBuild.get(j) == null) {
					buildsSkipped++;
				} else {
					buildsWithCommits++;
					totalHours += durationPerBuild.get(j)[0];
					if (BuildStatus.SUCCESS == sorted.get(j).getBuildStatus()) {
						successCount++;
					} else {
						failCount++;
					}
					String prs = prsPerBuild.get(j);
					if (prs != null && !prs.isEmpty()) {
						if (prsBuilder.length() > 0) prsBuilder.append(", ");
						prsBuilder.append(prs);
					}
				}
			}

			int totalBuilds = buildsWithCommits + buildsSkipped;
			double avgHours =
					buildsWithCommits > 0
							? Math.round((totalHours / buildsWithCommits) * 100.0) / 100.0
							: 0.0;
			double avgMinutes = avgHours * 60.0;
			String tooltipDisplay =
					avgMinutes >= 60.0
							? String.format("%.2f Hrs", avgHours)
							: String.format("%.2f Mins", Math.round(avgMinutes * 100.0) / 100.0);

			DataCount dc = new DataCount();
			dc.setData(String.format("%.2f", avgHours));
			dc.setSProjectName(trendLineName);
			dc.setDate(dateLabel);
			dc.setValue(avgHours);

			Map<String, Object> hover = new HashMap<>();
			hover.put(TOTAL_BUILDS, totalBuilds);
			hover.put("Avg Duration", tooltipDisplay);
			dc.setHoverValue(hover);

			Map<String, Object> excelExtras = new HashMap<>();
			excelExtras.put("successCount", successCount);
			excelExtras.put("failCount", failCount);
			excelExtras.put("skippedCount", buildsSkipped);
			excelExtras.put("prsInWindow", prsBuilder.toString());
			dc.setSubfilterValues(excelExtras);

			aggDataMap.get(workflowName).add(dc);
			currentDate = DeveloperKpiHelper.getNextRangeDate(CommonConstant.WEEK, currentDate);
		}
	}

	private boolean matchesBranch(ScmCommits commit, String buildBranch) {
		if (buildBranch == null || buildBranch.isEmpty()) return false;
		return (commit.getBranchName() != null && commit.getBranchName().equalsIgnoreCase(buildBranch))
				|| (commit.getBranch() != null && commit.getBranch().equalsIgnoreCase(buildBranch));
	}
}
