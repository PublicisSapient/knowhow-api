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

package com.publicissapient.kpidashboard.apis.jira.scrum.service;

import static com.publicissapient.kpidashboard.common.constant.CommonConstant.HIERARCHY_LEVEL_ID_PROJECT;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.JiraFeature;
import com.publicissapient.kpidashboard.apis.enums.JiraFeatureHistory;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.scrum.service.leadtime.LeadTimeCalculationStrategy;
import com.publicissapient.kpidashboard.apis.jira.scrum.service.leadtime.LeadTimeContext;
import com.publicissapient.kpidashboard.apis.jira.scrum.service.leadtime.LeadTimeStrategyFactory;
import com.publicissapient.kpidashboard.apis.jira.service.JiraKPIService;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.LeadTimeChangeData;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.constant.ProcessorConstants;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.Deployment;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.repository.application.DeploymentRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.repository.scm.ScmCommitsRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * This service for managing Lead time for change kpi for dora tab.
 *
 * @author hiren babariya
 */
@Component
@Slf4j
public class LeadTimeForChangeServiceImpl
		extends JiraKPIService<Double, List<Object>, Map<String, Object>> {

	@Autowired private ConfigHelperService configHelperService;

	@Autowired private JiraIssueRepository jiraIssueRepository;

	@Autowired private JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;

	@Autowired private DeploymentRepository deploymentRepositoryCustom;

	@Autowired private ScmKpiHelperService scmKpiHelperService;

	@Autowired private ScmCommitsRepository scmCommitsRepository;

	@Autowired private CustomApiConfig customApiConfig;

	@Autowired private CacheService cacheService;

	@Autowired private LeadTimeStrategyFactory strategyFactory;

	private static final String JIRA_DATA = "jiraIssueData";
	private static final String JIRA_HISTORY_DATA = "jiraIssueHistoryData";
	private static final String MERGE_REQUEST_DATA = "mergeRequestData";
	private static final String LEAD_TIME_CONFIG_REPO_TOOL = "leadTimeConfigRepoTool";
	private static final String DOD_STATUS = "dodStatus";
	private static final String STATUS_FIELD = "statusUpdationLog.story.changedTo";
	private static final String STORY_ID = "storyID";
	private static final String DEPLOYMENT_DATA = "deploymentData";
	private static final String COMMITS_DATA = "commitsData";

	@Override
	public String getQualifierType() {
		return KPICode.LEAD_TIME_FOR_CHANGE.name();
	}

	@Override
	public KpiElement getKpiData(
			KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
			throws ApplicationException {

		Node root = treeAggregatorDetail.getRoot();
		Map<String, Node> mapTmp = treeAggregatorDetail.getMapTmp();

		List<Node> projectList =
				treeAggregatorDetail.getMapOfListOfProjectNodes().get(HIERARCHY_LEVEL_ID_PROJECT);
		projectWiseLeafNodeValue(mapTmp, projectList, kpiElement);

		log.debug(
				"[LEAD-TIME-CHANGE-LEAF-NODE-VALUE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				root);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValue(root, nodeWiseKPIValue, KPICode.LEAD_TIME_FOR_CHANGE);
		List<DataCount> trendValues =
				getAggregateTrendValues(
						kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.LEAD_TIME_FOR_CHANGE);
		kpiElement.setTrendValueList(trendValues);

		return kpiElement;
	}

	/**
	 * fetch data based on field mapping and project wise
	 *
	 * @param leafNodeList project node
	 * @param startDate start date
	 * @param endDate end date
	 * @param kpiRequest kpi request
	 * @return Map<String, Object> map of object
	 */
	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {

		List<String> projectBasicConfigIdList = new ArrayList<>();
		Map<String, Object> resultListMap = new HashMap<>();
		Map<String, List<String>> mapOfFilters = new LinkedHashMap<>();
		Map<String, List<String>> mapOfFiltersFH = new LinkedHashMap<>();
		Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();
		Map<String, Map<String, Object>> uniqueProjectMapFH = new HashMap<>();
		Map<String, String> toBranchForMRList = new HashMap<>();
		Map<String, String> projectWiseLeadTimeConfigRepoTool = new HashMap<>();
		Map<String, List<String>> projectWiseDodStatus = new HashMap<>();

		Map<String, List<String>> sortedReleaseListProjectWise = getProjectWiseSortedReleases();

		leafNodeList.forEach(
				leafNode -> {
					ObjectId basicProjectConfigId = leafNode.getProjectFilter().getBasicProjectConfigId();
					Map<String, Object> mapOfProjectFiltersFH = new LinkedHashMap<>();
					Map<String, Object> mapOfProjectFilters = new LinkedHashMap<>();

					FieldMapping fieldMapping =
							configHelperService.getFieldMappingMap().get(basicProjectConfigId);
					projectWiseLeadTimeConfigRepoTool.put(
							basicProjectConfigId.toString(), fieldMapping.getLeadTimeConfigRepoTool());
					setFieldMappingForRepoTools(toBranchForMRList, basicProjectConfigId, fieldMapping);
					setFieldMappingOfRelease(
							uniqueProjectMap,
							sortedReleaseListProjectWise,
							basicProjectConfigId,
							mapOfProjectFilters);
					setFieldMappingForJira(
							projectWiseDodStatus, basicProjectConfigId, mapOfProjectFiltersFH, fieldMapping);
					uniqueProjectMapFH.put(basicProjectConfigId.toString(), mapOfProjectFiltersFH);
					projectBasicConfigIdList.add(basicProjectConfigId.toString());
				});

		mapOfFilters.put(
				JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
				projectBasicConfigIdList.stream().distinct().collect(Collectors.toList()));

		List<JiraIssue> jiraIssueList =
				jiraIssueRepository.findByRelease(mapOfFilters, uniqueProjectMap);
		List<String> issueIdList =
				jiraIssueList.stream().map(JiraIssue::getNumber).collect(Collectors.toList());
		if (CollectionUtils.isNotEmpty(jiraIssueList)) {

			mapOfFiltersFH.put(STORY_ID, issueIdList);
			List<JiraIssueCustomHistory> historyDataList =
					jiraIssueCustomHistoryRepository.findFeatureCustomHistoryStoryProjectWise(
							mapOfFiltersFH, uniqueProjectMapFH, Sort.Direction.ASC);
			resultListMap.put(JIRA_HISTORY_DATA, historyDataList); // logic 1 data
		}
		Map<String, List<ScmMergeRequests>> projectWiseMergeRequestList = new HashMap<>();
		Map<String, List<ScmCommits>> commitsByProject = new HashMap<>();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DateUtil.TIME_FORMAT);
		LocalDate localStartDate = LocalDate.parse(startDate, formatter);
		LocalDate localEndDate = LocalDate.parse(endDate, formatter);
		findMergeRequestAndCommits(
				toBranchForMRList,
				projectWiseLeadTimeConfigRepoTool,
				localStartDate,
				localEndDate,
				projectWiseMergeRequestList,
				commitsByProject);

		List<Deployment> deploymentList =
				deploymentRepositoryCustom.findDeploymentList(
						mapOfFilters,
						projectBasicConfigIdList.stream().map(ObjectId::new).collect(Collectors.toSet()),
						startDate,
						endDate);

		List<Deployment> argoCDDeployments =
				deploymentList.stream()
						.filter(
								d ->
										ProcessorConstants.ARGOCD.equalsIgnoreCase(d.getTool())
												|| ProcessorConstants.GITHUBACTION.equalsIgnoreCase(d.getTool()))
						.toList();
		if (CollectionUtils.isNotEmpty(argoCDDeployments)) {
			deploymentList.sort(Comparator.comparing(Deployment::getStartTime));
			AtomicReference<LocalDateTime> previousDeploymentTime = new AtomicReference<>();
			deploymentList.forEach(
					deployment -> {
						if (previousDeploymentTime.get() != null) {
							LocalDateTime deployStartDateTime =
									LocalDateTime.parse(deployment.getStartTime(), formatter);
							List<String> changeSetShas =
									commitsByProject.get(deployment.getBasicProjectConfigId().toString()).stream()
											.filter(
													commit ->
															(DateUtil.convertMillisToLocalDateTime(commit.getCommitTimestamp())
																			.isAfter(previousDeploymentTime.get())
																	&& DateUtil.convertMillisToLocalDateTime(
																					commit.getCommitTimestamp())
																			.isBefore(deployStartDateTime)))
											.map(ScmCommits::getSha)
											.toList();
							deployment.setChangeSets(changeSetShas);
						}
						if (!deployment.getChangeSets().isEmpty()) {
							Optional<ScmCommits> headCommit =
									commitsByProject.get(deployment.getBasicProjectConfigId().toString()).stream()
											.filter(
													commit ->
															commit
																	.getSha()
																	.equalsIgnoreCase(
																			deployment.getChangeSets().stream().findFirst().get()))
											.findFirst();
							headCommit.ifPresent(
									scmCommits ->
											previousDeploymentTime.set(
													DateUtil.convertMillisToLocalDateTime(scmCommits.getCommitTimestamp())));
						}
					});
		}
		resultListMap.put(JIRA_DATA, jiraIssueList);
		resultListMap.put(LEAD_TIME_CONFIG_REPO_TOOL, projectWiseLeadTimeConfigRepoTool);
		resultListMap.put(DOD_STATUS, projectWiseDodStatus);
		resultListMap.put(MERGE_REQUEST_DATA, projectWiseMergeRequestList); // logic 2 data
		resultListMap.put(
				DEPLOYMENT_DATA,
				deploymentList.stream()
						.collect(Collectors.groupingBy(d -> d.getBasicProjectConfigId().toString())));
		resultListMap.put(COMMITS_DATA, commitsByProject);
		return resultListMap;
	}

	/**
	 * set field maaping for history fetch data of jira
	 *
	 * @param projectWiseDodStatus done status
	 * @param basicProjectConfigId basic config id
	 * @param mapOfProjectFiltersFH db fetching object
	 * @param fieldMapping field mapping
	 */
	private void setFieldMappingForJira(
			Map<String, List<String>> projectWiseDodStatus,
			ObjectId basicProjectConfigId,
			Map<String, Object> mapOfProjectFiltersFH,
			FieldMapping fieldMapping) {
		if (CollectionUtils.isNotEmpty(fieldMapping.getJiraIssueTypeKPI156())) {
			mapOfProjectFiltersFH.put(
					JiraFeatureHistory.STORY_TYPE.getFieldValueInFeature(),
					CommonUtils.convertToPatternList(fieldMapping.getJiraIssueTypeKPI156()));
		} else {
			List<String> defaultIssueTypes =
					Arrays.stream(fieldMapping.getJiraIssueTypeNames()).collect(Collectors.toList());
			mapOfProjectFiltersFH.put(
					JiraFeatureHistory.STORY_TYPE.getFieldValueInFeature(),
					CommonUtils.convertToPatternList(defaultIssueTypes));
		}
		if (CollectionUtils.isNotEmpty(fieldMapping.getJiraDodKPI156())) {
			mapOfProjectFiltersFH.put(
					STATUS_FIELD, CommonUtils.convertToPatternList(fieldMapping.getJiraDodKPI156()));
			projectWiseDodStatus.put(basicProjectConfigId.toString(), fieldMapping.getJiraDodKPI156());
		} else {
			List<String> defaultDODStatus = new ArrayList<>();
			defaultDODStatus.add(CommonConstant.CLOSED);
			mapOfProjectFiltersFH.put(STATUS_FIELD, CommonUtils.convertToPatternList(defaultDODStatus));
			projectWiseDodStatus.put(basicProjectConfigId.toString(), defaultDODStatus);
		}
	}

	/**
	 * set field mapping for fetch data release wise
	 *
	 * @param uniqueProjectMap db fetching object
	 * @param sortedReleaseListProjectWise last N release
	 * @param basicProjectConfigId basic config id
	 * @param mapOfProjectFilters db fetching object
	 */
	private void setFieldMappingOfRelease(
			Map<String, Map<String, Object>> uniqueProjectMap,
			Map<String, List<String>> sortedReleaseListProjectWise,
			ObjectId basicProjectConfigId,
			Map<String, Object> mapOfProjectFilters) {
		List<String> sortedReleaseList =
				sortedReleaseListProjectWise.getOrDefault(
						basicProjectConfigId.toString(), new ArrayList<>());
		if (CollectionUtils.isNotEmpty(sortedReleaseList)) {
			mapOfProjectFilters.put(
					CommonConstant.RELEASE, CommonUtils.convertToPatternListForSubString(sortedReleaseList));
			uniqueProjectMap.put(basicProjectConfigId.toString(), mapOfProjectFilters);
		}
	}

	/**
	 * set field mapping for repo tools
	 *
	 * @param toBranchForMRList production branch
	 * @param basicProjectConfigId basic config id
	 * @param fieldMapping field mapping
	 */
	private void setFieldMappingForRepoTools(
			Map<String, String> toBranchForMRList,
			ObjectId basicProjectConfigId,
			FieldMapping fieldMapping) {
		if (CommonConstant.REPO.equals(fieldMapping.getLeadTimeConfigRepoTool())
				&& Optional.ofNullable(fieldMapping.getToBranchForMRKPI156()).isPresent()) {
			toBranchForMRList.put(basicProjectConfigId.toString(), fieldMapping.getToBranchForMRKPI156());
		}
	}

	/**
	 * find merge request list if field mapping true based on basic config id and from branch , to
	 * branch
	 *
	 * @param toBranchForMRList to branch name
	 * @param projectWiseLeadTimeConfigRepoTool config logic of kpi
	 * @param mergeRequestsByProject merge Request list
	 */
	private void findMergeRequestAndCommits(
			Map<String, String> toBranchForMRList,
			Map<String, String> projectWiseLeadTimeConfigRepoTool,
			LocalDate startDate,
			LocalDate endDate,
			Map<String, List<ScmMergeRequests>> mergeRequestsByProject,
			Map<String, List<ScmCommits>> commitsByProjects) {
		CustomDateRange customDateRange = new CustomDateRange();
		customDateRange.setStartDate(startDate);
		customDateRange.setEndDate(endDate);
		projectWiseLeadTimeConfigRepoTool.forEach(
				(projectBasicConfigId, leadTimeConfigRepoTool) -> {
					if (CommonConstant.REPO.equals(leadTimeConfigRepoTool)) {
						String toBranchForMRKPI156 = toBranchForMRList.get(projectBasicConfigId);
						List<ScmMergeRequests> mergeRequests =
								scmKpiHelperService.getMergeRequests(
										new ObjectId(projectBasicConfigId), customDateRange);
						List<ScmCommits> commits =
								scmKpiHelperService.getCommitDetails(
										new ObjectId(projectBasicConfigId), customDateRange);
						mergeRequestsByProject.put(
								projectBasicConfigId,
								mergeRequests.stream()
										.filter(mr -> mr.getToBranch().equalsIgnoreCase(toBranchForMRKPI156))
										.toList());
						commitsByProjects.put(projectBasicConfigId, commits);
					}
				});
	}

	/**
	 * get latest N Released releases project wise
	 *
	 * @return sorted release
	 */
	private Map<String, List<String>> getProjectWiseSortedReleases() {
		Map<String, List<String>> sortedReleaseListProjectWise = new HashMap<>();
		List<AccountHierarchyData> accountHierarchyDataList =
				(List<AccountHierarchyData>) cacheService.cacheAccountHierarchyData();

		Map<ObjectId, List<Node>> releaseNodeProjectWise =
				accountHierarchyDataList.stream()
						.flatMap(accountHierarchyData -> accountHierarchyData.getNode().stream())
						.filter(
								accountHierarchyNode ->
										accountHierarchyNode
														.getProjectHierarchy()
														.getHierarchyLevelId()
														.equalsIgnoreCase(CommonConstant.HIERARCHY_LEVEL_ID_RELEASE)
												&& Objects.nonNull(
														accountHierarchyNode.getProjectHierarchy().getReleaseState())
												&& accountHierarchyNode
														.getProjectHierarchy()
														.getReleaseState()
														.equalsIgnoreCase(CommonConstant.RELEASED))
						.collect(
								Collectors.groupingBy(
										releaseNode -> releaseNode.getProjectHierarchy().getBasicProjectConfigId()));

		releaseNodeProjectWise.forEach(
				(basicProjectConfigId, projectNodes) -> {
					List<String> sortedReleaseList = new ArrayList<>();
					projectNodes.stream()
							.filter(node -> Objects.nonNull(node.getProjectHierarchy().getEndDate()))
							.sorted(Comparator.comparing(node -> node.getProjectHierarchy().getEndDate()))
							.limit(customApiConfig.getJiraXaxisMonthCount())
							.forEach(
									node ->
											sortedReleaseList.add(
													node.getProjectHierarchy().getNodeName().split("_")[0]));

					sortedReleaseListProjectWise.put(basicProjectConfigId.toString(), sortedReleaseList);
				});

		return sortedReleaseListProjectWise;
	}

	/**
	 * calculate and set project wise leaf node value
	 *
	 * @param mapTmp map tmp data
	 * @param projectLeafNodeList projectLeafNodeList
	 * @param kpiElement kpiElement
	 */
	private void projectWiseLeafNodeValue(
			Map<String, Node> mapTmp, List<Node> projectLeafNodeList, KpiElement kpiElement) {

		Map<String, Object> durationFilter = KpiDataHelper.getDurationFilter(kpiElement);
		LocalDateTime localStartDate = (LocalDateTime) durationFilter.get(Constant.DATE);
		LocalDateTime localEndDate = DateUtil.getTodayTime();
		DateTimeFormatter formatterMonth = DateTimeFormatter.ofPattern(DateUtil.TIME_FORMAT);
		String startDate = localStartDate.format(formatterMonth);
		String endDate = localEndDate.format(formatterMonth);
		List<KPIExcelData> excelData = new ArrayList<>();
		Map<String, Object> resultMap =
				fetchKPIDataFromDb(projectLeafNodeList, startDate, endDate, null);

		if (MapUtils.isNotEmpty(resultMap)) {
			String requestTrackerId = getRequestTrackerId();

			List<JiraIssueCustomHistory> historyDataList =
					(List<JiraIssueCustomHistory>)
							resultMap.getOrDefault(JIRA_HISTORY_DATA, new ArrayList<>());
			List<JiraIssue> jiraIssueList =
					(List<JiraIssue>) resultMap.getOrDefault(JIRA_DATA, new ArrayList<>());
			Map<String, String> projectWiseLeadTimeConfigRepoTool =
					(Map<String, String>) resultMap.get(LEAD_TIME_CONFIG_REPO_TOOL);
			Map<String, List<ScmMergeRequests>> projectWiseMergeRequestList =
					(Map<String, List<ScmMergeRequests>>) resultMap.get(MERGE_REQUEST_DATA);
			Map<String, List<String>> projectWiseDodStatus =
					(Map<String, List<String>>) resultMap.get(DOD_STATUS);
			Map<String, List<Deployment>> projectWiseDeploymentData =
					(Map<String, List<Deployment>>) resultMap.get(DEPLOYMENT_DATA);
			Map<String, List<ScmCommits>> scmCommitsByProject =
					(Map<String, List<ScmCommits>>) resultMap.get(COMMITS_DATA);

			Map<String, List<JiraIssue>> projectWiseJiraIssueList =
					jiraIssueList.stream().collect(Collectors.groupingBy(JiraIssue::getBasicProjectConfigId));
			Map<String, List<JiraIssueCustomHistory>> projectWiseJiraIssueHistoryDataList =
					historyDataList.stream()
							.collect(Collectors.groupingBy(JiraIssueCustomHistory::getBasicProjectConfigId));
			List<String> leadTimeTools = new ArrayList<>();
			projectLeafNodeList.forEach(
					node -> {
						String trendLineName = node.getProjectFilter().getName();
						String basicProjectConfigId =
								node.getProjectFilter().getBasicProjectConfigId().toString();
						String leadTimeConfigRepoTool =
								projectWiseLeadTimeConfigRepoTool.get(basicProjectConfigId);
						leadTimeTools.add(leadTimeConfigRepoTool);

						List<JiraIssueCustomHistory> jiraIssueHistoryDataList =
								projectWiseJiraIssueHistoryDataList.get(basicProjectConfigId);
						List<JiraIssue> jiraIssueDataList = projectWiseJiraIssueList.get(basicProjectConfigId);
						List<String> dodStatus = projectWiseDodStatus.get(basicProjectConfigId);

						String weekOrMonth =
								(String) durationFilter.getOrDefault(Constant.DURATION, CommonConstant.WEEK);
						int defaultTimeCount = 8;
						Map<String, List<LeadTimeChangeData>> leadTimeMapTimeWise =
								weekOrMonth.equalsIgnoreCase(CommonConstant.WEEK)
										? getLastNWeek(defaultTimeCount)
										: getLastNMonthCount(defaultTimeCount);

						List<DataCount> dataCountList = new ArrayList<>();

						List<ScmMergeRequests> mergeRequestList =
								projectWiseMergeRequestList.get(basicProjectConfigId);
						List<Deployment> deploymentList = projectWiseDeploymentData.get(basicProjectConfigId);
						Map<String, JiraIssue> jiraIssueMap =
								CollectionUtils.isNotEmpty(jiraIssueDataList)
										? jiraIssueDataList.stream()
												.collect(Collectors.toMap(JiraIssue::getNumber, Function.identity()))
										: new HashMap<>();

						LeadTimeCalculationStrategy strategy =
								strategyFactory.getStrategy(
										leadTimeConfigRepoTool, CollectionUtils.isNotEmpty(deploymentList));

						LeadTimeContext context =
								LeadTimeContext.builder()
										.weekOrMonth(weekOrMonth)
										.jiraIssueHistoryDataList(jiraIssueHistoryDataList)
										.jiraIssueMap(jiraIssueMap)
										.dodStatus(dodStatus)
										.mergeRequestList(mergeRequestList)
										.deploymentList(deploymentList)
										.scmCommitList(scmCommitsByProject.get(basicProjectConfigId))
										.build();

						strategy.calculateLeadTime(context, leadTimeMapTimeWise);

						leadTimeMapTimeWise.forEach(
								(weekOrMonthName, leadTimeListCurrentTime) -> {
									DataCount dataCount =
											createDataCount(trendLineName, weekOrMonthName, leadTimeListCurrentTime);
									dataCountList.add(dataCount);
								});
						populateLeadTimeExcelData(
								excelData,
								requestTrackerId,
								trendLineName,
								leadTimeConfigRepoTool,
								leadTimeMapTimeWise);

						mapTmp.get(node.getId()).setValue(dataCountList);
					});
			kpiElement.setExcelData(excelData);
			kpiElement.setExcelColumns(KPIExcelColumn.LEAD_TIME_FOR_CHANGE.getColumns());
			if (CollectionUtils.isNotEmpty(leadTimeTools)
					&& leadTimeTools.contains(CommonConstant.REPO)) {
				kpiElement.setExcelColumns(KPIExcelColumn.LEAD_TIME_FOR_CHANGE_REPO.getColumns());
			}
		}
	}

	/**
	 * populate excel data
	 *
	 * @param excelData excel data
	 * @param requestTrackerId tracker id for cache
	 * @param trendLineName project name
	 * @param leadTimeConfigRepoTool config for logic of kpi
	 * @param leadTimeMapTimeWise lead time in days
	 */
	private void populateLeadTimeExcelData(
			List<KPIExcelData> excelData,
			String requestTrackerId,
			String trendLineName,
			String leadTimeConfigRepoTool,
			Map<String, List<LeadTimeChangeData>> leadTimeMapTimeWise) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			KPIExcelUtility.populateLeadTimeForChangeExcelData(
					trendLineName, leadTimeMapTimeWise, excelData, leadTimeConfigRepoTool);
		}
	}

	/**
	 * set data count
	 *
	 * @param trendLineName project name
	 * @param weekOrMonthName date
	 * @param leadTimeListCurrentTime lead time list
	 * @return data count
	 */
	private DataCount createDataCount(
			String trendLineName,
			String weekOrMonthName,
			List<LeadTimeChangeData> leadTimeListCurrentTime) {
		double days =
				leadTimeListCurrentTime.stream().mapToDouble(LeadTimeChangeData::getLeadTime).sum();
		DataCount dataCount = new DataCount();
		dataCount.setData(String.valueOf(days));
		dataCount.setSProjectName(trendLineName);
		dataCount.setDate(weekOrMonthName);
		dataCount.setValue(days);
		dataCount.setHoverValue(new HashMap<>());
		return dataCount;
	}

	@Override
	public Double calculateKpiValue(List<Double> valueList, String kpiId) {
		return calculateKpiValueForDouble(valueList, kpiId);
	}

	@Override
	public Double calculateKPIMetrics(Map<String, Object> t) {
		return null;
	}

	/**
	 * get last N weeks
	 *
	 * @param count count
	 * @return map of list of LeadTimeChangeData
	 */
	private Map<String, List<LeadTimeChangeData>> getLastNWeek(int count) {
		Map<String, List<LeadTimeChangeData>> lastNWeek = new LinkedHashMap<>();
		LocalDateTime endDateTime = DateUtil.getTodayTime();

		for (int i = 0; i < count; i++) {
			String currentWeekStr = DateUtil.getWeekRangeUsingDateTime(endDateTime);
			lastNWeek.put(currentWeekStr, new ArrayList<>());
			endDateTime = endDateTime.minusWeeks(1);
		}
		return lastNWeek;
	}

	/**
	 * get last N months
	 *
	 * @param count count
	 * @return map of list of LeadTimeChangeData
	 */
	private Map<String, List<LeadTimeChangeData>> getLastNMonthCount(int count) {
		Map<String, List<LeadTimeChangeData>> lastNMonth = new LinkedHashMap<>();
		LocalDateTime currentDate = DateUtil.getTodayTime();
		String currentDateStr = currentDate.getYear() + Constant.DASH + currentDate.getMonthValue();
		lastNMonth.put(currentDateStr, new ArrayList<>());
		LocalDateTime lastMonth = DateUtil.getTodayTime();
		for (int i = 1; i < count; i++) {
			lastMonth = lastMonth.minusMonths(1);
			String lastMonthStr = lastMonth.getYear() + Constant.DASH + lastMonth.getMonthValue();
			lastNMonth.put(lastMonthStr, new ArrayList<>());
		}
		return lastNMonth;
	}

	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI156(), KPICode.LEAD_TIME_FOR_CHANGE.getKpiId());
	}
}
