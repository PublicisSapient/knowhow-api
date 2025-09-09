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
import java.time.temporal.ChronoUnit;
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

@Slf4j
@Service
@AllArgsConstructor
public class ScmMeanTimeToMergeServiceImpl extends BitBucketKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String ASSIGNEE_SET = "assigneeSet";
	private static final String MERGE_REQUEST_LIST = "mergeRequestList";

	private final ConfigHelperService configHelperService;
	private final KpiHelperService kpiHelperService;

	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node projectNode)
			throws ApplicationException {
		Map<String, Node> nodeMap = Map.of(projectNode.getId(), projectNode);
		projectWiseLeafNodeValue(kpiElement, nodeMap, projectNode, kpiRequest);

		log.debug("[PROJECT-WISE][{}]. Values of leaf node after KPI calculation {}", kpiRequest.getRequestTrackerId(),
				projectNode);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValueMap(projectNode, nodeWiseKPIValue, KPICode.REPO_TOOL_MEAN_TIME_TO_MERGE);

		Map<String, List<DataCount>> trendValuesMap = getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue,
				KPICode.REPO_TOOL_MEAN_TIME_TO_MERGE);
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

		Map<String, List<DataCount>> aggregatedDataMap = new LinkedHashMap<>();
		List<RepoToolValidationData> validationDataList = new ArrayList<>();

		for (int i = 0; i < dataPoints; i++) {
			CustomDateRange periodRange = KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration);
			String dateLabel = KpiHelperService.getDateRange(periodRange, duration);
			List<ScmMergeRequests> mergedRequestsInRange = mergeRequests.stream()
					.filter(request -> request.getMergedAt() != null)
					.filter(request -> DateUtil.isWithinDateTimeRange(request.getMergedAt(),
							periodRange.getStartDateTime(), periodRange.getEndDateTime()))
					.toList();

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

		double meanTimeToMergeSeconds = calculateMeanTimeToMerge(mergeRequestsForBranch);
		long meanTimeToMergeHours = KpiHelperService.convertMilliSecondsToHours(meanTimeToMergeSeconds * 1000);

		DeveloperKpiHelper.setDataCount(projectName, dateLabel, overallKpiGroup, meanTimeToMergeHours, new HashMap<>(),
				aggregatedDataMap);

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
			double userAverageSeconds = calculateMeanTimeToMerge(userMergeRequests);
			Long userAverageHrs = KpiHelperService.convertMilliSecondsToHours(userAverageSeconds * 1000);

			String userKpiGroup = getBranchSubFilter(tool, projectName) + "#" + developerName;

			DeveloperKpiHelper.setDataCount(projectName, dateLabel, userKpiGroup, userAverageHrs, new HashMap<>(),
					aggregatedDataMap);

			List<RepoToolValidationData> userValidationData = new ArrayList<>();
			userMergeRequests.forEach(mr -> {
				if (mr.getCreatedDate() != null && mr.getMergedAt() != null) {
					userValidationData.add(createValidationData(projectName, tool, developerName, dateLabel, mr));
				}
			});
			return userValidationData;
		}).flatMap(List::stream).collect(Collectors.toList());
	}

	private RepoToolValidationData createValidationData(String projectName, Tool tool, String developerName,
			String dateLabel, ScmMergeRequests mergeRequest) {
		RepoToolValidationData validationData = new RepoToolValidationData();
		validationData.setProjectName(projectName);
		validationData.setBranchName(tool.getBranch());
		validationData.setRepoUrl(tool.getRepositoryName() != null ? tool.getRepositoryName() : tool.getRepoSlug());
		validationData.setDeveloperName(developerName);
		validationData.setDate(dateLabel);

		LocalDateTime createdDateTime = DateUtil.convertMillisToLocalDateTime(mergeRequest.getCreatedDate());
		long timeToMergeSeconds = ChronoUnit.SECONDS.between(createdDateTime, mergeRequest.getMergedAt());
		validationData.setMeanTimeToMerge(KpiHelperService.convertMilliSecondsToHours(timeToMergeSeconds * 1000.0));

		validationData.setMergeRequestUrl(mergeRequest.getMergeRequestUrl());
		LocalDateTime createdDateTimeUTC = DateUtil.localDateTimeToUTC(createdDateTime);
		LocalDateTime mergedAtUTC = DateUtil.localDateTimeToUTC(mergeRequest.getMergedAt());

		validationData.setPrRaisedTime(DateUtil.tranformUTCLocalTimeToZFormat(createdDateTimeUTC));
		validationData.setPrActivityTime(DateUtil.tranformUTCLocalTimeToZFormat(mergedAtUTC));

		return validationData;
	}

	private double calculateMeanTimeToMerge(List<ScmMergeRequests> mergeRequests) {
		if (CollectionUtils.isEmpty(mergeRequests)) {
			return 0.0;
		}

		List<Long> timeToMergeList = mergeRequests.stream()
				.filter(mr -> mr.getCreatedDate() != null && mr.getMergedAt() != null)
				.filter(mr -> ScmMergeRequests.MergeRequestState.MERGED.name().equalsIgnoreCase(mr.getState()))
				.map(mr -> {
					LocalDateTime createdDateTime = DateUtil.convertMillisToLocalDateTime(mr.getCreatedDate());
					return ChronoUnit.SECONDS.between(createdDateTime, mr.getMergedAt());
				}).collect(Collectors.toList());

		if (CollectionUtils.isEmpty(timeToMergeList)) {
			return 0.0;
		}

		return timeToMergeList.stream().mapToLong(Long::longValue).average().orElse(0.0);
	}

	private void populateExcelData(String requestTrackerId, List<RepoToolValidationData> validationDataList,
			KpiElement kpiElement) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			List<KPIExcelData> excelData = new ArrayList<>();
			KPIExcelUtility.populateMeanTimeMergeExcelData(validationDataList, excelData);
			kpiElement.setExcelData(excelData);
			kpiElement.setExcelColumns(KPIExcelColumn.REPO_TOOL_MEAN_TIME_TO_MERGE.getColumns());
		}
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
	public Map<String, Object> fetchKPIDataFromDb(List<Node> leafNodeList, String startDate, String endDate,
			KpiRequest kpiRequest) {
		Map<String, Object> scmDataMap = new HashMap<>();

		scmDataMap.put(ASSIGNEE_SET, getScmUsersFromBaseClass());
		scmDataMap.put(MERGE_REQUEST_LIST, getMergeRequestsFromBaseClass());
		return scmDataMap;
	}

	@Override
	public String getQualifierType() {
		return KPICode.REPO_TOOL_MEAN_TIME_TO_MERGE.name();
	}

	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(fieldMapping.getThresholdValueKPI158(),
				KPICode.REPO_TOOL_MEAN_TIME_TO_MERGE.getKpiId());
	}
}
