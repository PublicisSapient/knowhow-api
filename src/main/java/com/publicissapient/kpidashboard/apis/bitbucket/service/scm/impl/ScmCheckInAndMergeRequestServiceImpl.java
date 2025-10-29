/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
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
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.BitBucketKPIService;
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
import lombok.extern.slf4j.Slf4j;

/**
 * This service is used to calculate the SCM Check-in and Merge Request KPI.
 *
 * @author shunaray
 */
@Slf4j
@Service
@AllArgsConstructor
public class ScmCheckInAndMergeRequestServiceImpl
		extends BitBucketKPIService<Long, List<Object>, Map<String, Object>> {

	private static final String NO_CHECKIN = "No. of Check in";
	private static final String NO_MERGE = "No. of Merge Requests";
	private static final String ASSIGNEE_SET = "assigneeSet";
	private static final String COMMIT_LIST = "commitList";

	private final ConfigHelperService configHelperService;
	private final KpiHelperService kpiHelperService;

	/** {@inheritDoc} */
	@Override
	public String getQualifierType() {
		return KPICode.REPO_TOOL_CODE_COMMIT.name();
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
		calculateAggregatedValueMap(projectNode, nodeWiseKPIValue, KPICode.REPO_TOOL_CODE_COMMIT);

		Map<String, List<DataCount>> trendValuesMap =
				getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.REPO_TOOL_CODE_COMMIT);

		kpiElement.setTrendValueList(DeveloperKpiHelper.prepareDataCountGroups(trendValuesMap));
		return kpiElement;
	}

	/** {@inheritDoc} */
	@Override
	public Long calculateKPIMetrics(Map<String, Object> t) {
		return null;
	}

	/** {@inheritDoc} */
	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
		Map<String, Object> scmDataMap = new HashMap<>();

		scmDataMap.put(ASSIGNEE_SET, getScmUsersFromBaseClass());
		scmDataMap.put(COMMIT_LIST, getCommitsFromBaseClass());

		return scmDataMap;
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
				fieldMapping.getThresholdValueKPI157(), KPICode.REPO_TOOL_CODE_COMMIT.getKpiId());
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

		Map<String, Object> scmDataMap = fetchKPIDataFromDb(null, null, null, null);
		List<ScmCommits> allCommits = (List<ScmCommits>) scmDataMap.get(COMMIT_LIST);
		Set<Assignee> assignees = new HashSet<>((Collection<Assignee>) scmDataMap.get(ASSIGNEE_SET));

		if (CollectionUtils.isEmpty(allCommits)) {
			log.error("[BITBUCKET-AGGREGATED-VALUE]. No commits found for project {}", projectLeafNode);
			return;
		}

		Map<String, List<DataCount>> kpiTrendDataByGroup = new LinkedHashMap<>();
		List<RepoToolValidationData> validationDataList = new ArrayList<>();

		for (int i = 0; i < dataPoints; i++) {
			CustomDateRange periodRange =
					KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration);
			String dateLabel = KpiHelperService.getDateRange(periodRange, duration);

			List<ScmCommits> commitsInRange =
					DeveloperKpiHelper.filterCommitsByCommitTimeStamp(allCommits, periodRange);

			scmTools.forEach(
					tool ->
							processToolData(
									tool,
									commitsInRange,
									assignees,
									dateLabel,
									projectLeafNode.getProjectFilter().getName(),
									kpiTrendDataByGroup,
									validationDataList));

			currentDate = DeveloperKpiHelper.getNextRangeDate(duration, currentDate);
		}

		mapTmp.get(projectLeafNode.getId()).setValue(kpiTrendDataByGroup);
		populateExcelData(requestTrackerId, validationDataList, kpiElement);
	}

	private void processToolData(
			Tool tool,
			List<ScmCommits> commits,
			Set<Assignee> assignees,
			String dateLabel,
			String projectName,
			Map<String, List<DataCount>> kpiTrendDataByGroup,
			List<RepoToolValidationData> validationDataList) {

		if (!DeveloperKpiHelper.isValidTool(tool)) {
			return;
		}

		String branchName = getBranchSubFilter(tool, projectName);
		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;

		List<ScmCommits> commitsForBranch = DeveloperKpiHelper.filterCommitsForBranch(commits, tool);

		List<ScmCommits> mergeCommits =
				commitsForBranch.stream()
						.filter(commit -> Boolean.TRUE.equals(commit.getIsMergeCommit()))
						.collect(Collectors.toList());

		long totalCommits = commitsForBranch.size();
		long totalMergeRequests = mergeCommits.size();

		setDataCount(
				projectName,
				dateLabel,
				overallKpiGroup,
				totalCommits,
				totalMergeRequests,
				kpiTrendDataByGroup);

		Map<String, List<ScmCommits>> userWiseAllCommits =
				DeveloperKpiHelper.groupCommitsByUser(commitsForBranch);
		Map<String, List<ScmCommits>> userWiseMergeCommits =
				DeveloperKpiHelper.groupCommitsByUser(mergeCommits);

		Set<String> allUsers = userWiseAllCommits.keySet();

		allUsers.forEach(
				userEmail -> {
					String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);
					List<ScmCommits> userCommits =
							userWiseAllCommits.getOrDefault(userEmail, Collections.emptyList());
					List<ScmCommits> userMergeCommits =
							userWiseMergeCommits.getOrDefault(userEmail, Collections.emptyList());
					long commitCount = userCommits.size();
					long mrCount = userMergeCommits.size();
					String userKpiGroup = branchName + "#" + developerName;
					setDataCount(
							projectName, dateLabel, userKpiGroup, commitCount, mrCount, kpiTrendDataByGroup);
					validationDataList.add(
							createValidationData(
									projectName, tool, developerName, dateLabel, commitCount, mrCount));
				});
	}

	private void populateExcelData(
			String requestTrackerId,
			List<RepoToolValidationData> validationDataList,
			KpiElement kpiElement) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			List<KPIExcelData> excelData = new ArrayList<>();
			KPIExcelUtility.populateCodeCommit(validationDataList, excelData);
			kpiElement.setExcelData(excelData);
			kpiElement.setExcelColumns(KPIExcelColumn.REPO_TOOL_CODE_COMMIT.getColumns());
		}
	}

	private void setDataCount(
			String projectName,
			String week,
			String kpiGroup,
			Long commitValue,
			Long mrCount,
			Map<String, List<DataCount>> dataCountMap) {

		List<DataCount> dataCounts = dataCountMap.computeIfAbsent(kpiGroup, k -> new ArrayList<>());

		DataCount dataCount =
				dataCounts.stream()
						.filter(dc -> dc.getDate().equals(week))
						.findFirst()
						.orElseGet(
								() -> {
									DataCount newDataCount = new DataCount();
									newDataCount.setSProjectName(projectName);
									newDataCount.setDate(week);
									newDataCount.setKpiGroup(kpiGroup);
									newDataCount.setValue(0L);
									newDataCount.setLineValue(0L);
									dataCounts.add(newDataCount);
									return newDataCount;
								});

		dataCount.setValue(((Number) dataCount.getValue()).longValue() + commitValue);
		dataCount.setLineValue(((Number) dataCount.getLineValue()).longValue() + mrCount);

		Map<String, Object> hoverValues = new HashMap<>();
		hoverValues.put(NO_CHECKIN, dataCount.getValue());
		hoverValues.put(NO_MERGE, dataCount.getLineValue());
		dataCount.setHoverValue(hoverValues);
	}

	private RepoToolValidationData createValidationData(
			String projectName,
			Tool tool,
			String developerName,
			String dateLabel,
			long commitCount,
			long mrCount) {
		RepoToolValidationData validationData = new RepoToolValidationData();
		validationData.setProjectName(projectName);
		validationData.setBranchName(tool.getBranch());
		validationData.setRepoUrl(
				tool.getRepositoryName() != null ? tool.getRepositoryName() : tool.getRepoSlug());
		validationData.setDeveloperName(developerName);
		validationData.setDate(dateLabel);
		validationData.setCommitCount(commitCount);
		validationData.setMrCount(mrCount);
		return validationData;
	}
}
