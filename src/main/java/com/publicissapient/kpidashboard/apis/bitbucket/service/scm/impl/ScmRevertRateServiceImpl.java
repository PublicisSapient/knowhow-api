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
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author shunaray
 */
@Service
@Slf4j
@AllArgsConstructor
public class ScmRevertRateServiceImpl extends BitBucketKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String ASSIGNEE_SET = "assigneeSet";
	private static final String MERGE_REQUEST_LIST = "mergeRequestList";
	private static final String TOTAL_MR_COUNT = "No of Merges";
	private static final String REVERTED_PR_COUNT = "Revert PRs";

	private final ConfigHelperService configHelperService;
	private final KpiHelperService kpiHelperService;

	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node projectNode)
			throws ApplicationException {
		Map<String, Node> nodeMap = Map.of(projectNode.getId(), projectNode);
		calculateProjectKpiTrendData(kpiElement, nodeMap, projectNode, kpiRequest);

		log.debug("[PROJECT-WISE][{}]. Values of leaf node after KPI calculation {}", kpiRequest.getRequestTrackerId(),
				projectNode);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValueMap(projectNode, nodeWiseKPIValue, KPICode.REVERT_RATE);

		Map<String, List<DataCount>> trendValuesMap = getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue,
				KPICode.REVERT_RATE);

		kpiElement.setTrendValueList(DeveloperKpiHelper.prepareDataCountGroups(trendValuesMap));
		return kpiElement;
	}

	/**
	 * Fetch data from db
	 *
	 * @param leafNodeList
	 *            leaf node list
	 * @param startDate
	 *            start date
	 * @param endDate
	 *            end date
	 * @param kpiRequest
	 *            kpi request
	 * @return map of data
	 */
	@Override
	public Map<String, Object> fetchKPIDataFromDb(List<Node> leafNodeList, String startDate, String endDate,
			KpiRequest kpiRequest) {
		Map<String, Object> scmDataMap = new HashMap<>();

		scmDataMap.put(ASSIGNEE_SET, getScmUsersFromBaseClass());
		scmDataMap.put(MERGE_REQUEST_LIST, getMergeRequestsFromBaseClass());
		return scmDataMap;
	}

	@Override
	public String getQualifierType() {
		return KPICode.REVERT_RATE.name();
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
		return calculateThresholdValue(fieldMapping.getThresholdValueKPI180(), KPICode.REVERT_RATE.getKpiId());
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

		Map<String, Object> scmDataMap = fetchKPIDataFromDb(null, null, null, null);
		List<ScmMergeRequests> mergeRequests = (List<ScmMergeRequests>) scmDataMap.get(MERGE_REQUEST_LIST);
		Set<Assignee> assignees = new HashSet<>((Collection<Assignee>) scmDataMap.get(ASSIGNEE_SET));

		if (CollectionUtils.isEmpty(mergeRequests)) {
			log.error("[BITBUCKET-AGGREGATED-VALUE]. No merge requests found for project {}", projectLeafNode);
			return;
		}

		Map<String, List<DataCount>> kpiTrendDataByGroup = new HashMap<>();
		List<RepoToolValidationData> validationDataList = new ArrayList<>();

		for (int i = 0; i < dataPoints; i++) {
			CustomDateRange periodRange = KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration);
			String dateLabel = KpiHelperService.getDateRange(periodRange, duration);

			List<ScmMergeRequests> mergedRequestsInRange = DeveloperKpiHelper
					.filterMergeRequestsByUpdateDate(mergeRequests, periodRange);

			scmTools.forEach(tool -> processToolData(tool, mergedRequestsInRange, assignees, kpiTrendDataByGroup,
					validationDataList, dateLabel, projectLeafNode.getProjectFilter().getName()));

			currentDate = DeveloperKpiHelper.getNextRangeDate(duration, currentDate);
		}

		mapTmp.get(projectLeafNode.getId()).setValue(kpiTrendDataByGroup);
		populateExcelData(requestTrackerId, validationDataList, kpiElement);
	}

	private void processToolData(Tool tool, List<ScmMergeRequests> mergeRequests, Set<Assignee> assignees,
			Map<String, List<DataCount>> kpiTrendDataByGroup, List<RepoToolValidationData> validationDataList,
			String dateLabel, String projectName) {
		if (!DeveloperKpiHelper.isValidTool(tool)) {
			return;
		}

		String branchName = getBranchSubFilter(tool, projectName);
		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;

		List<ScmMergeRequests> branchMergeRequests = DeveloperKpiHelper.filterMergeRequestsForBranch(mergeRequests,
				tool);

		double revertRate = calculateRevertRate(branchMergeRequests);

		long totalMergeRequests = branchMergeRequests.size();
		long revertedPRs = countRevertedPRs(branchMergeRequests);

		Map<String, Object> hoverValues = new HashMap<>();
		hoverValues.put(TOTAL_MR_COUNT, totalMergeRequests);
		hoverValues.put(REVERTED_PR_COUNT, revertedPRs);

		DeveloperKpiHelper.setDataCount(projectName, dateLabel, overallKpiGroup, Math.round(revertRate), hoverValues,
				kpiTrendDataByGroup);

		Map<String, List<ScmMergeRequests>> userWiseMergeRequests = DeveloperKpiHelper
				.groupMergeRequestsByUser(branchMergeRequests);

		validationDataList.addAll(prepareUserValidationData(userWiseMergeRequests, assignees, tool, projectName,
				dateLabel, kpiTrendDataByGroup));
	}

	private List<RepoToolValidationData> prepareUserValidationData(
			Map<String, List<ScmMergeRequests>> userWiseMergeRequests, Set<Assignee> assignees, Tool tool,
			String projectName, String dateLabel, Map<String, List<DataCount>> kpiTrendDataByGroup) {
		return userWiseMergeRequests.entrySet().stream().map(entry -> {
			String userEmail = entry.getKey();
			List<ScmMergeRequests> userMergeRequests = entry.getValue();

			String developerName = DeveloperKpiHelper.getDeveloperName(userEmail, assignees);
			double userRevertRate = calculateRevertRate(userMergeRequests);
			long userMrCount = userMergeRequests.size();
			long revertedPRs = countRevertedPRs(userMergeRequests);

			String userKpiGroup = getBranchSubFilter(tool, projectName) + "#" + developerName;

			Map<String, Object> userHoverValues = new HashMap<>();
			userHoverValues.put(TOTAL_MR_COUNT, userMrCount);
			userHoverValues.put(REVERTED_PR_COUNT, revertedPRs);

			DeveloperKpiHelper.setDataCount(projectName, dateLabel, userKpiGroup, Math.round(userRevertRate),
					userHoverValues, kpiTrendDataByGroup);

			return createValidationData(projectName, tool, developerName, dateLabel, userRevertRate, userMrCount,
					revertedPRs);
		}).collect(Collectors.toList());
	}

	private long countRevertedPRs(List<ScmMergeRequests> mergeRequests) {
		return mergeRequests.stream().filter(this::isRevertedPR).count();
	}

	private boolean isRevertedPR(ScmMergeRequests mergeRequest) {
		return mergeRequest.getTitle() != null && mergeRequest.getTitle().toLowerCase().contains("revert");
	}

	private RepoToolValidationData createValidationData(String projectName, Tool tool, String developerName,
			String dateLabel, double revertRate, long mrCount, long revertedPRs) {
		RepoToolValidationData validationData = new RepoToolValidationData();
		validationData.setProjectName(projectName);
		validationData.setBranchName(tool.getBranch());
		validationData.setRepoUrl(tool.getRepositoryName() != null ? tool.getRepositoryName() : tool.getRepoSlug());
		validationData.setDeveloperName(developerName);
		validationData.setDate(dateLabel);
		validationData.setRevertRate(revertRate);
		validationData.setMrCount(mrCount);
		validationData.setKpiPRs(revertedPRs);
		return validationData;
	}

	/**
	 * Calculate revert rate from merge requests
	 *
	 * @param mergeRequests
	 *            list of merge requests```java
	 * @return revert rate percentage
	 */
	private double calculateRevertRate(List<ScmMergeRequests> mergeRequests) {
		if (CollectionUtils.isEmpty(mergeRequests)) {
			return 0.0;
		}

		long revertedCount = countRevertedPRs(mergeRequests);
		return (double) revertedCount / mergeRequests.size() * 100;
	}

	/**
	 * Populate excel data
	 *
	 * @param requestTrackerId
	 *            request tracker id
	 * @param validationDataList
	 *            repo tool validation data
	 * @param kpiElement
	 *            kpi element
	 */
	private void populateExcelData(String requestTrackerId, List<RepoToolValidationData> validationDataList,
			KpiElement kpiElement) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			List<KPIExcelData> excelData = new ArrayList<>();
			KPIExcelUtility.populateRevertRateExcelData(validationDataList, excelData);
			kpiElement.setExcelData(excelData);
			kpiElement.setExcelColumns(KPIExcelColumn.REVERT_RATE.getColumns());
		}
	}
}
