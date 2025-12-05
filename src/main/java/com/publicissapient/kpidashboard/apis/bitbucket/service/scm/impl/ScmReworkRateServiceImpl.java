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

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
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
import com.publicissapient.kpidashboard.apis.bitbucket.service.BitBucketKPIService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * SCM Rework Rate Service Implementation
 *
 * <h3>REWORK RATE LOGIC</h3>
 *
 * <b>What:</b> Measures how much code is being changed that was already changed in the past 21
 * days.
 *
 * <p><b>Why:</b> High rework indicates quality issues - developers are fixing/changing recently
 * written code.
 *
 * <p><b>How it works:</b>
 *
 * <pre>
 * 1. Look back 21 days (Past three weeks) to build a "reference pool" of all changed lines
 * 2. For current period, check if any changed lines were in the reference pool
 * 3. Rework Rate = (reworked lines / total lines changed) * 100
 *
 * Example:
 * - Day 1: Changed lines 10-20 in FileA.java (goes into reference pool)
 * - Day 15: Changed lines 15-25 in FileA.java
 *   - Lines 15-20 are rework (were changed before)
 *   - Lines 21-25 are new changes
 *   - Rework = 6 lines, Total = 11 lines
 *   - Rate = (6/11) * 100 = 54.55%
 *
 * Higher percentage = More rework (bad)
 * Lower percentage = Less rework (good)
 * </pre>
 *
 * @author shunaray
 */
