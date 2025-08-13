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

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.publicissapient.kpidashboard.apis.bitbucket.service.BitBucketKPIService;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.repository.scm.ScmMergeRequestsRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
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
import com.publicissapient.kpidashboard.common.model.jira.AssigneeDetails;
import com.publicissapient.kpidashboard.common.repository.jira.AssigneeDetailsRepository;

import lombok.extern.slf4j.Slf4j;

import static com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper.MILLISECONDS_IN_A_DAY;


@Slf4j
@Component
public class ScmPRSizeServiceImpl extends BitBucketKPIService<Long, List<Object>, Map<String, Object>> {

	private static final String MR_COUNT = "No of PRs";
	private static final String ASSIGNEE_SET = "assigneeSet";
	private static final String MERGE_REQUEST_LIST = "mergeRequestList";

	@Autowired
	private ConfigHelperService configHelperService;

	@Autowired
	private AssigneeDetailsRepository assigneeDetailsRepository;

	@Autowired
	private KpiHelperService kpiHelperService;

	@Autowired
	private ScmMergeRequestsRepository scmMergeRequestsRepository;

	@Override
	public String getQualifierType() {
		return KPICode.PR_SIZE.name();
	}

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

		Map<ObjectId, Map<String, List<Tool>>> toolMap = configHelperService.getToolItemMap();
		ObjectId projectConfigId = Optional.ofNullable(projectLeafNode.getProjectFilter())
				.map(ProjectFilter::getBasicProjectConfigId).orElse(null);

		Map<String, List<Tool>> toolListMap = toolMap.getOrDefault(projectConfigId, Map.of());
		List<Tool> scmTools = kpiHelperService.populateSCMToolsRepoList(toolListMap);

		if (CollectionUtils.isEmpty(scmTools)) {
			log.error("[BITBUCKET-AGGREGATED-VALUE]. No SCM tools found for project {}",
					projectLeafNode.getProjectFilter());
			return;
		}

		Map<String, Object> resultmap = fetchKPIDataFromDb(List.of(projectLeafNode),
				dateRange.getStartDate().toString(), dateRange.getEndDate().toString(), kpiRequest);
		List<ScmMergeRequests> mergeRequests = (List<ScmMergeRequests>) resultmap.get(MERGE_REQUEST_LIST);
		Set<Assignee> assignees = (Set<Assignee>) resultmap.get(ASSIGNEE_SET);

		if (CollectionUtils.isEmpty(mergeRequests)) {
			log.error("[BITBUCKET-AGGREGATED-VALUE]. No merge requests found for project {}", projectLeafNode);
			return;
		}

		Map<String, List<DataCount>> aggregatedDataMap = new LinkedHashMap<>();
		List<RepoToolValidationData> validationDataList = new ArrayList<>();

		for (int i = 0; i < dataPoints; i++) {
			CustomDateRange weekRange = KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate, duration);
			String dateLabel = KpiHelperService.getDateRange(weekRange, duration);

			List<ScmMergeRequests> filteredMergeRequests = mergeRequests.stream().filter(request -> request.getMergedAt() != null)
					.filter(request -> DateUtil.isWithinDateTimeRange(request.getMergedAt(),
							weekRange.getStartDateTime(), weekRange.getEndDateTime()))
					.toList();

			scmTools.forEach(tool -> processToolData(tool, filteredMergeRequests, assignees, aggregatedDataMap,
					validationDataList, dateLabel, projectLeafNode.getProjectFilter().getName()));

