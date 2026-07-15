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
import java.util.*;
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
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScmPrRevertRateServiceImpl
		extends BitBucketKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String ASSIGNEE_LIST = "assigneeList";
	private static final String MRS_LIST = "mrsList";
	private static final String TOTAL_MERGED = "Merged PR";
	private static final String REVERTED = "No of Revert PR";
	private static final String REVERT_RATE = "Revert Rate";

	private final ConfigHelperService configHelperService;
	private final KpiHelperService kpiHelperService;
	private final ScmKpiHelperService scmKpiHelperService;

	@Override
	public String getQualifierType() {
		return KPICode.PR_REVERT_RATE_SLINGSHOT.name();
	}

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
		calculateAggregatedValueMap(projectNode, nodeWiseKPIValue, KPICode.PR_REVERT_RATE_SLINGSHOT);

		Map<String, List<DataCount>> trendValuesMap =
				getTrendValuesMap(
						kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.PR_REVERT_RATE_SLINGSHOT);

		kpiElement.setTrendValueList(
				DeveloperKpiHelper.prepareDataCountGroups(
						trendValuesMap, KPICode.PR_REVERT_RATE_SLINGSHOT.getKpiId()));
		return kpiElement;
	}

	@Override
	public Double calculateKPIMetrics(Map<String, Object> t) {
		return null;
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
	public Double calculateKpiValue(List<Double> valueList, String kpiId) {
		return calculateKpiValueForDouble(valueList, kpiId);
	}

	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI215(), KPICode.PR_REVERT_RATE_SLINGSHOT.getKpiId());
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
												.filter(Objects::nonNull)
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
		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;

		List<ScmMergeRequests> mergedRequests =
				mrsInRange.stream()
						.filter(mrs -> mrs.isClosed() && "merged".equalsIgnoreCase(mrs.getState()))
						.toList();

		long totalMerged = mergedRequests.size();
		long revertedCount = countRevertedPRs(mergedRequests);

		setDataCount(
				projectName, dateLabel, overallKpiGroup, revertedCount, totalMerged, kpiTrendDataByGroup);

		Map<String, List<ScmMergeRequests>> userWiseMergeRequests =
				DeveloperKpiHelper.groupMergeRequestsByUser(mergedRequests);

		userWiseMergeRequests.forEach(
				(userEmail, userMergeRequests) -> {
					String developerName = emailToNameMap.getOrDefault(userEmail, userEmail);
					long userTotal = userMergeRequests.size();
					List<ScmMergeRequests> revertedUserPRs =
							userMergeRequests.stream().filter(this::isRevertedPR).toList();
					long userReverted = revertedUserPRs.size();
					List<String> revertPrUrls =
							revertedUserPRs.stream()
									.map(ScmMergeRequests::getMergeRequestUrl)
									.filter(Objects::nonNull)
									.collect(Collectors.toList());

					String userKpiGroup = branchName + "#" + developerName;
					setDataCount(
							projectName, dateLabel, userKpiGroup, userReverted, userTotal, kpiTrendDataByGroup);

					validationDataList.add(
							createValidationData(
									projectName,
									tool,
									developerName,
									userEmail,
									dateLabel,
									userReverted,
									userTotal,
									revertPrUrls));
				});
	}

	private void setDataCount(
			String projectName,
			String week,
			String kpiGroup,
			long revertedCount,
			long totalMerged,
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
									newDataCount.setValue(0.0);
									newDataCount.setLineValue(0.0);
									dataCounts.add(newDataCount);
									return newDataCount;
								});

		long currentReverted =
				(dataCount.getHoverValue() != null && dataCount.getHoverValue().containsKey(REVERTED))
						? ((Number) dataCount.getHoverValue().get(REVERTED)).longValue() + revertedCount
						: revertedCount;
		long currentTotal =
				(dataCount.getHoverValue() != null && dataCount.getHoverValue().containsKey(TOTAL_MERGED))
						? ((Number) dataCount.getHoverValue().get(TOTAL_MERGED)).longValue() + totalMerged
						: totalMerged;

		long nonRevertTotal = currentTotal - currentReverted;
		double revertRate = nonRevertTotal > 0 ? (currentReverted * 100.0 / nonRevertTotal) : 0.0;
		dataCount.setValue(revertRate);
		Map<String, Object> hoverValues = new HashMap<>();
		hoverValues.put(TOTAL_MERGED, currentTotal);
		hoverValues.put(REVERTED, currentReverted);
		hoverValues.put(REVERT_RATE, String.format("%.2f%%", revertRate));
		dataCount.setHoverValue(hoverValues);
	}

	private long countRevertedPRs(List<ScmMergeRequests> mergeRequests) {
		return mergeRequests.stream().filter(this::isRevertedPR).count();
	}

	private boolean isRevertedPR(ScmMergeRequests mergeRequest) {
		return mergeRequest.getTitle() != null
				&& mergeRequest.getTitle().toLowerCase().contains("revert");
	}

	private RepoToolValidationData createValidationData(
			String projectName,
			Tool tool,
			String developerName,
			String developerEmail,
			String dateLabel,
			long revertedCount,
			long totalMerged,
			List<String> revertPrUrls) {
		RepoToolValidationData validationData = new RepoToolValidationData();
		validationData.setProjectName(projectName);
		validationData.setBranchName(tool.getBranch());
		validationData.setRepoUrl(
				tool.getRepositoryName() != null ? tool.getRepositoryName() : tool.getRepoSlug());
		validationData.setDeveloperName(developerName);
		validationData.setDeveloperEmail(developerEmail);
		validationData.setDate(dateLabel);
		validationData.setMrCount(totalMerged);
		validationData.setKpiPRs(revertedCount);
		validationData.setMergeRequestUrls(revertPrUrls);
		long nonRevertTotal = totalMerged - revertedCount;
		validationData.setRevertRate(
				nonRevertTotal > 0 ? (revertedCount * 100.0 / nonRevertTotal) : 0.0);
		return validationData;
	}

	private void populateExcelData(
			String requestTrackerId,
			List<RepoToolValidationData> validationDataList,
			KpiElement kpiElement) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			List<KPIExcelData> excelData = new ArrayList<>();
			KPIExcelUtility.populatePrRevertRateExcelData(validationDataList, excelData);
			kpiElement.setExcelData(excelData);
			kpiElement.setExcelColumns(KPIExcelColumn.SCM_PR_REVERT_RATE.getColumns());
		}
	}
}
