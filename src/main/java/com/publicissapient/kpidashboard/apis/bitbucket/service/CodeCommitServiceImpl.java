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

package com.publicissapient.kpidashboard.apis.bitbucket.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.ProjectFilter;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.scm.CommitDetails;
import com.publicissapient.kpidashboard.common.model.scm.MergeRequests;
import com.publicissapient.kpidashboard.common.repository.scm.CommitRepository;
import com.publicissapient.kpidashboard.common.repository.scm.MergeRequestRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * This service reflects the logic for the number of check-ins in master
 * metrics. The logic represent the calculations at the sprint, build and
 * release level.
 *
 * @author prigupta8
 */
@Component
@Slf4j
public class CodeCommitServiceImpl extends BitBucketKPIService<Long, List<Object>, Map<String, Object>> {

	private static final int MILISEC_ONE_DAY = 86_399_999;
	private static final String AZURE_REPO = "AzureRepository";
	private static final String BITBUCKET = "Bitbucket";
	private static final String GITLAB = "GitLab";
	private static final String GITHUB = "GitHub";
	private static final String NO_CHECKIN = "No. of Check in";
	private static final String NO_MERGE = "No. of Merge Requests";

	@Autowired
	private ConfigHelperService configHelperService;

	@Autowired
	private CommitRepository commitRepository;

	@Autowired
	private MergeRequestRepository mergeRequestRepository;

