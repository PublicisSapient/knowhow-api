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

package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.speed;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.PullRequestsValue;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScmPrSizeDistributionServiceImpl
		extends BitBucketKPIService<Long, List<Object>, Map<String, Object>> {

	private static final String MR_SIZE = "No. of lines";
	private static final String ASSIGNEE_LIST = "assigneeList";
	private static final String MRS_LIST = "mrsList";

	private final ConfigHelperService configHelperService;
	private final KpiHelperService kpiHelperService;
	private final ScmKpiHelperService scmKpiHelperService;

	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node projectNode)
			throws ApplicationException {
		kpiRequest.setXAxisDataPoints(12);
		kpiRequest.setDuration(CommonConstant.WEEK);
		Map<String, Node> nodeMap = Map.of(projectNode.getId(), projectNode);
		calculateProjectKpiTrendData(kpiElement, nodeMap, projectNode, kpiRequest);

		log.debug(
				"[PROJECT-WISE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				projectNode);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValueMap(projectNode, nodeWiseKPIValue, KPICode.PR_SIZE_DISTRIBUTION);

		Map<String, List<DataCount>> trendValuesMap =
				getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.PR_SIZE_DISTRIBUTION);
		kpiElement.setTrendValueList(
				DeveloperKpiHelper.prepareDataCountGroups(
						trendValuesMap, KPICode.PR_SIZE_DISTRIBUTION.getKpiId()));
		return kpiElement;
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
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
		Map<String, Object> scmDataMap = new HashMap<>();
		List<ScmMergeRequests> scmMergeRequests =
				scmKpiHelperService.getMergedRequests(
						leafNodeList.get(0).getProjectFilter().getBasicProjectConfigId(),
						DeveloperKpiHelper.getStartAndEndDate(kpiRequest));
		List<Assignee> assigneeList =
				scmKpiHelperService.getJiraAssigneeForScmUsers(
						leafNodeList.get(0).getProjectFilter().getBasicProjectConfigId());

		scmDataMap.put(ASSIGNEE_LIST, assigneeList);
		scmDataMap.put(MRS_LIST, scmMergeRequests);

		return scmDataMap;
	}

	@Override
	public String getQualifierType() {
		return KPICode.PR_SIZE_DISTRIBUTION.name();
	}

	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI211(), KPICode.PR_SIZE_DISTRIBUTION.getKpiId());
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
		List<ScmMergeRequests> allMrs = (List<ScmMergeRequests>) scmDataMap.get(MRS_LIST);
		List<Assignee> assigneeList = (List<Assignee>) scmDataMap.get(ASSIGNEE_LIST);

		if (CollectionUtils.isEmpty(allMrs)) {
			log.error(
					"[BITBUCKET-AGGREGATED-VALUE]. No merge requests found for project {}", projectLeafNode);
			return;
		}

		Map<String, String> emailToNameMap =
				CollectionUtils.emptyIfNull(assigneeList).stream()
						.filter(a -> CollectionUtils.isNotEmpty(a.getEmail()))
						.flatMap(
								a ->
										a.getEmail().stream()
												.filter(email -> email != null)
												.map(email -> Map.entry(email, a.getAssigneeName())))
						.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));

		Map<ObjectId, List<ScmMergeRequests>> mrsByProcessorItem =
				allMrs.stream()
						.filter(c -> c.getProcessorItemId() != null)
						.collect(Collectors.groupingBy(ScmMergeRequests::getProcessorItemId));

		Map<String, List<DataCount>> kpiTrendDataByGroup = new LinkedHashMap<>();
		List<RepoToolValidationData> validationDataList = new ArrayList<>();

		for (int i = 0; i < dataPoints; i++) {
			CustomDateRange periodRange =
					KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration);
			String dateLabel = KpiHelperService.getDateRange(periodRange, duration);

			scmTools.forEach(
					tool -> {
						if (!DeveloperKpiHelper.isValidTool(tool)) {
							return;
						}
						ObjectId processorItemId = tool.getProcessorItemList().get(0).getId();
						List<ScmMergeRequests> toolMrs =
								mrsByProcessorItem.getOrDefault(processorItemId, Collections.emptyList());
						List<ScmMergeRequests> mrsInRange =
								DeveloperKpiHelper.filterMergeRequestsByMergedDate(toolMrs, periodRange);
						processToolData(
								tool,
								mrsInRange,
								emailToNameMap,
								dateLabel,
								projectLeafNode.getProjectFilter().getName(),
								kpiTrendDataByGroup,
								validationDataList);
					});

			currentDate = DeveloperKpiHelper.getNextRangeDate(duration, currentDate);
		}

		mapTmp.get(projectLeafNode.getId()).setValue(kpiTrendDataByGroup);
		Collections.reverse(validationDataList);
		populateExcelData(requestTrackerId, validationDataList, kpiElement);
	}

	private void processToolData(
			Tool tool,
			List<ScmMergeRequests> mrsInRange,
			Map<String, String> emailToNameMap,
			String dateLabel,
			String projectName,
			Map<String, List<DataCount>> kpiTrendDataByGroup,
			List<RepoToolValidationData> validationDataList) {

		if (!DeveloperKpiHelper.isValidTool(tool)) {
			return;
		}

		String branchName = getBranchSubFilter(tool, projectName);

		List<ScmMergeRequests> mergedRequests =
				mrsInRange.stream()
						.filter(mrs -> mrs.isClosed() && "merged".equalsIgnoreCase(mrs.getState()))
						.toList();

		List<PullRequestsValue> overAllPRValues =
				mergedRequests.stream().map(this::createPullRequestValue).collect(Collectors.toList());

		DeveloperKpiHelper.setDataCounts(
				projectName,
				dateLabel,
				branchName + "#" + Constant.AGGREGATED_VALUE,
				overAllPRValues,
				kpiTrendDataByGroup);

		Map<String, List<ScmMergeRequests>> userWiseMergeRequests =
				DeveloperKpiHelper.groupMergeRequestsByUser(mergedRequests);

		userWiseMergeRequests.entrySet().stream()
				.map(
						entry -> {
							String userEmail = entry.getKey();
							List<ScmMergeRequests> userMergeRequests = entry.getValue();
							String developerName = emailToNameMap.getOrDefault(userEmail, userEmail);

							List<PullRequestsValue> userWiseMRValues =
									userMergeRequests.stream()
											.map(this::createPullRequestValue)
											.collect(Collectors.toList());

							String userKpiGroup = branchName + "#" + developerName;
							DeveloperKpiHelper.setDataCounts(
									projectName, dateLabel, userKpiGroup, userWiseMRValues, kpiTrendDataByGroup);

							return createValidationData(
									projectName, tool, developerName, userEmail, dateLabel, userWiseMRValues);
						})
				.forEach(validationDataList::add);
	}

	private PullRequestsValue createPullRequestValue(ScmMergeRequests scmMergeRequests) {
		PullRequestsValue pr = new PullRequestsValue();
		pr.setLabel(scmMergeRequests.getExternalId());
		pr.setSize(scmMergeRequests.getLinesChanged().toString());
		pr.setPrUrl(scmMergeRequests.getMergeRequestUrl());
		pr.setHoverValue(Map.of(MR_SIZE, scmMergeRequests.getLinesChanged()));
		return pr;
	}

	private RepoToolValidationData createValidationData(
			String projectName,
			Tool tool,
			String developerName,
			String developerEmail,
			String dateLabel,
			List<PullRequestsValue> pullRequests) {
		RepoToolValidationData validationData = new RepoToolValidationData();
		validationData.setProjectName(projectName);
		validationData.setBranchName(tool.getBranch());
		validationData.setRepoUrl(
				tool.getRepositoryName() != null ? tool.getRepositoryName() : tool.getRepoSlug());
		validationData.setDeveloperName(developerName);
		validationData.setDeveloperEmail(developerEmail);
		validationData.setDate(dateLabel);
		validationData.setPullRequestsValues(pullRequests);
		validationData.setMrCount(pullRequests.size());
		return validationData;
	}

	private void populateExcelData(
			String requestTrackerId,
			List<RepoToolValidationData> validationDataList,
			KpiElement kpiElement) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			List<KPIExcelData> excelData = new ArrayList<>();
			KPIExcelUtility.populatePRSizeExcelData(validationDataList, excelData);
			kpiElement.setExcelData(excelData);
			kpiElement.setExcelColumns(KPIExcelColumn.SCM_PR_SIZE_DISTRIBUTION.getColumns());
		}
	}
}
