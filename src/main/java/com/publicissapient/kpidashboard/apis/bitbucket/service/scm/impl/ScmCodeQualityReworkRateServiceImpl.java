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
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.BitBucketKPIService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.model.*;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
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
 * @author valsa anil
 */
@Slf4j
@Service
@AllArgsConstructor
public class ScmCodeQualityReworkRateServiceImpl
		extends BitBucketKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String COMMITS = "commits";
	private static final String ASSIGNEE_SET = "assigneeSet";
	private static final int REWORK_DAYS_AGO = 21;

	private final KpiHelperService kpiHelperService;
	private final ScmKpiHelperService scmKpiHelperService;
	private final ConfigHelperService configHelperService;

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

	/**
	 * Returns the qualifier type for this KPI service. Currently returns null as this service doesn't
	 * require a specific qualifier.
	 *
	 * @return null
	 */
	@Override
	public String getQualifierType() {
		return null;
	}

	/**
	 * Main entry point for calculating rework rate KPI data. Processes project node to calculate
	 * rework percentages for different filters. Returns list of CodeQualityMetricsRes objects with
	 * filter1, filter2, and percentage values.
	 *
	 * @param kpiRequest the KPI request containing filters and parameters
	 * @param kpiElement the KPI element to populate with results
	 * @param projectNode the project node for which to calculate metrics
	 * @return populated KpiElement with List<CodeQualityMetricsRes> in trendValueList
	 * @throws ApplicationException if error occurs during processing
	 */
	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node projectNode)
			throws ApplicationException {

		Map<String, MetricsHolder> reworkMap =
				(Map<String, MetricsHolder>) kpiElement.getTrendValueList();
		calculateProjectKpiPercentage(projectNode, kpiRequest, reworkMap);

		log.debug(
				"[PROJECT-WISE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				projectNode);

		kpiElement.setTrendValueList(reworkMap);
		return kpiElement;
	}

	/**
	 * Fetches SCM commit data from database for rework rate calculation. Extends the date range by 21
	 * days to include reference period data.
	 *
	 * @param leafNodeList list of leaf nodes to process
	 * @param startDate start date for data retrieval (not used, calculated from kpiRequest)
	 * @param endDate end date for data retrieval (not used, calculated from kpiRequest)
	 * @param kpiRequest KPI request containing date range parameters
	 * @return map containing commits and assignee data
	 */
	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
		Map<String, Object> resultMap = new HashMap<>();

		resultMap.put(COMMITS, getCommitsFromBaseClass());
		resultMap.put(ASSIGNEE_SET, getScmUsersFromBaseClass());
		return resultMap;
	}

	/**
	 * Calculates KPI metrics from provided data map. Not implemented for this service as calculations
	 * are done in other methods.
	 *
	 * @param stringObjectMap map containing metric calculation data
	 * @return null as this method is not used
	 */
	@Override
	public Double calculateKPIMetrics(Map<String, Object> stringObjectMap) {
		return null;
	}

	/**
	 * Calculates aggregated KPI value from list of values.
	 *
	 * @param valueList list of double values to aggregate
	 * @param kpiId KPI identifier
	 * @return aggregated KPI value
	 */
	@Override
	public Double calculateKpiValue(List<Double> valueList, String kpiId) {
		return calculateKpiValueForDouble(valueList, kpiId);
	}

	/**
	 * Calculates threshold value for rework rate KPI from field mapping.
	 *
	 * @param fieldMapping field mapping containing threshold configuration
	 * @return threshold value for rework rate KPI
	 */
	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI173(), KPICode.REWORK_RATE.getKpiId());
	}

	/**
	 * Calculates rework rate percentages for a project across multiple time periods. Processes each
	 * data point in the requested time range and calculates rework metrics for both overall project
	 * and individual developers.
	 *
	 * @param projectLeafNode the project node to process
	 * @param kpiRequest KPI request containing duration and data points
	 * @param reworkMap map to store calculated rework metrics by filter key
	 */
	@SuppressWarnings("unchecked")
	private void calculateProjectKpiPercentage(
			Node projectLeafNode, KpiRequest kpiRequest, Map<String, MetricsHolder> reworkMap) {

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

				processToolData(toolContext, reworkMap);
			}

			currentDate = DeveloperKpiHelper.getNextRangeDate(duration, currentDate);
		}
	}

	/**
	 * Processes SCM tool data to calculate rework metrics. Filters commits by branch, excludes merge
	 * commits, and calculates rework for both overall metrics and individual developers.
	 *
	 * @param toolContext context containing tool data and configuration
	 * @param reworkMap map to store calculated rework metrics
	 */
	private void processToolData(ToolDataContext toolContext, Map<String, MetricsHolder> reworkMap) {
		if (!DeveloperKpiHelper.isValidTool(toolContext.tool())) {
			return;
		}

		String branchName = getBranchSubFilter(toolContext.tool(), toolContext.projectName());

		List<ScmCommits> commitsForBranch =
				DeveloperKpiHelper.filterCommitsForBranch(toolContext.commits(), toolContext.tool());

		List<ScmCommits> nonMergeCommits =
				commitsForBranch.stream()
						.filter(commit -> !Boolean.TRUE.equals(commit.getIsMergeCommit()))
						.collect(Collectors.toList());

		calculateReworkRateForPeriod(
				nonMergeCommits,
				toolContext.periodRange(),
				toolContext.commitTimestampMap(),
				reworkMap,
				branchName + "#" + Constant.AGGREGATED_VALUE);

		Map<String, List<ScmCommits>> userWiseCommits =
				DeveloperKpiHelper.groupCommitsByUser(nonMergeCommits);

		prepareUserValidationData(userWiseCommits, toolContext, reworkMap, branchName);
	}

	/**
	 * Prepares user-specific validation data by calculating rework metrics for each developer who has
	 * commits in the analysis period.
	 *
	 * @param userWiseCommits map of commits grouped by user email
	 * @param toolContext context containing tool data and configuration
	 * @param reworkMap map to store calculated rework metrics
	 * @param branchName branch name for filter key construction
	 */
	private void prepareUserValidationData(
			Map<String, List<ScmCommits>> userWiseCommits,
			ToolDataContext toolContext,
			Map<String, MetricsHolder> reworkMap,
			String branchName) {

		for (Map.Entry<String, List<ScmCommits>> entry : userWiseCommits.entrySet()) {
			String userEmail = entry.getKey();
			List<ScmCommits> userCommits = entry.getValue();
			String developerName =
					DeveloperKpiHelper.getDeveloperName(userEmail, toolContext.assignees());

			if (hasCommitsInPeriod(
					userCommits, toolContext.periodRange(), toolContext.commitTimestampMap())) {

				calculateReworkRateForPeriod(
						userCommits,
						toolContext.periodRange(),
						toolContext.commitTimestampMap(),
						reworkMap,
						branchName + "#" + developerName);
			}
		}
	}

	/**
	 * Core rework calculation logic for a specific time period. Partitions commits into reference
	 * period (past 21 days) and analysis period, builds reference pool from past changes, and
	 * calculates rework metrics.
	 *
	 * @param commits all commits (reference + analysis period)
	 * @param periodRange current analysis period
	 * @param commitTimestampMap map of commit timestamps
	 * @param reworkMap map to store calculated rework metrics
	 * @param kpiGroup filter key for grouping results
	 */
	private void calculateReworkRateForPeriod(
			List<ScmCommits> commits,
			CustomDateRange periodRange,
			Map<ScmCommits, LocalDateTime> commitTimestampMap,
			Map<String, MetricsHolder> reworkMap,
			String kpiGroup) {

		if (commits == null || commits.isEmpty()) {
			return;
		}

		LocalDateTime periodStart = periodRange.getStartDateTime();

		// Further partition by referenceCommits and analysisCommits
		Map<Boolean, List<ScmCommits>> referenceAndAnalysisPartition =
				commits.stream()
						.filter(commitTimestampMap::containsKey)
						.collect(
								Collectors.partitioningBy(
										commit -> commitTimestampMap.get(commit).isBefore(periodStart)));

		List<ScmCommits> referenceCommits = referenceAndAnalysisPartition.get(true);
		List<ScmCommits> analysisCommits = referenceAndAnalysisPartition.get(false);

		// Step 2: Build reference pool (all lines changed in past 21 days)
		Map<String, Set<Integer>> referencePool = createReferencePool(referenceCommits);

		// Step 3: Calculate how many current changes are rework
		calculateReworkMetrics(analysisCommits, referencePool, reworkMap, kpiGroup);
	}

	/**
	 * Creates a reference pool of all lines changed in the past 21 days. This pool is used to
	 * identify rework in the current analysis period.
	 *
	 * @param referenceCommits commits from the past 21 days
	 * @return map of file path to set of changed line numbers
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
	 * Calculates rework metrics for analysis period commits. Processes each file change to count
	 * total changes and rework against reference pool.
	 *
	 * @param commits analysis period commits
	 * @param referencePool lines changed in reference period
	 * @param reworkMap map to store calculated rework metrics
	 * @param kpiGroup filter key for grouping results
	 */
	private void calculateReworkMetrics(
			List<ScmCommits> commits,
			Map<String, Set<Integer>> referencePool,
			Map<String, MetricsHolder> reworkMap,
			String kpiGroup) {
		if (CollectionUtils.isEmpty(commits)) {
			return;
		}

		commits.stream()
				.filter(commit -> CollectionUtils.isNotEmpty(commit.getFileChanges()))
				.flatMap(commit -> commit.getFileChanges().stream())
				.filter(this::isValidFileChange)
				.forEach(fileChange -> processFileChange(fileChange, referencePool, reworkMap, kpiGroup));
	}

	/**
	 * Validates if a file change is valid for rework calculation. Checks for non-null file path and
	 * non-empty changed line numbers.
	 *
	 * @param fileChange the file change to validate
	 * @return true if file change is valid, false otherwise
	 */
	private boolean isValidFileChange(ScmCommits.FileChange fileChange) {
		return fileChange.getFilePath() != null
				&& CollectionUtils.isNotEmpty(fileChange.getChangedLineNumbers());
	}

	/**
	 * Processes a single file change to count rework against reference pool. Calculates total changes
	 * and identifies rework by comparing with reference lines. Note: Reference pool should not be
	 * modified during analysis period.
	 *
	 * @param fileChange current file change to process
	 * @param referencePool reference pool of past changes
	 * @param reworkMap map to store calculated rework metrics
	 * @param kpiGroup filter key for grouping results
	 */
	private void processFileChange(
			ScmCommits.FileChange fileChange,
			Map<String, Set<Integer>> referencePool,
			Map<String, MetricsHolder> reworkMap,
			String kpiGroup) {
		String filePath = fileChange.getFilePath();
		List<Integer> changedLines = fileChange.getChangedLineNumbers();
		Set<Integer> referenceLines = referencePool.get(filePath);

		int totalChanges = changedLines.size();
		MetricsHolder calculation = reworkMap.computeIfAbsent(kpiGroup, key -> new MetricsHolder());

		calculation.addTotalChanges(totalChanges);
		if (log.isInfoEnabled()) {
			log.info(" KpiGroup: {} ----> totalChanges: {}", kpiGroup, totalChanges);
		}

		if (CollectionUtils.isNotEmpty(referenceLines)) {
			Set<Integer> changedLineSet = new HashSet<>(changedLines);
			changedLineSet.retainAll(referenceLines);
			int reworkCount = changedLineSet.size();
			calculation.addRework(reworkCount);
			referenceLines.addAll(changedLines);
			if (log.isInfoEnabled()) {
				log.info(" KpiGroup: {} ----> reworkCount: {}", kpiGroup, reworkCount);
			}
		} else {
			// New file - no rework, add to reference pool
			referencePool.put(filePath, new HashSet<>(changedLines));
		}
	}

	/**
	 * Checks if any commits exist within the specified analysis period. Used to determine if a
	 * developer should be included in rework calculations.
	 *
	 * @param commits list of commits to check
	 * @param periodRange the analysis period date range
	 * @param commitTimestampCache cache of commit timestamps
	 * @return true if commits exist in period, false otherwise
	 */
	private boolean hasCommitsInPeriod(
			List<ScmCommits> commits,
			CustomDateRange periodRange,
			Map<ScmCommits, LocalDateTime> commitTimestampCache) {
		LocalDateTime periodStart = periodRange.getStartDateTime();
		return commits.stream()
				.filter(commitTimestampCache::containsKey)
				.anyMatch(commit -> !commitTimestampCache.get(commit).isBefore(periodStart));
	}
}