	@Override
	public String getQualifierType() {
		return KPICode.CODE_COMMIT.name();
	}

	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node projectNode)
			throws ApplicationException {

		Map<String, Node> mapTmp = new HashMap<>();
		mapTmp.put(projectNode.getId(), projectNode);
		projectWiseLeafNodeValue(kpiElement, mapTmp, projectNode, kpiRequest);
		log.debug("[PROJECT-WISE][{}]. Values of leaf node after KPI calculation {}", kpiRequest.getRequestTrackerId(),
				projectNode);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValueMap(projectNode, nodeWiseKPIValue, KPICode.CODE_COMMIT);
		Map<String, List<DataCount>> trendValuesMap = getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue,
				KPICode.CODE_COMMIT);
		Map<String, Map<String, List<DataCount>>> kpiFilterWiseProjectWiseDc = new LinkedHashMap<>();
		trendValuesMap.forEach((issueType, dataCounts) -> {
			Map<String, List<DataCount>> projectWiseDc = dataCounts.stream()
					.collect(Collectors.groupingBy(DataCount::getData));
			kpiFilterWiseProjectWiseDc.put(issueType, projectWiseDc);
		});

		List<DataCountGroup> dataCountGroups = new ArrayList<>();
		kpiFilterWiseProjectWiseDc.forEach((issueType, projectWiseDc) -> {
			DataCountGroup dataCountGroup = new DataCountGroup();
			List<DataCount> dataList = new ArrayList<>();
			projectWiseDc.entrySet().stream().forEach(trend -> dataList.addAll(trend.getValue()));
			dataCountGroup.setFilter(issueType);
			dataCountGroup.setValue(dataList);
			dataCountGroups.add(dataCountGroup);
		});
		kpiElement.setTrendValueList(dataCountGroups);
		return kpiElement;
	}

	/**
	 * Populates KPI value to project leaf nodes. It also gives the trend analysis
	 * project wise.
	 *
	 * @param kpiElement
	 * @param projectLeafNode
	 */
	private void projectWiseLeafNodeValue(KpiElement kpiElement, Map<String, Node> mapTmp, Node projectLeafNode,
			KpiRequest kpiRequest) {

		String requestTrackerId = getRequestTrackerId();
		CustomDateRange dateRange = KpiDataHelper.getStartAndEndDate(kpiRequest);
		LocalDate localStartDate = dateRange.getStartDate();
		LocalDate localEndDate = dateRange.getEndDate();

		Integer dataPoints = kpiRequest.getXAxisDataPoints();
		String duration = kpiRequest.getDuration();

		// gets the tool configuration
		Map<ObjectId, Map<String, List<Tool>>> toolMap = configHelperService.getToolItemMap();

		Map<String, Object> resultMap = fetchKPIDataFromDb(Arrays.asList(projectLeafNode), localStartDate.toString(),
				localEndDate.toString(), null);
		List<CommitDetails> commitList = (List<CommitDetails>) resultMap.get("commitCount");
		List<MergeRequests> mergeList = (List<MergeRequests>) resultMap.get("mrCount");
		final Map<ObjectId, Map<String, Long>> commitListItemId = new HashMap<>();
		final Map<ObjectId, Map<String, Long>> mergeListItemId = new HashMap<>();
		collectCommitAndMergeItems(commitList, mergeList, commitListItemId, mergeListItemId);
		List<KPIExcelData> excelData = new ArrayList<>();
		ProjectFilter accountHierarchyData = projectLeafNode.getProjectFilter();
		ObjectId configId = accountHierarchyData == null ? null : accountHierarchyData.getBasicProjectConfigId();
		Map<String, List<Tool>> mapOfListOfTools = toolMap.get(configId);
		List<Tool> reposList = new ArrayList<>();
		populateRepoList(reposList, mapOfListOfTools);
		if (CollectionUtils.isEmpty(reposList)) {
			log.error("[BITBUCKET-AGGREGATED-VALUE]. No Jobs found for this project {}", projectLeafNode.getProjectFilter());
			return;
		}

		List<Map<String, Long>> repoWiseCommitList = new ArrayList<>();
		List<Map<String, Long>> repoWiseMergeRequestList = new ArrayList<>();
		List<String> repoList = new ArrayList<>();
		List<String> branchList = new ArrayList<>();
		String projectName = projectLeafNode.getProjectFilter().getName();
		Map<String, List<DataCount>> aggDataMap = new HashMap<>();
		Map<String, Long> aggCommitCountForRepo = new HashMap<>();
		Map<String, Long> aggMergeCountForRepo = new HashMap<>();
		List<RepoToolValidationData> repoToolValidationDataList = new ArrayList<>();
		reposList.forEach(repo -> {
			if (!CollectionUtils.isEmpty(repo.getProcessorItemList()) && repo.getProcessorItemList().get(0).getId() != null) {
				Map<String, Long> commitCountForRepo = commitListItemId.get(repo.getProcessorItemList().get(0).getId());
				Map<String, Long> mergeCountForRepo = mergeListItemId.get(repo.getProcessorItemList().get(0).getId());
				if (MapUtils.isNotEmpty(commitCountForRepo) || MapUtils.isNotEmpty(mergeCountForRepo)) {
					aggCommitAndMergeCount(aggCommitCountForRepo, aggMergeCountForRepo, commitCountForRepo, mergeCountForRepo);
					Map<String, Long> excelDataLoader = new HashMap<>();
					Map<String, Long> mergeRequestExcelDataLoader = new HashMap<>();
					String repoName = repo.getRepositoryName() != null ? repo.getRepositoryName() : repo.getRepoSlug();
					List<DataCount> dayWiseCount = setDayWiseCountForProject(mergeCountForRepo, commitCountForRepo, repo,
							projectName, repoToolValidationDataList, duration, dataPoints);
					aggDataMap.put(getBranchSubFilter(repo, projectName), dayWiseCount);
					repoWiseCommitList.add(excelDataLoader);
					repoWiseMergeRequestList.add(mergeRequestExcelDataLoader);
					repoList.add(repoName);
					branchList.add(repo.getBranch());
				}
			}
		});

		mapTmp.get(projectLeafNode.getId()).setValue(aggDataMap);
		populateExcelData(requestTrackerId, repoToolValidationDataList, excelData);

		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(KPIExcelColumn.CODE_COMMIT.getColumns());
	}

	/**
	 * @param aggCommitCountForRepo
	 * @param aggMergeCountForRepo
	 * @param commitCountForRepo
	 * @param mergeCountForRepo
	 */
	private void aggCommitAndMergeCount(Map<String, Long> aggCommitCountForRepo, Map<String, Long> aggMergeCountForRepo,
			Map<String, Long> commitCountForRepo, Map<String, Long> mergeCountForRepo) {
		if (MapUtils.isNotEmpty(commitCountForRepo)) {
			aggCommitCountForRepo.putAll(commitCountForRepo);
		}
		if (MapUtils.isNotEmpty(mergeCountForRepo)) {
			aggMergeCountForRepo.putAll(mergeCountForRepo);
		}
	}

	/**
	 * @param commitList
	 * @param mergeList
	 * @param commitListItemId
	 * @param mergeListItemId
	 */
	private void collectCommitAndMergeItems(List<CommitDetails> commitList, List<MergeRequests> mergeList,
			final Map<ObjectId, Map<String, Long>> commitListItemId, final Map<ObjectId, Map<String, Long>> mergeListItemId) {
		// converting to map with keys collectorItemId
		if (CollectionUtils.isNotEmpty(commitList)) {
			commitListItemId.putAll(commitList.stream().collect(Collectors.groupingBy(CommitDetails::getProcessorItemId,
					Collectors.toMap(CommitDetails::getDate, CommitDetails::getCount))));
		}
		if (CollectionUtils.isNotEmpty(mergeList)) {
			mergeListItemId.putAll(mergeList.stream().collect(Collectors.groupingBy(MergeRequests::getProcessorItemId,
					Collectors.toMap(MergeRequests::getDate, MergeRequests::getCount))));
		}
	}

	/**
	 * populate repolist from map
	 *
	 * @param reposList
	 * @param mapOfListOfTools
	 */
	private void populateRepoList(List<Tool> reposList, Map<String, List<Tool>> mapOfListOfTools) {
		if (null != mapOfListOfTools) {
			reposList
					.addAll(mapOfListOfTools.get(BITBUCKET) == null ? Collections.emptyList() : mapOfListOfTools.get(BITBUCKET));
			reposList.addAll(
					mapOfListOfTools.get(AZURE_REPO) == null ? Collections.emptyList() : mapOfListOfTools.get(AZURE_REPO));
			reposList.addAll(mapOfListOfTools.get(GITLAB) == null ? Collections.emptyList() : mapOfListOfTools.get(GITLAB));
			reposList.addAll(mapOfListOfTools.get(GITHUB) == null ? Collections.emptyList() : mapOfListOfTools.get(GITHUB));
		}
	}

	/**
	 * Populates the Excel data if the request tracker ID indicates an Excel source.
	 *
	 * @param requestTrackerId
	 *          the ID of the request tracker, used to determine if the source is
	 *          Excel
	 * @param repoToolValidationDataList
	 *          the list of repository tool validation data to be populated into the
	 *          Excel
	 * @param excelData
	 *          the list of KPI Excel data to be populated
	 */
	private void populateExcelData(String requestTrackerId, List<RepoToolValidationData> repoToolValidationDataList,
			List<KPIExcelData> excelData) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			KPIExcelUtility.populateCodeCommit(repoToolValidationDataList, excelData);
		}
	}

	/**
	 * Sets the day-wise count for the project based on commit and merge data.
	 *
	 * @param mergeCountForRepo
	 *          a map containing the merge count data for the repository
	 * @param commitCountForRepo
	 *          a map containing the commit count data for the repository
	 * @param repo
	 *          the tool repository object
	 * @param projectName
	 *          the name of the project
	 * @param repoToolValidationDataList
	 *          the list to store repository tool validation data
	 * @param duration
	 *          the duration for which the data is being calculated
	 * @param dataPoints
	 *          the number of data points to be calculated
	 * @return a list of DataCount objects representing the day-wise commit and
	 *         merge counts
	 */
	private List<DataCount> setDayWiseCountForProject(Map<String, Long> mergeCountForRepo,
			Map<String, Long> commitCountForRepo, Tool repo, String projectName,
			List<RepoToolValidationData> repoToolValidationDataList, String duration, Integer dataPoints) {
		List<DataCount> dayWiseCommitCount = new ArrayList<>();
		LocalDate currentDate = LocalDate.now();
		for (int i = 0; i < dataPoints; i++) {
			CustomDateRange dateRange = KpiHelperService.getStartAndEndDateExcludingWeekends(currentDate, duration);
			Map<String, Object> hoverValues = new HashMap<>();
			String date = getDateRange(dateRange, duration);
			LocalDate startDate = dateRange.getStartDate();
			Long commitCountValue = 0l;
			Long mergeCountValue = 0l;
			while (startDate.compareTo(dateRange.getEndDate()) <= 0) {
				if (commitCountForRepo != null) {
					commitCountValue = commitCountValue + commitCountForRepo.getOrDefault(currentDate.toString(), 0l);
				}
				if (mergeCountForRepo != null) {
					mergeCountValue = mergeCountValue + mergeCountForRepo.getOrDefault(currentDate.toString(), 0l);
				}
				startDate = startDate.plusDays(1);
			}
			if (repo != null) {
				RepoToolValidationData repoToolValidationData = new RepoToolValidationData();
				repoToolValidationData.setProjectName(projectName);
				repoToolValidationData.setDate(date);
				repoToolValidationData
						.setRepoUrl(repo.getRepositoryName() != null ? repo.getRepositoryName() : repo.getRepoSlug());
				repoToolValidationData.setBranchName(repo.getBranch());
				repoToolValidationData.setCommitCount(commitCountValue);
				repoToolValidationData.setMrCount(mergeCountValue);
				repoToolValidationDataList.add(repoToolValidationData);
			}
			hoverValues.put(NO_MERGE, mergeCountValue.intValue());
			hoverValues.put(NO_CHECKIN, commitCountValue.intValue());
			dayWiseCommitCount.add(setDataCount(projectName, date, hoverValues, commitCountValue, mergeCountValue));
			currentDate = KpiHelperService.getNextRangeDate(duration, currentDate);
		}
		Collections.reverse(dayWiseCommitCount);
		return dayWiseCommitCount;
	}

	private DataCount setDataCount(String projectName, String date, Map<String, Object> dataValues, Long commitCount,
			Long mergeCount) {
		DataCount dataCount = new DataCount();
		dataCount.setLineValue(mergeCount);
		dataCount.setValue(commitCount);
		dataCount.setDate(date);
		dataCount.setHoverValue(dataValues);
		dataCount.setSProjectName(projectName);
		return dataCount;
	}

	private String getDateRange(CustomDateRange dateRange, String duration) {
		String range = null;
		if (CommonConstant.WEEK.equalsIgnoreCase(duration)) {
			range = DateUtil.dateTimeConverter(dateRange.getStartDate().toString(), DateUtil.DATE_FORMAT,
					DateUtil.DISPLAY_DATE_FORMAT) + " to " +
					DateUtil.dateTimeConverter(dateRange.getEndDate().toString(), DateUtil.DATE_FORMAT,
							DateUtil.DISPLAY_DATE_FORMAT);
		} else {
			range = dateRange.getStartDate().toString();
		}
		return range;
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(List<Node> leafNodeList, String startDate, String endDate,
			KpiRequest kpiRequest) {
		Set<String> listOfmapOfProjectFilters = new HashSet<>();
		Set<String> branchList = new HashSet<>();

		List<ObjectId> tools = new ArrayList<>();
		Map<String, Object> resultListMap = new HashMap<>();
		Map<ObjectId, Map<String, List<Tool>>> toolMap;
		// gets the tool configuration
		toolMap = configHelperService.getToolItemMap();

		BasicDBList filter = new BasicDBList();
		BasicDBList mergeFilter = new BasicDBList();

		leafNodeList.forEach(node -> {
			List<Tool> bitbucketJob = getBitBucketJobs(toolMap, node);
			if (CollectionUtils.isEmpty(bitbucketJob)) {
				return;
			}
			bitbucketJob.forEach(job -> {
				if (org.springframework.util.CollectionUtils.isEmpty(job.getProcessorItemList())) {
					return;
				}
				tools.add(job.getProcessorItemList().get(0).getId());
				filter.add(new BasicDBObject("processorItemId", job.getProcessorItemList().get(0).getId())
						.append("branch", job.getBranch()).append("repoSlug", job.getRepoSlug()));

				mergeFilter.add(new BasicDBObject("processorItemId", job.getProcessorItemList().get(0).getId()));
				listOfmapOfProjectFilters.add(job.getUrl());
				branchList.add(job.getBranch());
			});
		});

		if (filter.isEmpty()) {
			return new HashMap<>();
		}

		List<MergeRequests> mrCount = mergeRequestRepository.findMergeList(tools,
				new DateTime(startDate, DateTimeZone.UTC).withTimeAtStartOfDay().getMillis(),
				StringUtils.isNotEmpty(endDate)
						? new DateTime(endDate, DateTimeZone.UTC).withTimeAtStartOfDay().plus(MILISEC_ONE_DAY).getMillis()
						: new Date().getTime(),
				mergeFilter);
		List<CommitDetails> commitCount = commitRepository.findCommitList(tools,
				new DateTime(startDate, DateTimeZone.UTC).withTimeAtStartOfDay().getMillis(),
				StringUtils.isNotEmpty(endDate)
						? new DateTime(endDate, DateTimeZone.UTC).withTimeAtStartOfDay().plus(MILISEC_ONE_DAY).getMillis()
						: new Date().getTime(),
				filter);
		resultListMap.put("mrCount", mrCount);
		resultListMap.put("commitCount", commitCount);

		return resultListMap;
	}

	private List<Tool> getBitBucketJobs(Map<ObjectId, Map<String, List<Tool>>> toolMap, Node node) {

		ProjectFilter accountHierarchyData = node.getProjectFilter();
		ObjectId configId = accountHierarchyData == null ? null : accountHierarchyData.getBasicProjectConfigId();
		Map<String, List<Tool>> toolListMap = toolMap == null ? null : toolMap.get(configId);
		List<Tool> bitbucketJob = new ArrayList<>();
		if (null != toolListMap) {
			bitbucketJob.addAll(toolListMap.get(BITBUCKET) == null ? Collections.emptyList() : toolListMap.get(BITBUCKET));
			bitbucketJob.addAll(toolListMap.get(AZURE_REPO) == null ? Collections.emptyList() : toolListMap.get(AZURE_REPO));
			bitbucketJob.addAll(toolListMap.get(GITLAB) == null ? Collections.emptyList() : toolListMap.get(GITLAB));
			bitbucketJob.addAll(toolListMap.get(GITHUB) == null ? Collections.emptyList() : toolListMap.get(GITHUB));
		}

		if (CollectionUtils.isEmpty(bitbucketJob)) {
			log.error("[BITBUCKET]. No repository found for this project {}", node.getProjectFilter());
		}

		return bitbucketJob;
	}

	@Override
	public Long calculateKPIMetrics(Map<String, Object> t) {

		return null;
	}

	@Override
	public Long calculateKpiValue(List<Long> valueList, String kpiId) {
		return calculateKpiValueForLong(valueList, kpiId);
	}

	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(fieldMapping.getThresholdValueKPI11(), KPICode.CODE_COMMIT.getKpiId());
	}
}
