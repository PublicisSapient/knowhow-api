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
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * SCM Rework Rate Service Implementation Calculates rework rate using SCM
 * commits from database
 */
@Slf4j
@Service
@AllArgsConstructor
public class ScmReworkRateServiceImpl extends BitBucketKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String COMMITS = "commits";
	private static final String ASSIGNEE_SET = "assigneeSet";
	private static final int REWORK_DAYS_AGO = 21;

	private final KpiHelperService kpiHelperService;
	private final ScmKpiHelperService scmKpiHelperService;
	private final ConfigHelperService configHelperService;

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
			double score = (double) reworkChanges / totalChanges;
			return (1 - score) * 100;
		}
	}

	@Builder
	private record ToolDataContext(Tool tool, List<ScmCommits> commits, Set<Assignee> assignees, String dateLabel,
			String projectName, CustomDateRange periodRange, Map<String, List<DataCount>> kpiTrendDataByGroup,
			List<RepoToolValidationData> validationDataList) {
	}

	@Override
	public String getQualifierType() {
		return KPICode.REWORK_RATE.name();
	}

	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node projectNode)
			throws ApplicationException {
		Map<String, Node> nodeMap = Map.of(projectNode.getId(), projectNode);
		calculateProjectKpiTrendData(kpiElement, nodeMap, projectNode, kpiRequest);

		log.debug("[PROJECT-WISE][{}]. Values of leaf node after KPI calculation {}", kpiRequest.getRequestTrackerId(),
				projectNode);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValueMap(projectNode, nodeWiseKPIValue, KPICode.REWORK_RATE);

		Map<String, List<DataCount>> trendValuesMap = getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue,
				KPICode.REWORK_RATE);

		kpiElement.setTrendValueList(DeveloperKpiHelper.prepareDataCountGroups(trendValuesMap));
		return kpiElement;
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(List<Node> leafNodeList, String startDate, String endDate,
			KpiRequest kpiRequest) {
		Map<String, Object> resultMap = new HashMap<>();

		CustomDateRange dateRange = KpiDataHelper.getStartAndEndDate(kpiRequest);
		LocalDateTime extendedStartDate = dateRange.getStartDate().atStartOfDay().minusDays(REWORK_DAYS_AGO);
		LocalDateTime endDateTime = dateRange.getEndDate().atTime(23, 59, 59);

		ObjectId projectBasicConfigId = leafNodeList.get(0).getProjectFilter().getBasicProjectConfigId();
		CustomDateRange extendedDateRange = new CustomDateRange(extendedStartDate, endDateTime);

		List<ScmCommits> commits = scmKpiHelperService.getCommitDetails(projectBasicConfigId, extendedDateRange);

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
		return calculateThresholdValue(fieldMapping.getThresholdValueKPI173(), KPICode.REWORK_RATE.getKpiId());
	}

	@SuppressWarnings("unchecked")
	private void calculateProjectKpiTrendData(KpiElement kpiElement, Map<String, Node> mapTmp, Node projectLeafNode,
			KpiRequest kpiRequest) {

		String requestTrackerId = getRequestTrackerId();
		LocalDateTime currentDate = DateUtil.getTodayTime();
		int dataPoints = kpiRequest.getXAxisDataPoints();
		String duration = kpiRequest.getDuration();

		List<Tool> scmTools = DeveloperKpiHelper.getScmToolsForProject(projectLeafNode, configHelperService,
				kpiHelperService);
		if (CollectionUtils.isEmpty(scmTools)) {
			log.error("[BITBUCKET-AGGREGATED-VALUE]. No SCM tools found for project {}",
					projectLeafNode.getProjectFilter());
			return;
		}

		Map<String, Object> scmDataMap = fetchKPIDataFromDb(List.of(projectLeafNode), null, null, kpiRequest);
		List<ScmCommits> allCommits = (List<ScmCommits>) scmDataMap.get(COMMITS);
		Set<Assignee> assignees = new HashSet<>((Collection<Assignee>) scmDataMap.get(ASSIGNEE_SET));

		if (CollectionUtils.isEmpty(allCommits)) {
			log.error("[BITBUCKET-AGGREGATED-VALUE]. No commits found for project {}", projectLeafNode);
			return;
		}

		Map<String, List<DataCount>> kpiTrendDataByGroup = new LinkedHashMap<>();
		List<RepoToolValidationData> validationDataList = new ArrayList<>();

		for (int i = 0; i < dataPoints; i++) {
			CustomDateRange periodRange = KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration);
			String dateLabel = KpiHelperService.getDateRange(periodRange, duration);

			// Get commits for the extended period (including reference period)
			LocalDateTime referenceStartDate = periodRange.getStartDateTime().minusDays(REWORK_DAYS_AGO);
			List<ScmCommits> periodCommits = DeveloperKpiHelper.filterCommitsByCommitTimeStamp(allCommits,
					new CustomDateRange(referenceStartDate, periodRange.getEndDateTime()));

			// CHANGE: Create context object to reduce parameters
			for (Tool tool : scmTools) {
				ToolDataContext context = ToolDataContext.builder().tool(tool).commits(periodCommits)
						.assignees(assignees).dateLabel(dateLabel)
						.projectName(projectLeafNode.getProjectFilter().getName()).periodRange(periodRange)
						.kpiTrendDataByGroup(kpiTrendDataByGroup).validationDataList(validationDataList).build();

				processToolData(context);
			}

			currentDate = DeveloperKpiHelper.getNextRangeDate(duration, currentDate);
		}

		mapTmp.get(projectLeafNode.getId()).setValue(kpiTrendDataByGroup);
		populateExcelData(requestTrackerId, validationDataList, kpiElement);
	}

	private void processToolData(ToolDataContext context) {
		if (!DeveloperKpiHelper.isValidTool(context.tool())) {
			return;
		}

		String branchName = getBranchSubFilter(context.tool(), context.projectName());
		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;

		List<ScmCommits> commitsForBranch = DeveloperKpiHelper.filterCommitsForBranch(context.commits(),
				context.tool());

		List<ScmCommits> nonMergeCommits = commitsForBranch.stream()
				.filter(commit -> !Boolean.TRUE.equals(commit.getIsMergeCommit())).collect(Collectors.toList());

		Double overallReworkRate = calculateReworkRateForPeriod(nonMergeCommits, context.periodRange());
		DeveloperKpiHelper.setDataCount(context.projectName(), context.dateLabel(), overallKpiGroup, overallReworkRate,
				Map.of(), context.kpiTrendDataByGroup());

		// Process user-wise rework rates
		Map<String, List<ScmCommits>> userWiseCommits = DeveloperKpiHelper.groupCommitsByUser(nonMergeCommits);

		context.validationDataList()
				.addAll(prepareUserValidationData(userWiseCommits, context.assignees(), context.tool(),
						context.projectName(), context.dateLabel(), context.kpiTrendDataByGroup(),
						context.periodRange()));
	}

	private List<RepoToolValidationData> prepareUserValidationData(Map<String, List<ScmCommits>> userWiseCommits,
			Set<Assignee> assignees, Tool tool, String projectName, String dateLabel,
			Map<String, List<DataCount>> kpiTrendDataByGroup, CustomDateRange periodRange) {

		return userWiseCommits.entrySet().stream().filter(entry -> hasCommitsInPeriod(entry.getValue(), periodRange))
				.map(entry -> {
					String userEmail = entry.getKey();
					List<ScmCommits> userCommits = entry.getValue();

					String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);
					Double userReworkRate = calculateReworkRateForPeriod(userCommits, periodRange);

					String userKpiGroup = getBranchSubFilter(tool, projectName) + "#" + developerName;
					DeveloperKpiHelper.setDataCount(projectName, dateLabel, userKpiGroup, userReworkRate, Map.of(),
							kpiTrendDataByGroup);

					return createValidationData(projectName, tool, developerName, dateLabel, userReworkRate);
				}).collect(Collectors.toList());
	}

	private Double calculateReworkRateForPeriod(List<ScmCommits> commits, CustomDateRange periodRange) {
		if (CollectionUtils.isEmpty(commits)) {
			return 0.0;
		}

		LocalDateTime periodStart = periodRange.getStartDateTime();

		Map<Boolean, List<ScmCommits>> partitionedCommits = commits.stream().collect(Collectors.partitioningBy(
				commit -> DateUtil.convertMillisToLocalDateTime(commit.getCommitTimestamp()).isBefore(periodStart)));

		List<ScmCommits> referenceCommits = partitionedCommits.get(true);
		List<ScmCommits> analysisCommits = partitionedCommits.get(false);

		Map<String, Set<Integer>> referencePool = createReferencePool(referenceCommits);

		return calculateReworkPercentage(analysisCommits, referencePool);
	}

	private Map<String, Set<Integer>> createReferencePool(List<ScmCommits> referenceCommits) {
		Map<String, Set<Integer>> referencePool = new HashMap<>();

		if (CollectionUtils.isEmpty(referenceCommits)) {
			return referencePool;
		}

		referenceCommits.stream().filter(commit -> CollectionUtils.isNotEmpty(commit.getFileChanges()))
				.flatMap(commit -> commit.getFileChanges().stream())
				.filter(fileChange -> fileChange.getFilePath() != null
						&& CollectionUtils.isNotEmpty(fileChange.getChangedLineNumbers()))
				.forEach(fileChange -> referencePool.computeIfAbsent(fileChange.getFilePath(), k -> new HashSet<>())
						.addAll(fileChange.getChangedLineNumbers()));

		return referencePool;
	}

	private Double calculateReworkPercentage(List<ScmCommits> commits, Map<String, Set<Integer>> referencePool) {
		if (CollectionUtils.isEmpty(commits)) {
			return 0.0;
		}

		ReworkCalculation calculation = new ReworkCalculation();

		commits.stream().filter(commit -> CollectionUtils.isNotEmpty(commit.getFileChanges()))
				.flatMap(commit -> commit.getFileChanges().stream()).filter(this::isValidFileChange)
				.forEach(fileChange -> processFileChange(fileChange, referencePool, calculation));

		return calculation.getPercentage();
	}

	private boolean isValidFileChange(ScmCommits.FileChange fileChange) {
		return fileChange.getFilePath() != null && CollectionUtils.isNotEmpty(fileChange.getChangedLineNumbers());
	}

	private void processFileChange(ScmCommits.FileChange fileChange, Map<String, Set<Integer>> referencePool,
			ReworkCalculation calculation) {
		String filePath = fileChange.getFilePath();
		List<Integer> changedLines = fileChange.getChangedLineNumbers();
		Set<Integer> referenceLines = referencePool.get(filePath);

		if (referenceLines != null) {
			// Count rework (intersection of changed lines with reference pool)
			Set<Integer> changedLineSet = new HashSet<>(changedLines);
			long reworkCount = referenceLines.stream().filter(changedLineSet::contains).count();
			calculation.addRework((int) reworkCount);

			// Add current changes to reference pool for cumulative tracking
			referenceLines.addAll(changedLines);
		} else {
			// New file, add to reference pool
			referencePool.put(filePath, new HashSet<>(changedLines));
		}

		calculation.addTotalChanges(changedLines.size());
	}

	private boolean hasCommitsInPeriod(List<ScmCommits> commits, CustomDateRange periodRange) {
		LocalDateTime periodStart = periodRange.getStartDateTime();
		return commits.stream().anyMatch(
				commit -> !DateUtil.convertMillisToLocalDateTime(commit.getCommitTimestamp()).isBefore(periodStart));
	}

	// CHANGE: Simplified validation data creation
	private RepoToolValidationData createValidationData(String projectName, Tool tool, String developerName,
			String dateLabel, Double reworkRate) {
		RepoToolValidationData validationData = new RepoToolValidationData();
		validationData.setProjectName(projectName);
		validationData.setBranchName(tool.getBranch());
		validationData.setRepoUrl(tool.getRepositoryName());
		validationData.setDeveloperName(developerName);
		validationData.setDate(dateLabel);
		validationData.setReworkRate(reworkRate);
		return validationData;
	}

	private void populateExcelData(String requestTrackerId, List<RepoToolValidationData> validationDataList,
			KpiElement kpiElement) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			List<KPIExcelData> excelData = new ArrayList<>();
			KPIExcelUtility.populateReworkRateExcelData(validationDataList, excelData);
			kpiElement.setExcelData(excelData);
			kpiElement.setExcelColumns(KPIExcelColumn.REWORK_RATE.getColumns());
		}
	}
}