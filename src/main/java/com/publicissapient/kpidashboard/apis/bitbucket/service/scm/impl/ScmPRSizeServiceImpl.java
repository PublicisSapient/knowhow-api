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
import java.util.*;
import java.util.stream.Collectors;

import com.publicissapient.kpidashboard.apis.bitbucket.service.BitBucketKPIService;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.model.*;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class ScmPRSizeServiceImpl extends BitBucketKPIService<Long, List<Object>, Map<String, Object>> {

	private static final String MR_COUNT = "No of PRs";
	private static final String ASSIGNEE_SET = "assigneeSet";
	private static final String MERGE_REQUEST_LIST = "mergeRequestList";

	private ConfigHelperService configHelperService;
	private KpiHelperService kpiHelperService;

	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node projectNode)
			throws ApplicationException {
		Map<String, Node> nodeMap = Map.of(projectNode.getId(), projectNode);
		projectWiseLeafNodeValue(kpiElement, nodeMap, projectNode, kpiRequest);

		log.debug("[PROJECT-WISE][{}]. Values of leaf node after KPI calculation {}", kpiRequest.getRequestTrackerId(),
				projectNode);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValueMap(projectNode, nodeWiseKPIValue, KPICode.PR_SIZE);

		Map<String, List<DataCount>> trendValuesMap = getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue,
				KPICode.PR_SIZE);
		kpiElement.setTrendValueList(DeveloperKpiHelper.prepareDataCountGroups(trendValuesMap));
		return kpiElement;
	}

	/**
	 * Populates KPI value to project leaf nodes. It also gives the trend analysis
	 * project wise.
	 *
	 * @param kpiElement
	 *            kpi element
	 * @param mapTmp
	 *            node map
	 * @param projectLeafNode
	 *            leaf node of project
	 * @param kpiRequest
	 *            kpi request
	 */
	@SuppressWarnings("unchecked")
	private void projectWiseLeafNodeValue(KpiElement kpiElement, Map<String, Node> mapTmp, Node projectLeafNode,
			KpiRequest kpiRequest) {
		CustomDateRange dateRange = KpiDataHelper.getStartAndEndDate(kpiRequest);
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

		Map<String, Object> resultmap = fetchKPIDataFromDb(List.of(projectLeafNode),
				dateRange.getStartDate().toString(), dateRange.getEndDate().toString(), kpiRequest);
		List<ScmMergeRequests> mergeRequests = (List<ScmMergeRequests>) resultmap.get(MERGE_REQUEST_LIST);
		Set<Assignee> assignees = new HashSet<>((Collection<Assignee>) resultmap.get(ASSIGNEE_SET));

		if (CollectionUtils.isEmpty(mergeRequests)) {
			log.error("[BITBUCKET-AGGREGATED-VALUE]. No merge requests found for project {}", projectLeafNode);
			return;
		}

		Map<String, List<DataCount>> aggregatedDataMap = new LinkedHashMap<>();
		List<RepoToolValidationData> validationDataList = new ArrayList<>();

		for (int i = 0; i < dataPoints; i++) {
			CustomDateRange weekRange = KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration);
			String dateLabel = KpiHelperService.getDateRange(weekRange, duration);
			List<ScmMergeRequests> mergedRequestsInRange = DeveloperKpiHelper
					.filterMergeRequestsByUpdateDate(mergeRequests, weekRange);

			scmTools.forEach(tool -> processToolData(tool, mergedRequestsInRange, assignees, aggregatedDataMap,
					validationDataList, dateLabel, projectLeafNode.getProjectFilter().getName()));

			currentDate = DeveloperKpiHelper.getNextRangeDate(duration, currentDate);
		}

		mapTmp.get(projectLeafNode.getId()).setValue(aggregatedDataMap);
		populateExcelData(requestTrackerId, validationDataList, kpiElement);
	}

	private void processToolData(Tool tool, List<ScmMergeRequests> mergeRequests, Set<Assignee> assignees,
			Map<String, List<DataCount>> aggregatedDataMap, List<RepoToolValidationData> validationDataList,
			String dateLabel, String projectName) {
		if (!DeveloperKpiHelper.isValidTool(tool)) {
			return;
		}

		String branchName = getBranchSubFilter(tool, projectName);
		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;

		List<ScmMergeRequests> mergeRequestsForBranch = DeveloperKpiHelper.filterMergeRequestsForBranch(mergeRequests,
				tool);

		long totalLinesChanged = mergeRequestsForBranch.stream().mapToLong(ScmMergeRequests::getLinesChanged).sum();
		long totalMergeRequests = mergeRequestsForBranch.size();

		DeveloperKpiHelper.setDataCount(projectName, dateLabel, overallKpiGroup, totalLinesChanged,
				Map.of(MR_COUNT, totalMergeRequests), aggregatedDataMap);

		// todo:: check when no link in dbref
		Map<String, List<ScmMergeRequests>> userWiseMergeRequests = DeveloperKpiHelper
				.groupMergeRequestsByUser(mergeRequestsForBranch);

		validationDataList.addAll(prepareUserValidationData(userWiseMergeRequests, assignees, tool, projectName,
				dateLabel, aggregatedDataMap));
	}

	private List<RepoToolValidationData> prepareUserValidationData(
			Map<String, List<ScmMergeRequests>> userWiseMergeRequests, Set<Assignee> assignees, Tool tool,
			String projectName, String dateLabel, Map<String, List<DataCount>> aggregatedDataMap) {
		return userWiseMergeRequests.entrySet().stream().map(entry -> {
			String userEmail = entry.getKey();
			List<ScmMergeRequests> userMergeRequests = entry.getValue();

			String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);
			long userLineChange = userMergeRequests.stream().mapToLong(ScmMergeRequests::getLinesChanged).sum();
			long userMrCount = userMergeRequests.size();

			String userKpiGroup = getBranchSubFilter(tool, projectName) + "#" + developerName;

			DeveloperKpiHelper.setDataCount(projectName, dateLabel, userKpiGroup, userLineChange,
					Map.of(MR_COUNT, userMrCount), aggregatedDataMap);

			return createValidationData(projectName, tool, developerName, dateLabel, userLineChange, userMrCount);
		}).collect(Collectors.toList());
	}

	private RepoToolValidationData createValidationData(String projectName, Tool tool, String developerName,
			String dateLabel, long prSize, long mrCount) {
		RepoToolValidationData validationData = new RepoToolValidationData();
		validationData.setProjectName(projectName);
		validationData.setBranchName(tool.getBranch());
		validationData.setRepoUrl(tool.getRepositoryName());
		validationData.setDeveloperName(developerName);
		validationData.setDate(dateLabel);
		validationData.setPrSize(prSize);
		validationData.setMrCount(mrCount);
		return validationData;
	}

	private void populateExcelData(String requestTrackerId, List<RepoToolValidationData> validationDataList,
			KpiElement kpiElement) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			List<KPIExcelData> excelData = new ArrayList<>();
			KPIExcelUtility.populatePRSizeExcelData(validationDataList, excelData);
			kpiElement.setExcelData(excelData);
			kpiElement.setExcelColumns(KPIExcelColumn.PR_SIZE.getColumns());
		}
	}

	@Override
	public Long calculateKPIMetrics(Map<String, Object> stringObjectMap) {
		return null;
	}

	@Override
	public Long calculateKpiValue(List<Long> valueList, String kpiId) {
		return calculateKpiValueForLong(valueList, kpiId);
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(List<Node> leafNodeList, String startDate, String endDate,
			KpiRequest kpiRequest) {
		Map<String, Object> resultMap = new HashMap<>();

		resultMap.put(ASSIGNEE_SET, getScmUsersFromBaseClass());
		resultMap.put(MERGE_REQUEST_LIST, getMergeRequestsFromBaseClass());
		return resultMap;
	}

	@Override
	public String getQualifierType() {
		return KPICode.PR_SIZE.name();
	}

	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(fieldMapping.getThresholdValueKPI162(), KPICode.PR_SIZE.getKpiId());
	}
}