			currentDate = DeveloperKpiHelper.getNextRangeDate(duration, currentDate);
		}

		mapTmp.get(projectLeafNode.getId()).setValue(aggregatedDataMap);
		populateExcelData(requestTrackerId, validationDataList, kpiElement);
	}

	private void processToolData(Tool tool, List<ScmMergeRequests> mergeRequests, Set<Assignee> assignees,
			Map<String, List<DataCount>> aggregatedDataMap, List<RepoToolValidationData> validationDataList,
			String dateLabel, String projectName) {
		if (CollectionUtils.isEmpty(tool.getProcessorItemList())
				|| tool.getProcessorItemList().get(0).getId() == null) {
			return;
		}

		String branchName = getBranchSubFilter(tool, projectName);
		String overallKpiGroup = branchName + "#" + Constant.AGGREGATED_VALUE;

		List<ScmMergeRequests> matchingRequests = mergeRequests.stream()
				.filter(request -> request.getRepositoryName().equals(tool.getRepositoryName()))
				.filter(request -> request.getToBranch().equals(tool.getBranch())).toList();

		long totalLinesChanged = matchingRequests.stream().mapToLong(ScmMergeRequests::getLinesChanged).sum();
		long totalMergeRequests = matchingRequests.size();

		setDataCount(projectName, dateLabel, overallKpiGroup, totalLinesChanged, totalMergeRequests, aggregatedDataMap);

		Map<String, List<ScmMergeRequests>> userWiseMergeRequests = matchingRequests.stream()
                .filter(req -> req.getAuthorDetails() != null && req.getAuthorDetails().getEmail() != null)//todo:: check
				.collect(Collectors.groupingBy(request -> request.getAuthorDetails().getEmail()));

		validationDataList.addAll(prepareUserValidationData(userWiseMergeRequests, assignees, tool, projectName,
				dateLabel, aggregatedDataMap));
	}

	private List<RepoToolValidationData> prepareUserValidationData(
			Map<String, List<ScmMergeRequests>> userWiseMergeRequests, Set<Assignee> assignees, Tool tool,
			String projectName, String dateLabel, Map<String, List<DataCount>> aggregatedDataMap) {
		return userWiseMergeRequests.entrySet().stream().map(entry -> {
			String userEmail = entry.getKey();
			List<ScmMergeRequests> userMergeRequests = entry.getValue();

			Optional<Assignee> assignee = assignees.stream()
					.filter(a -> CollectionUtils.isNotEmpty(a.getEmail()) && a.getEmail().contains(userEmail))
					.findFirst();

			String developerName = assignee.map(Assignee::getAssigneeName).orElse(userEmail);
			long userLineChange = userMergeRequests.stream().mapToLong(ScmMergeRequests::getLinesChanged).sum();
			long userMrCount = userMergeRequests.size();

			String userKpiGroup = getBranchSubFilter(tool, projectName) + "#" + developerName;

			setDataCount(projectName, dateLabel, userKpiGroup, userLineChange, userMrCount, aggregatedDataMap);

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

	private void setDataCount(String projectName, String dateLabel, String kpiGroup, long value, long mrCount,
			Map<String, List<DataCount>> dataCountMap) {
		List<DataCount> dataCounts = dataCountMap.computeIfAbsent(kpiGroup, k -> new ArrayList<>());
		Optional<DataCount> existingDataCount = dataCounts.stream()
				.filter(dataCount -> dataCount.getDate().equals(dateLabel)).findFirst();

		if (existingDataCount.isPresent()) {
			DataCount updatedDataCount = existingDataCount.get();
			updatedDataCount.setValue(((Number) updatedDataCount.getValue()).longValue() + value);
		} else {
			DataCount newDataCount = new DataCount();
			newDataCount.setData(String.valueOf(value));
			newDataCount.setSProjectName(projectName);
			newDataCount.setDate(dateLabel);
			newDataCount.setValue(value);
			newDataCount.setKpiGroup(kpiGroup);
			newDataCount.setHoverValue(Map.of(MR_COUNT, mrCount));
			dataCounts.add(newDataCount);
		}
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
		List<ObjectId> toolIds = new ArrayList<>();
		Map<String, Object> resultMap = new HashMap<>();
		Map<ObjectId, Map<String, List<Tool>>> toolMap = configHelperService.getToolItemMap();
		ObjectId projectConfigId = leafNodeList.get(0).getProjectFilter().getBasicProjectConfigId();
		BasicDBList mergeFilter = new BasicDBList();

		for (Node node : leafNodeList) {
			List<Tool> scmTools = kpiHelperService
					.getScmToolJobs(toolMap.get(node.getProjectFilter().getBasicProjectConfigId()), node);
			if (CollectionUtils.isEmpty(scmTools))
				continue;

			for (Tool tool : scmTools) {
				if (CollectionUtils.isEmpty(tool.getProcessorItemList()))
					continue;
				ObjectId processorItemId = tool.getProcessorItemList().get(0).getId();
				toolIds.add(processorItemId);
				mergeFilter.add(new BasicDBObject("processorItemId", processorItemId));
			}
		}

		List<ScmMergeRequests> mergeRequests = scmMergeRequestsRepository.findMergeList(toolIds,
				new DateTime(startDate, DateTimeZone.UTC).withTimeAtStartOfDay().getMillis(),
				new DateTime(endDate, DateTimeZone.UTC).withTimeAtStartOfDay().plus(MILLISECONDS_IN_A_DAY).getMillis(),
				mergeFilter);

		AssigneeDetails assigneeDetails = assigneeDetailsRepository
				.findByBasicProjectConfigId(projectConfigId.toString());
		Set<Assignee> assignees = assigneeDetails != null ? assigneeDetails.getAssignee() : new HashSet<>();

		resultMap.put(ASSIGNEE_SET, assignees);
		resultMap.put(MERGE_REQUEST_LIST, mergeRequests);
		return resultMap;
	}

	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(fieldMapping.getThresholdValueKPI162(), KPICode.PR_SIZE.getKpiId());
	}
}