@Slf4j
@Service
@AllArgsConstructor
public class ScmReworkRateServiceImpl
		extends BitBucketKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String COMMITS = "commits";
	private static final String ASSIGNEE_SET = "assigneeSet";
	private static final int REWORK_DAYS_AGO = 21;

	private final KpiHelperService kpiHelperService;
	private final ScmKpiHelperService scmKpiHelperService;
	private final ConfigHelperService configHelperService;

	/** Helper class to track rework calculation */
	private static class ReworkCalculation {
		private int totalChanges = 0;
		private int reworkChanges = 0;

		public void addTotalChanges(int changes) {
			this.totalChanges += changes;
		}

		public void addRework(int rework) {
			this.reworkChanges += rework;
		}

		public Double getPercentage() {
			if (totalChanges == 0) {
				return 0.0;
			}
			return ((double) reworkChanges / totalChanges) * 100;
		}
	}

	@Builder
	private record ToolDataContext(
			Tool tool,
			List<ScmCommits> commits,
			Set<Assignee> assignees,
			String dateLabel,
			String projectName,
			CustomDateRange periodRange,
			Map<String, List<DataCount>> kpiTrendDataByGroup,
			List<RepoToolValidationData> validationDataList,
			Map<ScmCommits, LocalDateTime> commitTimestampMap) {}

	@Override
	public String getQualifierType() {
		return KPICode.REWORK_RATE.name();
	}

	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node projectNode)
			throws ApplicationException {
		Map<String, Node> nodeMap = Map.of(projectNode.getId(), projectNode);
		calculateProjectKpiTrendData(kpiElement, nodeMap, projectNode, kpiRequest);

		log.debug(
				"[PROJECT-WISE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				projectNode);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValueMap(projectNode, nodeWiseKPIValue, KPICode.REWORK_RATE);

		Map<String, List<DataCount>> trendValuesMap =
				getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.REWORK_RATE);

		kpiElement.setTrendValueList(
				DeveloperKpiHelper.prepareDataCountGroups(
						trendValuesMap, KPICode.REWORK_RATE.getKpiId(), true));
		return kpiElement;
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
		Map<String, Object> resultMap = new HashMap<>();

		// Fetch commits from (current - dataPoints - 21 days) for reference data
		CustomDateRange dateRange = KpiDataHelper.getStartAndEndDate(kpiRequest);
		LocalDateTime extendedStartDate =
				dateRange.getStartDate().atStartOfDay().minusDays(REWORK_DAYS_AGO);
		LocalDateTime endDateTime = dateRange.getEndDate().atTime(23, 59, 59);

		CustomDateRange extendedDateRange = new CustomDateRange();
		extendedDateRange.setStartDate(extendedStartDate.toLocalDate());
		extendedDateRange.setEndDate(endDateTime.toLocalDate());

		ObjectId projectBasicConfigId =
				leafNodeList.get(0).getProjectFilter().getBasicProjectConfigId();

		List<ScmCommits> commits =
				scmKpiHelperService.getCommitDetails(projectBasicConfigId, extendedDateRange);

		resultMap.put(COMMITS, commits);
		resultMap.put(ASSIGNEE_SET, getScmUsersFromBaseClass());
		return resultMap;
	}

	@Override
	public Double calculateKPIMetrics(Map<String, Object> stringObjectMap) {
		return null;
	}

	@Override
	public Double calculateKpiValue(List<Double> valueList, String kpiId) {
		return calculateKpiValueForDouble(valueList, kpiId);
	}

	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI173(), KPICode.REWORK_RATE.getKpiId());
	}

	@SuppressWarnings("unchecked")
	private void calculateProjectKpiTrendData(
			KpiElement kpiElement,
			Map<String, Node> mapTmp,
			Node projectLeafNode,
			KpiRequest kpiRequest) {

		String requestTrackerId = getRequestTrackerId();
		LocalDateTime currentDate = DateUtil.getTodayTime();
		int dataPoints = kpiRequest.getXAxisDataPoints();
		String duration = kpiRequest.getDuration();

		List<Tool> scmTools =
				DeveloperKpiHelper.getScmToolsForProject(
						projectLeafNode, configHelperService, kpiHelperService);
		if (CollectionUtils.isEmpty(scmTools)) {
			log.error(
					"[BITBUCKET-AGGREGATED-VALUE]. No SCM tools found for project {}",
					projectLeafNode.getProjectFilter());
			return;
		}

		Map<String, Object> scmDataMap =
				fetchKPIDataFromDb(List.of(projectLeafNode), null, null, kpiRequest);
		List<ScmCommits> allCommits = (List<ScmCommits>) scmDataMap.get(COMMITS);
		Set<Assignee> assignees = new HashSet<>((Collection<Assignee>) scmDataMap.get(ASSIGNEE_SET));

		if (CollectionUtils.isEmpty(allCommits)) {
			log.error("[BITBUCKET-AGGREGATED-VALUE]. No commits found for project {}", projectLeafNode);
			return;
		}

		Map<String, List<DataCount>> kpiTrendDataByGroup = new LinkedHashMap<>();
		List<RepoToolValidationData> validationDataList = new ArrayList<>();

		Map<ScmCommits, LocalDateTime> commitTimestampMap =
				allCommits.stream()
						.filter(commit -> commit.getCommitTimestamp() != null)
						.collect(
								Collectors.toMap(
										commit -> commit,
										commit -> DateUtil.convertMillisToLocalDateTime(commit.getCommitTimestamp())));

		for (int i = 0; i < dataPoints; i++) {
			CustomDateRange periodRange =
					KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration);
			String dateLabel = KpiHelperService.getDateRange(periodRange, duration);

			// Get commits for the extended period (including reference period)
			LocalDateTime referenceStartDate = periodRange.getStartDateTime().minusDays(REWORK_DAYS_AGO);
			final CustomDateRange extendedDateRange = new CustomDateRange();
			extendedDateRange.setStartDateTime(referenceStartDate);
			extendedDateRange.setEndDateTime(periodRange.getEndDateTime());

			List<ScmCommits> periodCommits =
					commitTimestampMap.entrySet().stream()
							.filter(
									entry ->
											DateUtil.isWithinDateTimeRange(
													entry.getValue(),
													extendedDateRange.getStartDateTime(),
													extendedDateRange.getEndDateTime()))
							.map(Map.Entry::getKey)
							.collect(Collectors.toList());

			for (Tool tool : scmTools) {
				ToolDataContext toolContext =
						ToolDataContext.builder()
								.tool(tool)
								.commits(periodCommits)
								.assignees(assignees)
								.dateLabel(dateLabel)
								.projectName(projectLeafNode.getProjectFilter().getName())
								.periodRange(periodRange)
								.kpiTrendDataByGroup(kpiTrendDataByGroup)
								.validationDataList(validationDataList)
								.commitTimestampMap(commitTimestampMap)
								.build();

				processToolData(toolContext);
			}

			currentDate = DeveloperKpiHelper.getNextRangeDate(duration, currentDate);
		}

		mapTmp.get(projectLeafNode.getId()).setValue(kpiTrendDataByGroup);
		populateExcelData(requestTrackerId, validationDataList, kpiElement);
	}

	private void processToolData(ToolDataContext toolContext) {
		if (!DeveloperKpiHelper.isValidTool(toolContext.tool())) {
			return;
		}

		String branchName = getBranchSubFilter(toolContext.tool(), toolContext.projectName());
		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;

		List<ScmCommits> commitsForBranch =
				DeveloperKpiHelper.filterCommitsForBranch(toolContext.commits(), toolContext.tool());

		List<ScmCommits> nonMergeCommits =
				commitsForBranch.stream()
						.filter(commit -> !Boolean.TRUE.equals(commit.getIsMergeCommit()))
						.collect(Collectors.toList());

		Double overallReworkRate =
				calculateReworkRateForPeriod(
						nonMergeCommits, toolContext.periodRange(), toolContext.commitTimestampMap());

		DeveloperKpiHelper.setDataCount(
				toolContext.projectName(),
				toolContext.dateLabel(),
				overallKpiGroup,
				overallReworkRate,
				Map.of(),
				toolContext.kpiTrendDataByGroup());

		Map<String, List<ScmCommits>> userWiseCommits =
				DeveloperKpiHelper.groupCommitsByUser(nonMergeCommits);

		toolContext
				.validationDataList()
				.addAll(prepareUserValidationData(userWiseCommits, toolContext));
	}

	private List<RepoToolValidationData> prepareUserValidationData(
			Map<String, List<ScmCommits>> userWiseCommits, ToolDataContext toolContext) {

		return userWiseCommits.entrySet().stream()
				.filter(
						entry ->
								hasCommitsInPeriod(
										entry.getValue(), toolContext.periodRange(), toolContext.commitTimestampMap()))
				.map(
						entry -> {
							String userEmail = entry.getKey();
							List<ScmCommits> userCommits = entry.getValue();

							String developerName =
									DeveloperKpiHelper.getDeveloperName(userEmail, toolContext.assignees());
							Double userReworkRate =
									calculateReworkRateForPeriod(
											userCommits, toolContext.periodRange(), toolContext.commitTimestampMap());

							String userKpiGroup =
									getBranchSubFilter(toolContext.tool(), toolContext.projectName())
											+ "#"
											+ developerName;
							DeveloperKpiHelper.setDataCount(
									toolContext.projectName(),
									toolContext.dateLabel(),
									userKpiGroup,
									userReworkRate,
									Map.of(),
									toolContext.kpiTrendDataByGroup());

							return createValidationData(
									toolContext.projectName(),
									toolContext.tool(),
									developerName,
									toolContext.dateLabel(),
									userReworkRate);
						})
				.collect(Collectors.toList());
	}

	/**
	 * Core rework calculation logic
	 *
	 * @param commits All commits (reference + analysis period)
	 * @param periodRange Current analysis period
	 * @param commitTimestampMap Map of commit timestamps
	 * @return Rework rate percentage (higher = more rework)
	 */
	private Double calculateReworkRateForPeriod(
			List<ScmCommits> commits,
			CustomDateRange periodRange,
			Map<ScmCommits, LocalDateTime> commitTimestampMap) {
		if (CollectionUtils.isEmpty(commits)) {
			return 0.0;
		}

		LocalDateTime periodStart = periodRange.getStartDateTime();

		Map<Boolean, List<ScmCommits>> partitionedCommits =
				commits.stream()
						.filter(commitTimestampMap::containsKey)
						.collect(
								Collectors.partitioningBy(
										commit -> commitTimestampMap.get(commit).isBefore(periodStart)));

		List<ScmCommits> referenceCommits = partitionedCommits.get(true);
		List<ScmCommits> analysisCommits = partitionedCommits.get(false);

		// Step 2: Build reference pool (all lines changed in past 21 days)
		Map<String, Set<Integer>> referencePool = createReferencePool(referenceCommits);

		// Step 3: Calculate how many current changes are rework
		return calculateReworkPercentage(analysisCommits, referencePool);
	}

	/**
	 * Creates a map of files to line numbers that were changed in the reference period
	 *
	 * @param referenceCommits Commits from the past 21 days
	 * @return Map of file path to set of changed line numbers
	 */
	private Map<String, Set<Integer>> createReferencePool(List<ScmCommits> referenceCommits) {
		Map<String, Set<Integer>> referencePool = new HashMap<>();

		if (CollectionUtils.isEmpty(referenceCommits)) {
			return referencePool;
		}

		referenceCommits.stream()
				.filter(commit -> CollectionUtils.isNotEmpty(commit.getFileChanges()))
				.flatMap(commit -> commit.getFileChanges().stream())
				.filter(this::isValidFileChange)
				.forEach(
						fileChange ->
								referencePool
										.computeIfAbsent(fileChange.getFilePath(), k -> new HashSet<>())
										.addAll(fileChange.getChangedLineNumbers()));

		return referencePool;
	}

	/**
	 * Calculates the rework percentage for analysis period commits
	 *
	 * @param commits Analysis period commits
	 * @param referencePool Lines changed in reference period
	 * @return Percentage (higher = more rework)
	 */
	private Double calculateReworkPercentage(
			List<ScmCommits> commits, Map<String, Set<Integer>> referencePool) {
		if (CollectionUtils.isEmpty(commits)) {
			return 0.0;
		}

		ReworkCalculation calculation = new ReworkCalculation();

		commits.stream()
				.filter(commit -> CollectionUtils.isNotEmpty(commit.getFileChanges()))
				.flatMap(commit -> commit.getFileChanges().stream())
				.filter(this::isValidFileChange)
				.forEach(fileChange -> processFileChange(fileChange, referencePool, calculation));

		return calculation.getPercentage();
	}

	private boolean isValidFileChange(ScmCommits.FileChange fileChange) {
		return fileChange.getFilePath() != null
				&& CollectionUtils.isNotEmpty(fileChange.getChangedLineNumbers());
	}

	/**
	 * Processes a single file change to count rework
	 *
	 * @param fileChange Current file change
	 * @param referencePool Reference pool of past changes
	 * @param calculation Rework calculation tracker
	 */
	private void processFileChange(
			ScmCommits.FileChange fileChange,
			Map<String, Set<Integer>> referencePool,
			ReworkCalculation calculation) {
		String filePath = fileChange.getFilePath();
		List<Integer> changedLines = fileChange.getChangedLineNumbers();
		Set<Integer> referenceLines = referencePool.get(filePath);

		int totalChanges = changedLines.size();
		calculation.addTotalChanges(totalChanges);

		if (CollectionUtils.isNotEmpty(referenceLines)) {
			Set<Integer> changedLineSet = new HashSet<>(changedLines);
			changedLineSet.retainAll(referenceLines);
			int reworkCount = changedLineSet.size();

			calculation.addRework(reworkCount);

			// Add current changes to reference pool for cumulative tracking
			referenceLines.addAll(changedLines);
		} else {
			// New file - no rework, add to reference pool
			referencePool.put(filePath, new HashSet<>(changedLines));
		}
	}

	private boolean hasCommitsInPeriod(
			List<ScmCommits> commits,
			CustomDateRange periodRange,
			Map<ScmCommits, LocalDateTime> commitTimestampCache) {
		LocalDateTime periodStart = periodRange.getStartDateTime();
		return commits.stream()
				.filter(commitTimestampCache::containsKey)
				.anyMatch(commit -> !commitTimestampCache.get(commit).isBefore(periodStart));
	}

	private RepoToolValidationData createValidationData(
			String projectName, Tool tool, String developerName, String dateLabel, Double reworkRate) {
		RepoToolValidationData validationData = new RepoToolValidationData();
		validationData.setProjectName(projectName);
		validationData.setBranchName(tool.getBranch());
		validationData.setRepoUrl(tool.getRepositoryName());
		validationData.setDeveloperName(developerName);
		validationData.setDate(dateLabel);
		validationData.setReworkRate(reworkRate);
		return validationData;
	}

	private void populateExcelData(
			String requestTrackerId,
			List<RepoToolValidationData> validationDataList,
			KpiElement kpiElement) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			List<KPIExcelData> excelData = new ArrayList<>();
			KPIExcelUtility.populateReworkRateExcelData(validationDataList, excelData);
			kpiElement.setExcelData(excelData);
			kpiElement.setExcelColumns(KPIExcelColumn.REWORK_RATE.getColumns());
		}
	}
}
