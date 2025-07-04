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

package com.publicissapient.kpidashboard.apis.common.service.impl;

import java.lang.reflect.Field;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.publicissapient.kpidashboard.apis.model.DefectTransitionInfo;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.abac.UserAuthorizedProjectsService;
import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.FieldMappingEnum;
import com.publicissapient.kpidashboard.apis.enums.JiraFeature;
import com.publicissapient.kpidashboard.apis.enums.JiraFeatureHistory;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.FieldMappingStructureResponse;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.MasterResponse;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolKpiMetricResponse;
import com.publicissapient.kpidashboard.apis.repotools.service.RepoToolsConfigServiceImpl;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.constant.NormalizedJira;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.FieldMappingStructure;
import com.publicissapient.kpidashboard.common.model.application.KpiMaster;
import com.publicissapient.kpidashboard.common.model.application.LabelCount;
import com.publicissapient.kpidashboard.common.model.application.ProjectToolConfig;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.excel.CapacityKpiData;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.KanbanIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.KanbanIssueHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;
import com.publicissapient.kpidashboard.common.model.jira.SprintWiseStory;
import com.publicissapient.kpidashboard.common.model.kpivideolink.KPIVideoLink;
import com.publicissapient.kpidashboard.common.repository.excel.CapacityKpiDataRepository;
import com.publicissapient.kpidashboard.common.repository.excel.KanbanCapacityRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.repository.jira.KanbanJiraIssueHistoryRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;
import com.publicissapient.kpidashboard.common.repository.kpivideolink.KPIVideoLinkRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Helper class for kpi requests . Utility to process for kpi requests.
 *
 * @author tauakram
 */
@Slf4j
@Service
public class KpiHelperService { // NOPMD

	private static final String STORY_DATA = "storyData";
	private static final String PROJECT_WISE_OPEN_STORY_STATUS = "projectWiseOpenStatus";
	private static final String STORY_POINTS_DATA = "storyPoints";
	private static final String DEFECT_DATA = "defectData";
	private static final String SPRINTVELOCITYKEY = "sprintVelocityKey";
	private static final String SUBGROUPCATEGORY = "subGroupCategory";
	private static final String TICKETVELOCITYKEY = "ticketVelocityKey";
	private static final String IN = "in";
	private static final String DEV = "DeveloperKpi";
	private static final String PROJECT_WISE_ISSUE_TYPES = "projectWiseIssueTypes";
	private static final String PROJECT_WISE_CLOSED_STORY_STATUS = "projectWiseClosedStoryStatus";
	private static final String JIRA_ISSUE_HISTORY_DATA = "JiraIssueHistoryData";
	private static final String FIELD_PRIORITY = "priority";
	private static final String FIELD_RCA = "rca";
	private static final String SPRINT_WISE_SPRINTDETAILS = "sprintWiseSprintDetailMap";
	private static final String ISSUE_DATA = "issueData";
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
	public static final String WEEK_FREQUENCY = "week";
	public static final String DAY_FREQUENCY = "day";
	private static final String STORY_LIST = "stories";
	private static final String SPRINTSDETAILS = "sprints";
	private static final String AZURE_REPO = "AzureRepository";
	private static final String BITBUCKET = "Bitbucket";
	private static final String GITLAB = "GitLab";
	private static final String GITHUB = "GitHub";
	// Define tool constants to avoid hardcoded strings
	private static final String TOOL_JIRA = "Jira";
	private static final String TOOL_AZURE = "Azure";
	private static final String TOOL_RALLY = "Rally";

	@Autowired
	private JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;
	@Autowired
	private JiraIssueRepository jiraIssueRepository;
	@Autowired
	private KanbanJiraIssueHistoryRepository kanbanJiraIssueHistoryRepository;
	@Autowired
	private CapacityKpiDataRepository capacityKpiDataRepository;
	@Autowired
	private KanbanCapacityRepository kanbanCapacityRepository;
	@Autowired
	private ConfigHelperService configHelperService;
	@Autowired
	private KPIVideoLinkRepository kpiVideoLinkRepository;
	@Autowired
	private CustomApiConfig customApiConfig;
	@Autowired
	private SprintRepository sprintRepository;
	@Autowired
	private FilterHelperService flterHelperService;
	@Autowired
	private CacheService cacheService;
	@Autowired
	private UserAuthorizedProjectsService authorizedProjectsService;
	@Autowired
	private RepoToolsConfigServiceImpl repoToolsConfigService;

	public static void getDroppedDefectsFilters(Map<String, Map<String, List<String>>> droppedDefects,
			ObjectId basicProjectConfigId, List<String> resolutionTypeForRejection, String jiraDefectRejectionStatus) {
		Map<String, List<String>> filtersMap = new HashMap<>();
		if (CollectionUtils.isNotEmpty(resolutionTypeForRejection)) {
			filtersMap.put(Constant.RESOLUTION_TYPE_FOR_REJECTION, resolutionTypeForRejection);
		}
		if (StringUtils.isNotEmpty(jiraDefectRejectionStatus)) {
			filtersMap.put(Constant.DEFECT_REJECTION_STATUS, List.of(jiraDefectRejectionStatus));
		}
		droppedDefects.put(basicProjectConfigId.toString(), filtersMap);
	}

	public static void getDefectsWithoutDrop(Map<String, Map<String, List<String>>> droppedDefects,
			List<JiraIssue> defectDataList, List<JiraIssue> defectListWoDrop) {
		if (CollectionUtils.isNotEmpty(defectDataList)) {
			Set<JiraIssue> defectListWoDropSet = new HashSet<>();
			defectDataList.forEach(jiraIssue -> getDefectsWoDrop(droppedDefects, defectListWoDropSet, jiraIssue));
			defectListWoDrop.addAll(defectListWoDropSet);
		}
	}

	private static void getDefectsWoDrop(Map<String, Map<String, List<String>>> droppedDefects,
			Set<JiraIssue> defectListWoDropSet, JiraIssue jiraIssue) {
		Map<String, List<String>> defectStatus = droppedDefects.get(jiraIssue.getBasicProjectConfigId());
		if (MapUtils.isNotEmpty(defectStatus)) {
			List<String> rejectedDefect = defectStatus.getOrDefault(Constant.DEFECT_REJECTION_STATUS,
					new ArrayList<>());
			List<String> resolutionTypeForRejection = defectStatus.getOrDefault(Constant.RESOLUTION_TYPE_FOR_REJECTION,
					new ArrayList<>());
			if (!rejectedDefect.contains(jiraIssue.getStatus())
					&& !resolutionTypeForRejection.contains(jiraIssue.getResolution())) {
				defectListWoDropSet.add(jiraIssue);
			}
		} else {
			defectListWoDropSet.add(jiraIssue);
		}
	}

	public static void removeRejectedStoriesFromSprint(List<SprintWiseStory> sprintWiseStories,
			List<JiraIssue> acceptedStories) {

		Set<String> acceptedStoryIds = acceptedStories.stream().map(JiraIssue::getNumber).collect(Collectors.toSet());

		sprintWiseStories.forEach(sprintWiseStory -> sprintWiseStory.getStoryList()
				.removeIf(storyId -> !acceptedStoryIds.contains(storyId)));
	}

	/**
	 * exclude defects with priority and Filter RCA based on fieldMapping
	 *
	 * @param allDefects
	 * @param projectWisePriority
	 * @param projectWiseRCA
	 * @return
	 */
	public static List<JiraIssue> excludePriorityAndIncludeRCA(List<JiraIssue> allDefects,
			Map<String, List<String>> projectWisePriority, Map<String, Set<String>> projectWiseRCA) {
		Set<JiraIssue> defects = new HashSet<>(allDefects);
		List<JiraIssue> remainingDefects = new ArrayList<>();

		for (JiraIssue jiraIssue : defects) {
			String projectId = jiraIssue.getBasicProjectConfigId();
			List<String> priorities = projectWisePriority.getOrDefault(projectId, Collections.emptyList());
			Set<String> rcas = projectWiseRCA.getOrDefault(projectId, Collections.emptySet());

			if ((priorities.isEmpty() || (StringUtils.isNotEmpty(jiraIssue.getPriority())
					&& !priorities.contains(jiraIssue.getPriority().toLowerCase())))
					&& (rcas.isEmpty() || rcas.stream()
							.anyMatch(rca -> jiraIssue.getRootCauseList().contains(rca.toLowerCase())))) {
				remainingDefects.add(jiraIssue);
			}
		}
		return remainingDefects;
	}

	public static void addRCAProjectWise(Map<String, Set<String>> projectWiseRCA, String basicProjectConfigId,
			List<String> excludeRCA) {
		if (CollectionUtils.isNotEmpty(excludeRCA)) {
			Set<String> uniqueRCA = new HashSet<>();
			for (String rca : excludeRCA) {
				if (rca.equalsIgnoreCase(Constant.CODING) || rca.equalsIgnoreCase(Constant.CODE)) {
					rca = Constant.CODE_ISSUE;
				}
				uniqueRCA.add(rca.toLowerCase());
			}
			projectWiseRCA.put(basicProjectConfigId, uniqueRCA);
		}
	}

	public static void addRCAProjectWiseForQualityKPIs(Map<String, Set<String>> projectWiseRCA,
			ObjectId basicProjectConfigId, List<String> excludeRCA) {
		if (CollectionUtils.isNotEmpty(excludeRCA)) {
			Set<String> uniqueRCA = new HashSet<>();
			for (String rca : excludeRCA) {
				if (rca.equalsIgnoreCase(Constant.CODING) || rca.equalsIgnoreCase(Constant.CODE)) {
					rca = Constant.CODE_ISSUE;
				}
				uniqueRCA.add(rca.toLowerCase());
			}
			projectWiseRCA.put(basicProjectConfigId.toString(), uniqueRCA);
		}
	}

	/**
	 * @param projectWisePriority
	 * @param configPriority
	 * @param basicProjectConfigId
	 * @param defectPriority
	 */
	public static void addPriorityProjectWise(Map<String, List<String>> projectWisePriority,
			Map<String, List<String>> configPriority, String basicProjectConfigId, List<String> defectPriority) {
		if (CollectionUtils.isNotEmpty(defectPriority)) {
			List<String> priorValue = defectPriority.stream().map(String::toUpperCase).collect(Collectors.toList());
			if (CollectionUtils.isNotEmpty(priorValue)) {
				List<String> priorityValues = new ArrayList<>();
				priorValue.forEach(priority -> priorityValues.addAll(
						configPriority.get(priority).stream().map(String::toLowerCase).collect(Collectors.toList())));
				projectWisePriority.put(basicProjectConfigId, priorityValues);
			}
		}
	}

	public static void addPriorityProjectWiseForQualityKPIs(Map<String, List<String>> projectWisePriority,
			Map<String, List<String>> configPriority, ObjectId basicProjectConfigId, List<String> defectPriority) {
		if (CollectionUtils.isNotEmpty(defectPriority)) {
			List<String> priorValue = defectPriority.stream().map(String::toUpperCase).collect(Collectors.toList());
			if (CollectionUtils.isNotEmpty(priorValue)) {
				List<String> priorityValues = new ArrayList<>();
				priorValue.forEach(priority -> priorityValues.addAll(
						configPriority.get(priority).stream().map(String::toLowerCase).collect(Collectors.toList())));
				projectWisePriority.put(basicProjectConfigId.toString(), priorityValues);
			}
		}
	}

	/**
	 * Prepares Kpi Elemnts on the basis of kpi master data.
	 *
	 * @param kpiList
	 *            the kpi list
	 */
	public void kpiResolution(List<KpiElement> kpiList) {
		Iterable<KpiMaster> kpiIterable = configHelperService.loadKpiMaster();
		Map<String, KpiMaster> kpiMasterMapping = new HashMap<>();
		kpiIterable.forEach(kpiMaster -> kpiMasterMapping.put(kpiMaster.getKpiId(), kpiMaster));
		kpiList.forEach(kpiElement -> {
			KpiMaster kpiMaster = kpiMasterMapping.get(kpiElement.getKpiId());
			if (null != kpiMaster) {
				kpiElement.setKpiSource(kpiMaster.getKpiSource());
				kpiElement.setKpiName(kpiMaster.getKpiName());
				kpiElement.setUnit(kpiMaster.getKpiUnit());
				kpiElement.setMaxValue(kpiMaster.getMaxValue());
				kpiElement.setKpiCategory(kpiMaster.getKpiCategory());
			}
		});
	}

	/**
	 * Fetchs kpi master list master response.
	 *
	 * @return the master response
	 */
	public MasterResponse fetchKpiMasterList() {

		List<KpiMaster> lisOfKpiMaster = (List<KpiMaster>) configHelperService.loadKpiMaster();
		List<KPIVideoLink> videos = kpiVideoLinkRepository.findAll();
		CollectionUtils.emptyIfNull(lisOfKpiMaster)
				.forEach(kpiMaster -> kpiMaster.setVideoLink(findKpiVideoLink(kpiMaster.getKpiId(), videos)));

		MasterResponse masterResponse = new MasterResponse();
		masterResponse.setKpiList(lisOfKpiMaster);

		return masterResponse;
	}

	private KPIVideoLink findKpiVideoLink(String kpiId, List<KPIVideoLink> videos) {
		if (CollectionUtils.isEmpty(videos)) {
			return null;
		}
		return videos.stream().filter(video -> video.getKpiId().equals(kpiId)).findAny().orElse(null);
	}

	/**
	 * Process story data double.
	 *
	 * @param jiraIssueCustomHistory
	 *            the feature custom history
	 * @param status1
	 *            the status 1
	 * @param status2
	 *            the status 2
	 * @return difference of two date as days
	 */
	public double processStoryData(JiraIssueCustomHistory jiraIssueCustomHistory, String status1, String status2) {
		int storyDataSize = jiraIssueCustomHistory.getStatusUpdationLog().size();
		double daysDifference = -99d;
		if (storyDataSize >= 2 && null != status1 && null != status2) {
			if (status2.equalsIgnoreCase(jiraIssueCustomHistory.getStatusUpdationLog().get(0).getChangedTo())
					&& status1.equalsIgnoreCase(
							jiraIssueCustomHistory.getStatusUpdationLog().get(storyDataSize - 1).getChangedTo())) {
				DateTime closeDate = new DateTime(
						jiraIssueCustomHistory.getStatusUpdationLog().get(0).getUpdatedOn().toString(),
						DateTimeZone.UTC);
				DateTime startDate = new DateTime(
						jiraIssueCustomHistory.getStatusUpdationLog().get(storyDataSize - 1).getUpdatedOn().toString(),
						DateTimeZone.UTC);
				Duration duration = new Duration(startDate, closeDate);
				daysDifference = duration.getStandardDays();
			}
		} else {
			DateTime firstDate = new DateTime(jiraIssueCustomHistory.getCreatedDate().toString(), DateTimeZone.UTC);
			DateTime secondDate = new DateTime(
					jiraIssueCustomHistory.getStatusUpdationLog().get(0).getUpdatedOn().toString(), DateTimeZone.UTC);
			Duration duration = new Duration(firstDate, secondDate);
			daysDifference = duration.getStandardDays();
		}
		return daysDifference;
	}

	/**
	 * This method returns DIR data based upon kpi request and leaf node list.
	 *
	 * @param kpiRequest
	 *            the kpi request
	 * @param sprintList
	 * @return Map of string and object
	 */
	public Map<String, Object> fetchDIRDataFromDb(ObjectId basicProjectConfigID, KpiRequest kpiRequest,
			List<String> sprintList) {

		Map<String, Object> resultListMap = new HashMap<>();
		Map<String, List<String>> mapOfFilters = new LinkedHashMap<>();
		Map<String, List<String>> mapOfFiltersFH = new LinkedHashMap<>();
		List<String> basicProjectConfigIds = new ArrayList<>();
		Map<String, Map<String, Object>> uniqueProjectMapFH = new HashMap<>();
		Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();
		Map<String, Map<String, List<String>>> droppedDefects = new HashMap<>();
		Map<String, List<String>> projectWisePriority = new HashMap<>();
		Map<String, List<String>> configPriority = customApiConfig.getPriority();
		Map<String, Set<String>> projectWiseRCA = new HashMap<>();
		Map<String, Object> mapOfProjectFiltersFH = new LinkedHashMap<>();
		Map<String, Object> mapOfProjectFilters = new LinkedHashMap<>();
		FieldMapping fieldMapping = configHelperService.getFieldMappingMap().get(basicProjectConfigID);
		basicProjectConfigIds.add(basicProjectConfigID.toString());
		addPriorityProjectWiseForQualityKPIs(projectWisePriority, configPriority, basicProjectConfigID,
				fieldMapping.getDefectPriorityKPI14());
		addRCAProjectWiseForQualityKPIs(projectWiseRCA, basicProjectConfigID, fieldMapping.getIncludeRCAForKPI14());

		mapOfProjectFiltersFH.put(JiraFeatureHistory.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
				basicProjectConfigID);
		mapOfProjectFiltersFH.put(JiraFeatureHistory.STORY_TYPE.getFieldValueInFeature(),
				CommonUtils.convertToPatternList(fieldMapping.getJiraDefectInjectionIssueTypeKPI14()));
		mapOfProjectFiltersFH.put("statusUpdationLog.story.changedTo",
				CommonUtils.convertToPatternList(fieldMapping.getJiraDodKPI14()));
		mapOfProjectFiltersFH.put("statusUpdationLog.defect.changedTo", fieldMapping.getJiraDefectCreatedStatusKPI14());
		uniqueProjectMapFH.put(basicProjectConfigID.toString(), mapOfProjectFiltersFH);
		mapOfProjectFilters.put(JiraFeature.ISSUE_TYPE.getFieldValueInFeature(),
				CommonUtils.convertToPatternList(fieldMapping.getJiraDefectInjectionIssueTypeKPI14()));
		if (CollectionUtils.isNotEmpty(fieldMapping.getJiraLabelsKPI14())) {
			mapOfProjectFilters.put(JiraFeature.LABELS.getFieldValueInFeature(),
					CommonUtils.convertToPatternList(fieldMapping.getJiraLabelsKPI14()));
		}
		uniqueProjectMap.put(basicProjectConfigID.toString(), mapOfProjectFilters);
		KpiHelperService.getDroppedDefectsFilters(droppedDefects, basicProjectConfigID,
				fieldMapping.getResolutionTypeForRejectionKPI14(), fieldMapping.getJiraDefectRejectionStatusKPI14());

		KpiDataHelper.createAdditionalFilterMap(kpiRequest, mapOfFilters, Constant.SCRUM, DEV, flterHelperService);

		mapOfFilters.put(JiraFeature.SPRINT_ID.getFieldValueInFeature(),
				sprintList.stream().distinct().collect(Collectors.toList()));
		mapOfFilters.put(JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
				basicProjectConfigIds.stream().distinct().collect(Collectors.toList()));

		List<SprintWiseStory> sprintWiseStoryList = jiraIssueRepository.findIssuesGroupBySprint(mapOfFilters,
				uniqueProjectMap, kpiRequest.getFilterToShowOnTrend(), DEV);
		List<JiraIssue> issuesBySprintAndType = jiraIssueRepository.findIssuesBySprintAndType(mapOfFilters,
				uniqueProjectMap);
		List<JiraIssue> storyListWoDrop = new ArrayList<>();
		KpiHelperService.getDefectsWithoutDrop(droppedDefects, issuesBySprintAndType, storyListWoDrop);
		removeRejectedStoriesFromSprint(sprintWiseStoryList, storyListWoDrop);
		// Filter stories fetched in above query to get stories that have DOD
		// status
		List<String> storyIdList = new ArrayList<>();
		sprintWiseStoryList.forEach(s -> storyIdList.addAll(s.getStoryList()));
		mapOfFiltersFH.put("storyID", storyIdList);
		List<JiraIssueCustomHistory> storyDataList = jiraIssueCustomHistoryRepository
				.findFeatureCustomHistoryStoryProjectWise(mapOfFiltersFH, uniqueProjectMapFH, Sort.Direction.DESC);
		List<String> dodStoryIdList = storyDataList.stream().map(JiraIssueCustomHistory::getStoryID)
				.collect(Collectors.toList());
		sprintWiseStoryList.stream().forEach(story -> {
			List<String> storyNumberList = story.getStoryList().stream().filter(dodStoryIdList::contains)
					.collect(Collectors.toList());
			story.setStoryList(storyNumberList);
		});

		Map<String, List<String>> mapOfFiltersWithStoryIds = new LinkedHashMap<>();
		mapOfFiltersWithStoryIds.put(JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
				basicProjectConfigIds.stream().distinct().collect(Collectors.toList()));
		mapOfFiltersWithStoryIds.put(JiraFeature.DEFECT_STORY_ID.getFieldValueInFeature(), dodStoryIdList);
		mapOfFiltersWithStoryIds.put(JiraFeature.ISSUE_TYPE.getFieldValueInFeature(),
				Collections.singletonList(NormalizedJira.DEFECT_TYPE.getValue()));

		// Fetch Defects linked with story ID's
		List<JiraIssue> defectDataList = jiraIssueRepository.findIssuesByType(mapOfFiltersWithStoryIds);
		List<JiraIssue> defectListWoDrop = new ArrayList<>();
		getDefectsWithoutDrop(droppedDefects, defectDataList, defectListWoDrop);
		resultListMap.put(STORY_DATA, sprintWiseStoryList);
		resultListMap.put(DEFECT_DATA,
				excludePriorityAndIncludeRCA(defectListWoDrop, projectWisePriority, projectWiseRCA));
		resultListMap.put(ISSUE_DATA, jiraIssueRepository.findIssueAndDescByNumber(storyIdList));

		return resultListMap;
	}

	public Map<String, Object> fetchQADDFromDb(ObjectId basicProjectConfigID, KpiRequest kpiRequest,
			List<String> sprintList) {

		Map<String, Object> resultListMap = new HashMap<>();
		Map<String, List<String>> mapOfFilters = new LinkedHashMap<>();
		Map<String, List<String>> mapOfFiltersFH = new LinkedHashMap<>();
		List<String> basicProjectConfigIds = new ArrayList<>();
		Map<String, Map<String, Object>> uniqueProjectMapFH = new HashMap<>();
		Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();
		Map<String, Map<String, List<String>>> droppedDefects = new HashMap<>();
		Map<String, List<String>> projectWisePriority = new HashMap<>();
		Map<String, List<String>> configPriority = customApiConfig.getPriority();
		Map<String, Set<String>> projectWiseRCA = new HashMap<>();
		Map<String, Object> mapOfProjectFiltersFH = new LinkedHashMap<>();
		Map<String, Object> mapOfProjectFilters = new LinkedHashMap<>();
		FieldMapping fieldMapping = configHelperService.getFieldMappingMap().get(basicProjectConfigID);
		basicProjectConfigIds.add(basicProjectConfigID.toString());
		mapOfProjectFiltersFH.put(JiraFeatureHistory.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
				basicProjectConfigID.toString());
		mapOfProjectFiltersFH.put(JiraFeatureHistory.STORY_TYPE.getFieldValueInFeature(),
				CommonUtils.convertToPatternList(fieldMapping.getJiraQAKPI111IssueType()));

		addPriorityProjectWiseForQualityKPIs(projectWisePriority, configPriority, basicProjectConfigID,
				fieldMapping.getDefectPriorityQAKPI111());
		addRCAProjectWiseForQualityKPIs(projectWiseRCA, basicProjectConfigID, fieldMapping.getIncludeRCAForQAKPI111());

		List<String> dodList = fieldMapping.getJiraDodQAKPI111();
		if (CollectionUtils.isNotEmpty(dodList)) {
			mapOfProjectFiltersFH.put("statusUpdationLog.story.changedTo", CommonUtils.convertToPatternList(dodList));
		}
		uniqueProjectMapFH.put(basicProjectConfigID.toString(), mapOfProjectFiltersFH);

		mapOfProjectFilters.put(JiraFeature.ISSUE_TYPE.getFieldValueInFeature(),
				CommonUtils.convertToPatternList(fieldMapping.getJiraQAKPI111IssueType()));
		if (CollectionUtils.isNotEmpty(fieldMapping.getJiraLabelsQAKPI111())) {
			mapOfProjectFilters.put(JiraFeature.LABELS.getFieldValueInFeature(),
					CommonUtils.convertToPatternList(fieldMapping.getJiraLabelsQAKPI111()));
		}
		uniqueProjectMap.put(basicProjectConfigID.toString(), mapOfProjectFilters);
		getDroppedDefectsFilters(droppedDefects, basicProjectConfigID,
				fieldMapping.getResolutionTypeForRejectionQAKPI111(),
				fieldMapping.getJiraDefectRejectionStatusQAKPI111());

		KpiDataHelper.createAdditionalFilterMap(kpiRequest, mapOfFilters, Constant.SCRUM, DEV, flterHelperService);

		mapOfFilters.put(JiraFeature.SPRINT_ID.getFieldValueInFeature(),
				sprintList.stream().distinct().collect(Collectors.toList()));
		mapOfFilters.put(JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
				basicProjectConfigIds.stream().distinct().collect(Collectors.toList()));

		List<SprintWiseStory> sprintWiseStoryList = jiraIssueRepository.findIssuesGroupBySprint(mapOfFilters,
				uniqueProjectMap, kpiRequest.getFilterToShowOnTrend(), DEV);
		List<JiraIssue> issuesBySprintAndType = jiraIssueRepository.findIssuesBySprintAndType(mapOfFilters,
				uniqueProjectMap);
		List<JiraIssue> storyListWoDrop = new ArrayList<>();
		KpiHelperService.getDefectsWithoutDrop(droppedDefects, issuesBySprintAndType, storyListWoDrop);
		removeRejectedStoriesFromSprint(sprintWiseStoryList, storyListWoDrop);
		// Filter stories fetched in above query to get stories that have DOD
		// status
		List<String> storyIdList = new ArrayList<>();
		sprintWiseStoryList.forEach(s -> storyIdList.addAll(s.getStoryList()));
		mapOfFiltersFH.put("storyID", storyIdList);
		List<JiraIssueCustomHistory> storyDataList = jiraIssueCustomHistoryRepository
				.findFeatureCustomHistoryStoryProjectWise(mapOfFiltersFH, uniqueProjectMapFH, Sort.Direction.DESC);
		List<String> dodStoryIdList = storyDataList.stream().map(JiraIssueCustomHistory::getStoryID)
				.collect(Collectors.toList());

		issuesBySprintAndType = issuesBySprintAndType.stream()
				.filter(feature -> dodStoryIdList.contains(feature.getNumber())).collect(Collectors.toList());

		sprintWiseStoryList.forEach(story -> {
			List<String> storyNumberList = story.getStoryList().stream().filter(dodStoryIdList::contains)
					.collect(Collectors.toList());
			story.setStoryList(storyNumberList);
		});
		// remove keys when search defects based on stories
		Map<String, List<String>> mapOfFiltersWithStoryIds = new LinkedHashMap<>();
		mapOfFiltersWithStoryIds.put(JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
				basicProjectConfigIds.stream().distinct().collect(Collectors.toList()));
		mapOfFiltersWithStoryIds.put(JiraFeature.DEFECT_STORY_ID.getFieldValueInFeature(), dodStoryIdList);
		mapOfFiltersWithStoryIds.put(JiraFeature.ISSUE_TYPE.getFieldValueInFeature(),
				Collections.singletonList(NormalizedJira.DEFECT_TYPE.getValue()));

		// Fetch Defects linked with story ID's
		List<JiraIssue> defectDataList = jiraIssueRepository.findIssuesByType(mapOfFiltersWithStoryIds);
		List<JiraIssue> defectListWoDrop = new ArrayList<>();
		getDefectsWithoutDrop(droppedDefects, defectDataList, defectListWoDrop);
		resultListMap.put(STORY_POINTS_DATA, issuesBySprintAndType);
		resultListMap.put(STORY_DATA, sprintWiseStoryList);
		resultListMap.put(DEFECT_DATA,
				excludePriorityAndIncludeRCA(defectListWoDrop, projectWisePriority, projectWiseRCA));

		return resultListMap;
	}

	/**
	 * Fetch sprint velocity data from db map. based upon kpi request and leaf node
	 * list
	 *
	 * @param kpiRequest
	 *            the kpi request
	 * @return map
	 */
	public Map<String, Object> fetchSprintVelocityDataFromDb(KpiRequest kpiRequest, List<String> basicProjectConfigIds,
			List<SprintDetails> sprintDetails) {

		Map<String, List<String>> mapOfFilters = new LinkedHashMap<>();
		Map<String, Object> resultListMap = new HashMap<>();

		List<String> totalIssueIds = new ArrayList<>();
		if (CollectionUtils.isNotEmpty(sprintDetails)) {
			Map<ObjectId, List<SprintDetails>> projectWiseTotalSprintDetails = sprintDetails.stream()
					.collect(Collectors.groupingBy(SprintDetails::getBasicProjectConfigId));

			Map<ObjectId, Set<String>> duplicateIssues = getProjectWiseTotalSprintDetail(projectWiseTotalSprintDetails);
			Map<ObjectId, Map<String, List<LocalDateTime>>> projectWiseDuplicateIssuesWithMinCloseDate = null;
			Map<ObjectId, FieldMapping> fieldMappingMap = configHelperService.getFieldMappingMap();

			if (MapUtils.isNotEmpty(fieldMappingMap) && !duplicateIssues.isEmpty()) {
				Map<ObjectId, List<String>> customFieldMapping = duplicateIssues.keySet().stream()
						.filter(fieldMappingMap::containsKey).collect(Collectors.toMap(Function.identity(), key -> {
							FieldMapping fieldMapping = fieldMappingMap.get(key);
							return Optional.ofNullable(fieldMapping)
									.map(FieldMapping::getJiraIterationCompletionStatusKpi39)
									.orElse(Collections.emptyList());
						}));
				projectWiseDuplicateIssuesWithMinCloseDate = getMinimumClosedDateFromConfiguration(duplicateIssues,
						customFieldMapping);
			}

			Map<ObjectId, Map<String, List<LocalDateTime>>> finalProjectWiseDuplicateIssuesWithMinCloseDate = projectWiseDuplicateIssuesWithMinCloseDate;
			sprintDetails.stream().forEach(dbSprintDetail -> {
				FieldMapping fieldMapping = fieldMappingMap.get(dbSprintDetail.getBasicProjectConfigId());
				// to modify sprintdetails on the basis of configuration for the project
				SprintDetails sprintDetail = KpiDataHelper.processSprintBasedOnFieldMappings(dbSprintDetail,
						fieldMapping.getJiraIterationIssuetypeKPI39(),
						fieldMapping.getJiraIterationCompletionStatusKpi39(),
						finalProjectWiseDuplicateIssuesWithMinCloseDate);
				if (CollectionUtils.isNotEmpty(sprintDetail.getCompletedIssues())) {
					List<String> sprintWiseIssueIds = KpiDataHelper
							.getIssuesIdListBasedOnTypeFromSprintDetails(sprintDetail, CommonConstant.COMPLETED_ISSUES);
					totalIssueIds.addAll(sprintWiseIssueIds);
				}
			});
			mapOfFilters.put(JiraFeature.ISSUE_NUMBER.getFieldValueInFeature(),
					totalIssueIds.stream().distinct().collect(Collectors.toList()));
		}

		/** additional filter * */
		KpiDataHelper.createAdditionalFilterMap(kpiRequest, mapOfFilters, Constant.SCRUM, DEV, flterHelperService);

		mapOfFilters.put(JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
				basicProjectConfigIds.stream().distinct().collect(Collectors.toList()));

		if (CollectionUtils.isNotEmpty(totalIssueIds)) {
			List<JiraIssue> sprintVelocityList = jiraIssueRepository.findIssuesBySprintAndType(mapOfFilters,
					new HashMap<>());

			resultListMap.put(SPRINTVELOCITYKEY, sprintVelocityList);
			resultListMap.put(SPRINT_WISE_SPRINTDETAILS, sprintDetails);
		}

		return resultListMap;
	}

	public Map<String, Object> fetchBackLogReadinessFromdb(KpiRequest kpiRequest, Node projectNode) {

		Map<String, List<String>> mapOfFilters = new LinkedHashMap<>();
		Map<String, Object> resultListMap = new HashMap<>();
		List<String> sprintList = new ArrayList<>();
		Set<String> basicProjectConfigIds = new HashSet<>();

		Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();

		Map<ObjectId, List<String>> projectWiseSprintsForFilter = new LinkedHashMap<>();
		projectWiseSprintsForFilter.put(projectNode.getProjectFilter().getBasicProjectConfigId(),
				kpiRequest.getSelectedMap().get(CommonConstant.SPRINT));
		projectWiseSprintsForFilter.entrySet().forEach(entry -> {
			ObjectId basicProjectConfigId = entry.getKey();
			Map<String, Object> mapOfProjectFilters = new LinkedHashMap<>();
			FieldMapping fieldMapping = configHelperService.getFieldMappingMap().get(basicProjectConfigId);

			sprintList.addAll(entry.getValue());
			basicProjectConfigIds.add(basicProjectConfigId.toString());

			mapOfProjectFilters.put(JiraFeature.ISSUE_TYPE.getFieldValueInFeature(),
					CommonUtils.convertToPatternList(fieldMapping.getJiraSprintVelocityIssueTypeKPI138()));

			mapOfProjectFilters.put(JiraFeature.STATUS.getFieldValueInFeature(),
					CommonUtils.convertToPatternList(fieldMapping.getJiraIssueDeliverdStatusKPI138()));

			uniqueProjectMap.put(basicProjectConfigId.toString(), mapOfProjectFilters);
		});

		List<SprintDetails> sprintDetailList = sprintRepository.findBySprintIDIn(sprintList);
		sprintDetailList.sort(Comparator.comparing(SprintDetails::getStartDate).reversed());
		List<SprintDetails> sprintDetails = sprintDetailList.stream()
				.limit(customApiConfig.getSprintCountForBackLogStrength()).collect(Collectors.toList());
		List<String> totalIssueIds = new ArrayList<>();
		if (CollectionUtils.isNotEmpty(sprintDetails)) {
			sprintDetails.stream().forEach(dbSprintDetail -> {
				FieldMapping fieldMapping = configHelperService.getFieldMappingMap()
						.get(dbSprintDetail.getBasicProjectConfigId());
				// to modify sprintdetails on the basis of configuration for the project
				SprintDetails sprintDetail = KpiDataHelper.processSprintBasedOnFieldMappings(dbSprintDetail,
						fieldMapping.getJiraIterationIssuetypeKPI138(), fieldMapping.getJiraIssueDeliverdStatusKPI138(),
						null);
				if (CollectionUtils.isNotEmpty(sprintDetail.getCompletedIssues())) {
					List<String> sprintWiseIssueIds = KpiDataHelper
							.getIssuesIdListBasedOnTypeFromSprintDetails(sprintDetail, CommonConstant.COMPLETED_ISSUES);
					totalIssueIds.addAll(sprintWiseIssueIds);
				}
			});
			mapOfFilters.put(JiraFeature.ISSUE_NUMBER.getFieldValueInFeature(),
					totalIssueIds.stream().distinct().collect(Collectors.toList()));
		} else {
			mapOfFilters.put(JiraFeature.SPRINT_ID.getFieldValueInFeature(),
					sprintList.stream().distinct().collect(Collectors.toList()));
		}

		/** additional filter * */
		KpiDataHelper.createAdditionalFilterMap(kpiRequest, mapOfFilters, Constant.SCRUM, DEV, flterHelperService);

		mapOfFilters.put(JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
				basicProjectConfigIds.stream().distinct().collect(Collectors.toList()));

		if (CollectionUtils.isNotEmpty(totalIssueIds)) {
			List<JiraIssue> sprintVelocityList = jiraIssueRepository.findIssuesBySprintAndType(mapOfFilters,
					new HashMap<>());
			resultListMap.put(SPRINTVELOCITYKEY, sprintVelocityList);
			resultListMap.put(SPRINT_WISE_SPRINTDETAILS, sprintDetails);
		} else {
			List<JiraIssue> sprintVelocityList = jiraIssueRepository.findIssuesBySprintAndType(mapOfFilters,
					uniqueProjectMap);
			resultListMap.put(SPRINTVELOCITYKEY, sprintVelocityList);
			resultListMap.put(SPRINT_WISE_SPRINTDETAILS, null);
		}

		return resultListMap;
	}

	/**
	 * Fetches sprint capacity data from db based upon leaf node list.
	 *
	 * @param leafNodeList
	 *            the leaf node list
	 * @return the list
	 */
	public Map<String, Object> fetchSprintCapacityDataFromDb(KpiRequest kpiRequest, List<Node> leafNodeList) {

		Map<String, List<String>> mapOfFilters = new LinkedHashMap<>();

		List<String> sprintList = new ArrayList<>();
		List<String> basicProjectConfigIds = new ArrayList<>();

		Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();
		Map<String, Map<String, Object>> uniqueProjectMapForSubTask = new HashMap<>();
		Map<String, Object> resultListMap = new HashMap<>();

		/** additional filter * */
		KpiDataHelper.createAdditionalFilterMap(kpiRequest, mapOfFilters, Constant.SCRUM, CommonConstant.QA,
				flterHelperService);
		leafNodeList.forEach(leaf -> {
			ObjectId basicProjectConfigId = leaf.getProjectFilter().getBasicProjectConfigId();
			Map<String, Object> mapOfProjectFilters = new LinkedHashMap<>();
			Map<String, Object> mapOfProjectFiltersForSubTask = new LinkedHashMap<>();

			FieldMapping fieldMapping = configHelperService.getFieldMappingMap().get(basicProjectConfigId);

			List<String> capacityIssueType = fieldMapping.getJiraSprintCapacityIssueTypeKpi46();
			if (CollectionUtils.isEmpty(capacityIssueType)) {
				capacityIssueType = new ArrayList<>();
				capacityIssueType.add("Story");
			}

			List<String> taskType = fieldMapping.getJiraSubTaskIdentification();
			sprintList.add(leaf.getSprintFilter().getId());
			basicProjectConfigIds.add(basicProjectConfigId.toString());

			mapOfProjectFilters.put(JiraFeature.ISSUE_TYPE.getFieldValueInFeature(),
					CommonUtils.convertToPatternList(capacityIssueType));
			mapOfProjectFilters.putAll(mapOfFilters);
			mapOfProjectFiltersForSubTask.put(JiraFeature.ORIGINAL_ISSUE_TYPE.getFieldValueInFeature(),
					CommonUtils.convertToPatternList(taskType));
			uniqueProjectMap.put(basicProjectConfigId.toString(), mapOfProjectFilters);
			uniqueProjectMapForSubTask.put(basicProjectConfigId.toString(), mapOfProjectFiltersForSubTask);
		});

		List<SprintDetails> sprintDetails = sprintRepository.findBySprintIDIn(sprintList);
		Set<String> totalIssue = new HashSet<>();
		sprintDetails.forEach(dbSprintDetail -> {
			if (CollectionUtils.isNotEmpty(dbSprintDetail.getTotalIssues())) {
				totalIssue.addAll(KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(dbSprintDetail,
						CommonConstant.TOTAL_ISSUES));
			}
		});

		if (CollectionUtils.isNotEmpty(totalIssue)) {
			List<JiraIssue> jiraIssueList = jiraIssueRepository.findIssueByNumberOrParentStoryIdAndType(totalIssue,
					uniqueProjectMap, CommonConstant.NUMBER);
			List<JiraIssue> subTaskList = jiraIssueRepository.findIssueByNumberOrParentStoryIdAndType(
					jiraIssueList.stream().map(JiraIssue::getNumber).collect(Collectors.toSet()),
					uniqueProjectMapForSubTask, CommonConstant.PARENT_STORY_ID);
			List<JiraIssue> jiraIssues = new ArrayList<>();
			jiraIssues.addAll(subTaskList);
			jiraIssues.addAll(jiraIssueList);
			List<JiraIssueCustomHistory> jiraIssueCustomHistoryList = jiraIssueCustomHistoryRepository
					.findByStoryIDInAndBasicProjectConfigIdIn(jiraIssues.stream().map(JiraIssue::getNumber).toList(),
							basicProjectConfigIds.stream().distinct().collect(Collectors.toList()));
			resultListMap.put(STORY_LIST, jiraIssues);
			resultListMap.put(SPRINTSDETAILS, sprintDetails);
			resultListMap.put(JIRA_ISSUE_HISTORY_DATA, jiraIssueCustomHistoryList);
		}

		return resultListMap;
	}

	/**
	 * Fetch capacity data from db based upon leaf node list.
	 *
	 * @param leafNodeList
	 *            the leaf node list
	 * @return list
	 */
	public List<CapacityKpiData> fetchCapacityDataFromDB(KpiRequest kpiRequest, List<Node> leafNodeList) {
		Map<String, Object> mapOfFilters = new LinkedHashMap<>();
		List<String> sprintList = new ArrayList<>();
		List<ObjectId> basicProjectConfigIds = new ArrayList<>();
		Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();
		leafNodeList.forEach(leaf -> {
			ObjectId basicProjectConfigId = leaf.getProjectFilter().getBasicProjectConfigId();
			sprintList.add(leaf.getSprintFilter().getId());
			basicProjectConfigIds.add(basicProjectConfigId);
		});

		/** additional filter * */
		KpiDataHelper.createAdditionalFilterMapForCapacity(kpiRequest, mapOfFilters, flterHelperService);

		mapOfFilters.put(JiraFeature.SPRINT_ID.getFieldValueInFeature(),
				sprintList.stream().distinct().collect(Collectors.toList()));
		mapOfFilters.put(JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
				basicProjectConfigIds.stream().distinct().collect(Collectors.toList()));
		return capacityKpiDataRepository.findByFilters(mapOfFilters, uniqueProjectMap);
	}

	/**
	 * Fetch ticket velocity data from db based upon leaf node list within range of
	 * start date and end date.
	 *
	 * @param leafNodeList
	 *            the leaf node list
	 * @param startDate
	 *            the start date
	 * @param endDate
	 *            the end date
	 * @return {@code Map<String ,Object> map}
	 */
	public Map<String, Object> fetchTicketVelocityDataFromDb(List<Node> leafNodeList, String startDate,
			String endDate) {

		Map<String, List<String>> mapOfFilters = new LinkedHashMap<>();
		Map<String, Object> resultListMap = new HashMap<>();
		List<String> projectList = new ArrayList<>();
		Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();

		leafNodeList.forEach(leaf -> {
			ObjectId basicProjectConfigId = leaf.getProjectFilter().getBasicProjectConfigId();
			Map<String, Object> mapOfProjectFilters = new LinkedHashMap<>();
			FieldMapping fieldMapping = configHelperService.getFieldMappingMap().get(basicProjectConfigId);

			projectList.add(basicProjectConfigId.toString());
			if (Optional.ofNullable(fieldMapping.getJiraTicketVelocityIssueTypeKPI49()).isPresent()) {
				mapOfProjectFilters.put(JiraFeatureHistory.STORY_TYPE.getFieldValueInFeature(),
						CommonUtils.convertToPatternList(fieldMapping.getJiraTicketVelocityIssueTypeKPI49()));
			}
			mapOfProjectFilters.put(JiraFeatureHistory.HISTORY_STATUS.getFieldValueInFeature(),
					CommonUtils.convertToPatternList(fieldMapping.getTicketDeliveredStatusKPI49()));
			uniqueProjectMap.put(basicProjectConfigId.toString(), mapOfProjectFilters);
		});
		// Add list of subprojects in project wise filters

		String subGroupCategory = Constant.DATE;

		mapOfFilters.put(JiraFeatureHistory.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
				projectList.stream().distinct().collect(Collectors.toList()));

		List<KanbanIssueCustomHistory> dateVelocityList = kanbanJiraIssueHistoryRepository
				.findIssuesByStatusAndDate(mapOfFilters, uniqueProjectMap, startDate, endDate, IN);
		resultListMap.put(TICKETVELOCITYKEY, dateVelocityList);
		resultListMap.put(SUBGROUPCATEGORY, subGroupCategory);
		return resultListMap;
	}

	/**
	 * Fetch team capacity data from db map.
	 *
	 * @param leafNodeList
	 *            the leaf node list
	 * @param startDate
	 *            the start date
	 * @param endDate
	 *            the end date
	 * @param kpiRequest
	 *            the kpi request
	 * @param capacityKey
	 *            the capacity key
	 * @return the map
	 */
	public Map<String, Object> fetchTeamCapacityDataFromDb(List<Node> leafNodeList, String startDate, String endDate,
			KpiRequest kpiRequest, String capacityKey) {
		Map<String, Object> resultListMap = new HashMap<>();
		Map<String, Object> mapOfFilters = new LinkedHashMap<>();
		List<ObjectId> projectList = new ArrayList<>();
		leafNodeList.forEach(leaf -> projectList.add(leaf.getProjectFilter().getBasicProjectConfigId()));
		/** additional filter * */
		KpiDataHelper.createAdditionalFilterMapForCapacity(kpiRequest, mapOfFilters, flterHelperService);
		mapOfFilters.put(JiraFeatureHistory.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
				projectList.stream().distinct().collect(Collectors.toList()));

		resultListMap.put(capacityKey, kanbanCapacityRepository.findIssuesByType(mapOfFilters, startDate, endDate));
		resultListMap.put(SUBGROUPCATEGORY, Constant.DATE);
		return resultListMap;
	}

	/**
	 * Convert string to date local date.
	 *
	 * @param dateString
	 *            the date string
	 * @return the local date
	 */
	public LocalDate convertStringToDate(String dateString) {
		return LocalDate.parse(dateString);
	}

	/**
	 * fetching jira from jiraKanbanhistory for last 15 months and also returning
	 * fieldmapping for closed and open tickets from jira mapping
	 *
	 * @param leafNodeList
	 * @param startDate
	 * @param endDate
	 * @param kpiRequest
	 * @param projectWiseMapping
	 * @return
	 */
	public Map<String, Object> fetchJiraCustomHistoryDataFromDbForKanban(List<Node> leafNodeList, String startDate,
			String endDate, KpiRequest kpiRequest, String fieldName,
			Map<ObjectId, Map<String, Object>> projectWiseMapping) {
		Map<String, Object> resultListMap = new HashMap<>();
		Map<String, List<String>> mapOfFilters = new LinkedHashMap<>();
		Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();
		Map<String, List<String>> projectWiseClosedStatusMap = new HashMap<>();
		Map<String, String> projectWiseOpenStatusMap = new HashMap<>();
		Map<String, List<String>> projectWiseIssueTypeMap = new HashMap<>();
		List<String> projectList = new ArrayList<>();
		leafNodeList.forEach(leaf -> {
			ObjectId basicProjectConfigId = leaf.getProjectFilter().getBasicProjectConfigId();
			Map<String, Object> mapOfProjectFilters = new LinkedHashMap<>();

			Map<String, Object> fieldWiseMapping = projectWiseMapping.get(basicProjectConfigId);
			projectList.add(basicProjectConfigId.toString());

			setJiraIssueType(fieldName, projectWiseIssueTypeMap, leaf, mapOfProjectFilters, fieldWiseMapping);
			setJiraClosedStatusMap(projectWiseClosedStatusMap, leaf, fieldWiseMapping);

			if (Optional.ofNullable(fieldWiseMapping.get("StoryFirstStatus")).isPresent()) {
				projectWiseOpenStatusMap.put(basicProjectConfigId.toString(),
						(String) fieldWiseMapping.get("StoryFirstStatus"));
			}

			uniqueProjectMap.put(basicProjectConfigId.toString(), mapOfProjectFilters);
		});
		String subGroupCategory = KpiDataHelper.createAdditionalFilterMap(kpiRequest, mapOfFilters, Constant.KANBAN,
				DEV, flterHelperService);
		mapOfFilters.put(JiraFeatureHistory.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
				projectList.stream().distinct().collect(Collectors.toList()));

		List<KanbanIssueCustomHistory> issuesByCreatedDateAndType = kanbanJiraIssueHistoryRepository
				.findIssuesByCreatedDateAndType(mapOfFilters, uniqueProjectMap, startDate, endDate);
		resultListMap.put(SUBGROUPCATEGORY, subGroupCategory);
		resultListMap.put(PROJECT_WISE_ISSUE_TYPES, projectWiseIssueTypeMap);
		resultListMap.put(PROJECT_WISE_CLOSED_STORY_STATUS, projectWiseClosedStatusMap);
		resultListMap.put(PROJECT_WISE_OPEN_STORY_STATUS, projectWiseOpenStatusMap);
		resultListMap.put(JIRA_ISSUE_HISTORY_DATA, issuesByCreatedDateAndType);
		return resultListMap;
	}

	private void setJiraIssueType(String fieldName, Map<String, List<String>> projectWiseIssueTypeMap, Node leaf,
			Map<String, Object> mapOfProjectFilters, Map<String, Object> fieldWiseMapping) {
		if (FIELD_RCA.equals(fieldName)) {
			if (Optional.ofNullable(fieldWiseMapping.get("RCA_Count_IssueType")).isPresent()) {
				List<String> rcaFieldMappingIssueType = (List<String>) fieldWiseMapping.get("RCA_Count_IssueType");
				mapOfProjectFilters.put(JiraFeatureHistory.STORY_TYPE.getFieldValueInFeature(),
						CommonUtils.convertToPatternList(rcaFieldMappingIssueType));
				projectWiseIssueTypeMap.put(leaf.getProjectFilter().getBasicProjectConfigId().toString(),
						rcaFieldMappingIssueType.stream().distinct().collect(Collectors.toList()));
			}
		} else {
			if (Optional.ofNullable(fieldWiseMapping.get("Ticket_Count_IssueType")).isPresent()) {
				List<String> ticketCountIssueType = (List<String>) fieldWiseMapping.get("Ticket_Count_IssueType");
				mapOfProjectFilters.put(JiraFeatureHistory.STORY_TYPE.getFieldValueInFeature(),
						CommonUtils.convertToPatternList(ticketCountIssueType));
				projectWiseIssueTypeMap.put(leaf.getProjectFilter().getBasicProjectConfigId().toString(),
						ticketCountIssueType.stream().distinct().collect(Collectors.toList()));
			}
		}
	}

	private void setJiraClosedStatusMap(Map<String, List<String>> projectWiseClosedStatusMap, Node leaf,
			Map<String, Object> fieldWiseMapping) {
		if (Optional.ofNullable(fieldWiseMapping.get("ClosedStatus")).isPresent()) {
			List<String> closedStatusList = new ArrayList<>();
			closedStatusList.addAll((List<String>) fieldWiseMapping.get("ClosedStatus"));
			if (Optional.ofNullable(fieldWiseMapping.get("LiveStatus")).isPresent()) {
				closedStatusList.add((String) fieldWiseMapping.get("LiveStatus"));
			}
			if (Optional.ofNullable(fieldWiseMapping.get("RejectedStatus")).isPresent()) {
				closedStatusList.addAll((List<String>) fieldWiseMapping.get("RejectedStatus"));
			}
			projectWiseClosedStatusMap.put(leaf.getProjectFilter().getBasicProjectConfigId().toString(),
					closedStatusList.stream().distinct().collect(Collectors.toList()));
		}
	}

	/**
	 * returning all non-closed tickets from history data project wise the list will
	 * contain the reopen tickets within the range or outside the range if the
	 * current status of that ticket is not close the list will not contain those
	 * tickets which were closed before the filtered range
	 *
	 * @param resultListMap
	 * @param startDate
	 * @return
	 */
	public Map<String, List<KanbanIssueCustomHistory>> removeClosedTicketsFromHistoryIssuesData(
			Map<String, Object> resultListMap, String startDate) {

		List<KanbanIssueCustomHistory> nonClosedTicketsList = new ArrayList<>();
		List<KanbanIssueCustomHistory> jiraIssueHistoryDataList = (List<KanbanIssueCustomHistory>) resultListMap
				.get(JIRA_ISSUE_HISTORY_DATA);
		Map<String, List<String>> projectWiseClosedStoryStatus = (Map<String, List<String>>) resultListMap
				.get(PROJECT_WISE_CLOSED_STORY_STATUS);
		Map<String, String> projectWiseOpenStoryStatus = (Map<String, String>) resultListMap
				.get(PROJECT_WISE_OPEN_STORY_STATUS);

		jiraIssueHistoryDataList.stream().forEach(issueCustomHistory -> {
			boolean isTicketAdded = false;
			List<String> jiraClosedStatusList = projectWiseClosedStoryStatus
					.get(issueCustomHistory.getBasicProjectConfigId());
			List<String> nonClosedStatusList = new ArrayList<>();
			if (CollectionUtils.isNotEmpty(issueCustomHistory.getHistoryDetails())) {
				prepareClosedListHistoryDetailsWise(startDate, nonClosedTicketsList, issueCustomHistory, isTicketAdded,
						jiraClosedStatusList, nonClosedStatusList);
			} else {
				// checking if history details is empty then we should atleast
				// have one history
				// detail status with an Open status
				KanbanIssueHistory history = new KanbanIssueHistory();
				history.setStatus(projectWiseOpenStoryStatus.getOrDefault(issueCustomHistory.getBasicProjectConfigId(),
						CommonConstant.OPEN));
				history.setActivityDate(issueCustomHistory.getCreatedDate());
				List<KanbanIssueHistory> historyList = new ArrayList<>();
				historyList.add(history);
				issueCustomHistory.setHistoryDetails(historyList);
				nonClosedTicketsList.add(issueCustomHistory);
			}
		});
		return KpiDataHelper.createProjectWiseMapKanbanHistory(nonClosedTicketsList,
				(String) resultListMap.get(SUBGROUPCATEGORY), flterHelperService);
	}

	/**
	 * @param startDate
	 * @param nonClosedTicketsList
	 * @param issueCustomHistory
	 * @param isTicketAdded
	 * @param jiraClosedStatusList
	 * @param nonClosedStatusList
	 */
	private void prepareClosedListHistoryDetailsWise(String startDate,
			List<KanbanIssueCustomHistory> nonClosedTicketsList, KanbanIssueCustomHistory issueCustomHistory,
			boolean isTicketAdded, List<String> jiraClosedStatusList, List<String> nonClosedStatusList) {
		List<KanbanIssueHistory> statusHistoryDetailsList = issueCustomHistory.getHistoryDetails();
		for (int i = statusHistoryDetailsList.size() - 1; i >= 0; i--) {
			KanbanIssueHistory issueStatusHistory = statusHistoryDetailsList.get(i);
			/*
			 * to check the recent status from history details in case of reopen
			 * nonClosedStatusList will have more status before closed status under that
			 * scenario the ticket will be counted
			 */

			if (!jiraClosedStatusList.contains(issueStatusHistory.getStatus())) {
				nonClosedStatusList.add(issueStatusHistory.getStatus());
			}
			if (checkConditionForClosedStatusTickets(issueStatusHistory.getStatus(), jiraClosedStatusList,
					issueStatusHistory.getActivityDate(), startDate, nonClosedStatusList)) {
				break;
			}

			if (!isTicketAdded) {
				nonClosedTicketsList.add(issueCustomHistory);
				isTicketAdded = true;
			}
		}
	}

	/**
	 * checking if from history details for a particular story contains the closed
	 * status type from fieldmapping or (the activity status is more than the
	 * selected start time of filter) and (that status be the latest status from
	 * history details) then wil return true
	 *
	 * @param historyStatus
	 * @param jiraClosedStatusList
	 * @param updatedOn
	 * @param startDate
	 * @param nonClosedStatusList
	 * @return
	 */
	public boolean checkConditionForClosedStatusTickets(String historyStatus, List<String> jiraClosedStatusList,
			String updatedOn, String startDate, List<String> nonClosedStatusList) {
		LocalDateTime activityLocalDate = LocalDateTime.parse(updatedOn.split("\\.")[0], DATE_TIME_FORMATTER);
		LocalDateTime startLocalDate = LocalDateTime.parse(LocalDate.parse(startDate).atTime(23, 59, 59).toString());
		return jiraClosedStatusList.contains(historyStatus) && activityLocalDate.isBefore(startLocalDate)
				&& CollectionUtils.isEmpty(nonClosedStatusList);
	}

	/**
	 * the non closed stories are processed according to status
	 *
	 * @param projectWiseNonClosedTickets
	 * @param startDate
	 * @param historyDataResultMap
	 * @return
	 */
	public Map<String, Map<String, Map<String, Set<String>>>> computeProjectWiseJiraHistoryByStatusAndDate(
			Map<String, List<KanbanIssueCustomHistory>> projectWiseNonClosedTickets, String startDate,
			Map<String, Object> historyDataResultMap) {
		Map<String, Map<String, Map<String, Set<String>>>> projectWiseJiraHistoryStatusAndDateWiseIssueMap = new HashMap<>();
		Map<String, String> projectWiseOpenStatus = (Map<String, String>) historyDataResultMap
				.get(PROJECT_WISE_OPEN_STORY_STATUS);
		projectWiseNonClosedTickets.entrySet().stream().forEach(nonClosedTicketsList -> {
			String openStatusFromFieldMapping = projectWiseOpenStatus.getOrDefault(nonClosedTicketsList.getKey(),
					CommonConstant.OPEN);
			Map<String, Map<String, Set<String>>> jiraHistoryStatusAndDateWiseIssueMap = new HashMap<>();
			for (KanbanIssueCustomHistory issueCustomHistory : nonClosedTicketsList.getValue()) {
				// if all activity date are before the filter range then this
				// flag will remain
				// true
				boolean dateLessThanStartDate = true;
				// for every ticket this status will always be set to null for
				// first time
				String status = null;
				LocalDate startLocalDateTemp = LocalDate.parse(startDate);
				List<KanbanIssueHistory> statusHistoryDetailsList = issueCustomHistory.getHistoryDetails();
				if (CollectionUtils.isNotEmpty(statusHistoryDetailsList)) {
					for (KanbanIssueHistory statusList : statusHistoryDetailsList) {
						String currentStatus = statusList.getStatus().equals("") ? openStatusFromFieldMapping
								: statusList.getStatus();
						LocalDate activityLocalDate = LocalDate.parse(statusList.getActivityDate().split("\\.")[0],
								DATE_TIME_FORMATTER);
						/*
						 * check if ticket's latest activity was before the filter's start time then
						 * will consider that ticket and latest status in selected filter range
						 * cumulative way otherwise will move into the loop
						 */
						if (activityLocalDate.isEqual(startLocalDateTemp)
								|| activityLocalDate.isAfter(startLocalDateTemp)) {
							if (status == null) {
								/*
								 * when no change in status happened after the creation date of ticket
								 */
								status = currentStatus;
								startLocalDateTemp = activityLocalDate;
								populateJiraHistoryFieldAndDateWiseIssues(startLocalDateTemp.toString(), status,
										issueCustomHistory.getStoryID(), jiraHistoryStatusAndDateWiseIssueMap);
							} else {
								/*
								 * if within a ticket some history details are before the filter range and
								 * others are within the filter range and status change happened on the same day
								 */
								if (startLocalDateTemp.isEqual(activityLocalDate)) {
									status = currentStatus;
									populateJiraHistoryFieldAndDateWiseIssues(startLocalDateTemp.toString(), status,
											issueCustomHistory.getStoryID(), jiraHistoryStatusAndDateWiseIssueMap);
								} else {
									/*
									 * if within a ticket some history details are before the filter range and
									 * others are within the filter range and status change happened on the
									 * different day or the status remain consistent for some days
									 */
									for (LocalDate loopStartDate = startLocalDateTemp; loopStartDate
											.isBefore(activityLocalDate)
											|| loopStartDate.isEqual(activityLocalDate); loopStartDate = loopStartDate
													.plusDays(1)) {
										if (loopStartDate.isEqual(activityLocalDate)) {
											status = currentStatus;
										}
										populateJiraHistoryFieldAndDateWiseIssues(loopStartDate.toString(), status,
												issueCustomHistory.getStoryID(), jiraHistoryStatusAndDateWiseIssueMap);
									}
									startLocalDateTemp = activityLocalDate;
								}
								dateLessThanStartDate = false;
							}
						}
						// if activity date is less than the filter range then
						// just update the status
						status = statusList.getStatus().equals("") ? openStatusFromFieldMapping
								: statusList.getStatus();
					}

					LocalDate endDate = LocalDate.now();
					/**
					 * when all activity dates are less than the filter range or when the last
					 * status's activity date between the fiilter range loop will run from last
					 * activity date till today's date for cumulative sum
					 */
					if (dateLessThanStartDate || startLocalDateTemp.isBefore(endDate)) {
						for (LocalDate loopStartDate = startLocalDateTemp; loopStartDate.isBefore(endDate)
								|| loopStartDate.isEqual(endDate); loopStartDate = loopStartDate.plusDays(1)) {
							populateJiraHistoryFieldAndDateWiseIssues(loopStartDate.toString(), status,
									issueCustomHistory.getStoryID(), jiraHistoryStatusAndDateWiseIssueMap);
						}
					}
				}
			}
			projectWiseJiraHistoryStatusAndDateWiseIssueMap.put(nonClosedTicketsList.getKey(),
					jiraHistoryStatusAndDateWiseIssueMap);
		});
		return projectWiseJiraHistoryStatusAndDateWiseIssueMap;
	}

	/**
	 * will create a map of Map<field,Map<Date,List of issues>> field could be rca,
	 * priority or status for each status at passed time what all tickets to be
	 * considered
	 *
	 * @param startDate
	 * @param fieldValue
	 * @param storyId
	 * @param jiraHistoryFieldAndDateWiseIssueMap
	 */
	public void populateJiraHistoryFieldAndDateWiseIssues(String startDate, String fieldValue, String storyId,
			Map<String, Map<String, Set<String>>> jiraHistoryFieldAndDateWiseIssueMap) {
		// if field value is already present in the map then we have to add the
		// story in
		// the already present list of stories
		jiraHistoryFieldAndDateWiseIssueMap.computeIfPresent(fieldValue, (key, value) -> {
			populateJiraDateWiseIssues(startDate, storyId, value);
			return value;
		});

		jiraHistoryFieldAndDateWiseIssueMap.computeIfAbsent(fieldValue, value -> {
			Map<String, Set<String>> dateWiseIssues = new HashMap<>();
			populateJiraDateWiseIssues(startDate, storyId, dateWiseIssues);
			return dateWiseIssues;
		});
	}

	/**
	 * depending upon the startdate passed, we need to create story list for that
	 * particular field field can be status/rca/priority
	 *
	 * @param startDate
	 * @param storyId
	 * @param jiraHistoryDateWiseIssuesMap
	 */
	public void populateJiraDateWiseIssues(String startDate, String storyId,
			Map<String, Set<String>> jiraHistoryDateWiseIssuesMap) {
		jiraHistoryDateWiseIssuesMap.computeIfPresent(startDate, (key, value) -> {
			value.add(storyId);
			return value;
		});

		jiraHistoryDateWiseIssuesMap.computeIfAbsent(startDate, value -> {
			Set<String> issueIIds = new HashSet<>();
			issueIIds.add(storyId);
			return issueIIds;
		});
	}

	/**
	 * the non closed stories are processed according to the field can be
	 * rca/priority
	 *
	 * @param projectWiseNonClosedTickets
	 * @param startDate
	 * @param resultListMap
	 * @param fieldName
	 * @return
	 */
	public Map<String, Map<String, Map<String, Set<String>>>> computeProjectWiseJiraHistoryByFieldAndDate(
			Map<String, List<KanbanIssueCustomHistory>> projectWiseNonClosedTickets, String startDate,
			Map<String, Object> resultListMap, String fieldName) {
		Map<String, Map<String, Map<String, Set<String>>>> projectWiseJiraHistoryFieldAndDateWiseIssueMap = new HashMap<>();
		Map<String, List<String>> projectWiseClosedStoryStatus = (Map<String, List<String>>) resultListMap
				.get(PROJECT_WISE_CLOSED_STORY_STATUS);
		Map<String, String> projectWiseOpenStoryStatus = (Map<String, String>) resultListMap
				.get(PROJECT_WISE_OPEN_STORY_STATUS);

		projectWiseNonClosedTickets.entrySet().stream().forEach(nonClosedTicketsList -> {
			String openStatusFromFieldMapping = projectWiseOpenStoryStatus.getOrDefault(nonClosedTicketsList.getKey(),
					CommonConstant.OPEN);
			Map<String, Map<String, Set<String>>> jiraHistoryStatusAndDateWiseIssueMap = new HashMap<>();
			for (KanbanIssueCustomHistory issueCustomHistory : nonClosedTicketsList.getValue()) {
				// if all activity date are before the filter range then this
				// flag will remain
				// true
				boolean dateLessThanStartDate = true;
				// for every ticket this status will always be set to null for
				// first time
				String status = null;
				List<String> jiraClosedStatusList = projectWiseClosedStoryStatus
						.get(issueCustomHistory.getBasicProjectConfigId());
				LocalDate startLocalDateTemp = LocalDate.parse(startDate);
				String fieldValues = basedOnKPIFieldNameFetchValues(fieldName, issueCustomHistory);
				List<KanbanIssueHistory> statusHistoryDetailsList = issueCustomHistory.getHistoryDetails();
				if (CollectionUtils.isNotEmpty(statusHistoryDetailsList) && StringUtils.isNotEmpty(fieldValues)) {
					for (KanbanIssueHistory statusList : statusHistoryDetailsList) {
						String currentStatus = statusList.getStatus().equals("") ? openStatusFromFieldMapping
								: statusList.getStatus();
						LocalDate activityLocalDate = LocalDate.parse(statusList.getActivityDate().split("\\.")[0],
								DATE_TIME_FORMATTER);
						/*
						 * check if ticket's latest activity was before the filter's start time then
						 * will consider that ticket and latest status in selected filter range
						 * cumulative way otherwise will move into the loop
						 */
						if ((activityLocalDate.isEqual(startLocalDateTemp)
								|| activityLocalDate.isAfter(startLocalDateTemp))) {
							if (status == null) {
								/*
								 * when no change in status happened after the creation date of ticket
								 */
								status = currentStatus;
								startLocalDateTemp = activityLocalDate;
								checkStatusAndPopulateJiraHistoryFieldAndDateWiseIssues(
										jiraHistoryStatusAndDateWiseIssueMap, issueCustomHistory, status,
										jiraClosedStatusList, fieldValues, startLocalDateTemp);
							} else {
								/*
								 * if within a ticket some history details are before the filter range and
								 * others are within the filter range and status change happened on the same day
								 */
								if (startLocalDateTemp.isEqual(activityLocalDate)) {
									status = currentStatus;
									checkStatusAndPopulateJiraHistoryFieldAndDateWiseIssues(
											jiraHistoryStatusAndDateWiseIssueMap, issueCustomHistory, status,
											jiraClosedStatusList, fieldValues, startLocalDateTemp);
								} else {
									/*
									 * if within a ticket some history details are before the filter range and
									 * others are within the filter range and status change happened on the
									 * different day or the status remain consistent for some days
									 */
									for (LocalDate loopStartDate = startLocalDateTemp; loopStartDate
											.isBefore(activityLocalDate)
											|| loopStartDate.isEqual(activityLocalDate); loopStartDate = loopStartDate
													.plusDays(1)) {
										if (loopStartDate.isEqual(activityLocalDate)) {
											status = currentStatus;
										}
										checkStatusAndPopulateJiraHistoryFieldAndDateWiseIssues(
												jiraHistoryStatusAndDateWiseIssueMap, issueCustomHistory, status,
												jiraClosedStatusList, fieldValues, loopStartDate);
									}
									startLocalDateTemp = activityLocalDate;
								}
								dateLessThanStartDate = false;
							}
						}
						// if activity date is less than the filter range then
						// just update the status
						status = statusList.getStatus().equals("") ? openStatusFromFieldMapping
								: statusList.getStatus();
					}

					LocalDate endDate = LocalDate.now();
					/**
					 * when all activity dates are less than the filter range or when the last
					 * status's activity date between the fiilter range loop will run from last
					 * activity date till today's date for cumulative sum
					 */
					if (!jiraClosedStatusList.contains(status)
							&& (dateLessThanStartDate || startLocalDateTemp.isBefore(endDate))) {
						for (LocalDate loopStartDate = startLocalDateTemp; loopStartDate.isBefore(endDate)
								|| loopStartDate.isEqual(endDate); loopStartDate = loopStartDate.plusDays(1)) {
							populateJiraHistoryFieldAndDateWiseIssues(loopStartDate.toString(), fieldValues,
									issueCustomHistory.getStoryID(), jiraHistoryStatusAndDateWiseIssueMap);
						}
					}
				}
			}
			projectWiseJiraHistoryFieldAndDateWiseIssueMap.put(nonClosedTicketsList.getKey(),
					jiraHistoryStatusAndDateWiseIssueMap);
		});
		return projectWiseJiraHistoryFieldAndDateWiseIssueMap;
	}

	/**
	 * if status is contains in closed jira list then we did not populate
	 *
	 * @param jiraHistoryStatusAndDateWiseIssueMap
	 * @param issueCustomHistory
	 * @param status
	 * @param jiraClosedStatusList
	 * @param fieldValues
	 * @param loopStartDate
	 */
	private void checkStatusAndPopulateJiraHistoryFieldAndDateWiseIssues(
			Map<String, Map<String, Set<String>>> jiraHistoryStatusAndDateWiseIssueMap,
			KanbanIssueCustomHistory issueCustomHistory, String status, List<String> jiraClosedStatusList,
			String fieldValues, LocalDate loopStartDate) {
		if (!jiraClosedStatusList.contains(status)) {
			populateJiraHistoryFieldAndDateWiseIssues(loopStartDate.toString(), fieldValues,
					issueCustomHistory.getStoryID(), jiraHistoryStatusAndDateWiseIssueMap);
		}
	}

	/**
	 * based on field name fetch values from db
	 *
	 * @param fieldName
	 * @param issueCustomHistory
	 * @return
	 */
	private String basedOnKPIFieldNameFetchValues(String fieldName, KanbanIssueCustomHistory issueCustomHistory) {
		if (fieldName.equals(FIELD_PRIORITY)) {
			return KPIHelperUtil.mappingPriority(issueCustomHistory.getPriority(), customApiConfig);
		} else if (fieldName.equals(FIELD_RCA) && CollectionUtils.isNotEmpty(issueCustomHistory.getRootCauseList())) {
			return StringUtils.capitalize(issueCustomHistory.getRootCauseList().get(0));
		}
		return null;
	}

	public FieldMappingStructureResponse fetchFieldMappingStructureByKpiId(String projectBasicConfigId, String kpiId) {
		FieldMappingStructureResponse fieldMappingStructureResponse = new FieldMappingStructureResponse();
		fieldMappingStructureResponse.setFieldConfiguration(new ArrayList<>());
		try {
			List<FieldMappingStructure> fieldMappingStructureList = (List<FieldMappingStructure>) configHelperService
					.loadFieldMappingStructure();
			if (fieldMappingStructureList == null || fieldMappingStructureList.isEmpty()) {
				return fieldMappingStructureResponse;
			}

			FieldMappingEnum fieldMappingEnum = FieldMappingEnum.valueOf(kpiId.toUpperCase());
			List<String> fieldList = fieldMappingEnum.getFields();
			String kpiSource = fieldMappingEnum.getKpiSource();

			Map<String, List<ProjectToolConfig>> projectToolMap = configHelperService.getProjectToolConfigMap()
					.get(new ObjectId(projectBasicConfigId));
			List<ProjectToolConfig> projectToolConfig = null;
			if (MapUtils.isNotEmpty(projectToolMap)) {
				projectToolConfig = getProjectToolConfigs(projectToolMap, projectToolConfig, kpiSource);
			}
			if (CollectionUtils.isEmpty(projectToolConfig)) {
				return fieldMappingStructureResponse;
			}

			ObjectId projectToolConfigId = projectToolConfig.stream()
					.filter(t -> t.getBasicProjectConfigId().toString().equals(projectBasicConfigId))
					.map(ProjectToolConfig::getId).findFirst().orElse(null);

			List<FieldMappingStructure> fieldMappingStructureList1 = getFieldMappingStructure(fieldMappingStructureList,
					fieldList);

			fieldMappingStructureResponse.setFieldConfiguration(
					CollectionUtils.isNotEmpty(fieldMappingStructureList1) ? fieldMappingStructureList1
							: new ArrayList<>());
			fieldMappingStructureResponse.setKpiSource(kpiSource);
			fieldMappingStructureResponse
					.setProjectToolConfigId(projectToolConfigId != null ? projectToolConfigId.toString() : null);
		} catch (IllegalArgumentException e) {
			fieldMappingStructureResponse.setFieldConfiguration(new ArrayList<>());
			log.info("kpi Id" + kpiId + "No Enum is present");
		}
		return fieldMappingStructureResponse;
	}

	/**
	 * Gets the project tool configurations based on priority and source.
	 *
	 * @param projectToolMap Map containing tool configurations by tool name
	 * @param projectToolConfig Initial project tool configuration list
	 * @param kpiSource The source of the KPI
	 * @return The appropriate project tool configuration list
	 */
	private static List<ProjectToolConfig> getProjectToolConfigs(Map<String, List<ProjectToolConfig>> projectToolMap,
        List<ProjectToolConfig> projectToolConfig, String kpiSource) {
    // If projectToolConfig is already populated, no need to process further
    if (!CollectionUtils.isEmpty(projectToolConfig)) {
        return projectToolConfig;
    }
    
    // Handle special case for Bitbucket
    if (kpiSource.equalsIgnoreCase(Constant.TOOL_BITBUCKET) && projectToolMap.containsKey(Constant.REPO_TOOLS)) {
        return projectToolMap.get(Constant.REPO_TOOLS);
    }
    
    // Handle special case for Azure
    if (kpiSource.equalsIgnoreCase(Constant.TOOL_AZURE) && projectToolMap.containsKey(TOOL_AZURE)) {
        return projectToolMap.get(TOOL_AZURE);
    }
    
    // Priority-based selection: Jira > Azure > Rally
    if (projectToolMap.containsKey(TOOL_JIRA)) {
        return projectToolMap.get(TOOL_JIRA);
    } else if (projectToolMap.containsKey(TOOL_AZURE)) {
        return projectToolMap.get(TOOL_AZURE);
    } else if (projectToolMap.containsKey(TOOL_RALLY)) {
        return projectToolMap.get(TOOL_RALLY);
    }
    
    // Return the original list if no matches found
    return projectToolConfig;
}

	public List<FieldMappingStructure> getFieldMappingStructure(List<FieldMappingStructure> fieldMappingStructureList,
			List<String> fieldList) {
		return fieldMappingStructureList.stream().filter(f -> fieldList.contains(f.getFieldName()))
				.collect(Collectors.toList());
	}

	public boolean hasReturnTransactionOrFTPRRejectedStatus(JiraIssue issue,
			List<JiraIssueCustomHistory> storiesHistory, List<String> statusForDevelopemnt,
			List<String> jiraStatusForQa, List<String> jiraFtprRejectStatus) {
		JiraIssueCustomHistory jiraIssueCustomHistory = storiesHistory.stream()
				.filter(issueHistory -> issueHistory.getStoryID().equals(issue.getNumber())).findFirst().orElse(null);
		if (jiraIssueCustomHistory == null) {
			return false;
		} else {
			List<JiraHistoryChangeLog> statusUpdationLog = jiraIssueCustomHistory.getStatusUpdationLog();
			if (CollectionUtils.isNotEmpty(jiraFtprRejectStatus)) {
				// if rejected field is mentioned then we will not calculate return transactions
				return CollectionUtils.isNotEmpty(statusUpdationLog.stream()
						.filter(statusHistory -> jiraFtprRejectStatus.contains(statusHistory.getChangedTo()))
						.collect(Collectors.toList()));
			} else {
				Collections.sort(statusUpdationLog, Comparator.comparing(JiraHistoryChangeLog::getUpdatedOn));
				// if after qa field we get some status which signifies statusfor development
				// then we will consider that as return transaction
				List<String> jiraStatusForQa1 = (List<String>) CollectionUtils.emptyIfNull(jiraStatusForQa);
				JiraHistoryChangeLog latestQAField = statusUpdationLog.stream()
						.filter(statusHistory -> jiraStatusForQa1.contains(statusHistory.getChangedTo())).findFirst()
						.orElse(null);
				if (latestQAField != null) {
					List<String> jiraStatusForDevelopemnt = (List<String>) CollectionUtils
							.emptyIfNull(statusForDevelopemnt);
					DateTime latestQAFieldActivityDate = DateTime.parse(latestQAField.getUpdatedOn().toString());
					return statusUpdationLog.stream()
							.filter(statusHistory -> DateTime.parse(statusHistory.getUpdatedOn().toString())
									.isAfter(latestQAFieldActivityDate))
							.anyMatch(statusHistory -> jiraStatusForDevelopemnt.contains(statusHistory.getChangedTo()));
				}
			}
			return false;
		}
	}

	/**
	 * when multiple sprints are selected from knowHow dashboard, duplicate issues
	 * present in total sprintdetails section should be used to find minimum closed
	 * dates
	 *
	 * @param projectWiseTotalSprintDetails
	 * @return
	 */
	public Map<ObjectId, Set<String>> getProjectWiseTotalSprintDetail(
			Map<ObjectId, List<SprintDetails>> projectWiseTotalSprintDetails) {
		Map<ObjectId, Set<String>> duplicateIssues = new HashMap<>();
		projectWiseTotalSprintDetails.forEach((projectId, sprintDetails) -> {
			Set<String> allIssues = sprintDetails.stream().flatMap(
					sprint -> Optional.ofNullable(sprint.getTotalIssues()).orElse(Collections.emptySet()).stream())
					.map(SprintIssue::getNumber).collect(Collectors.toSet());
			duplicateIssues.put(projectId, allIssues);
		});
		return duplicateIssues;
	}

	/**
	 * for all the duplicate issues, present in sprintdetails find out the minimum
	 * closed dates
	 *
	 * @param duplicateIssues
	 * @param customFieldMapping
	 * @return
	 */
	public Map<ObjectId, Map<String, List<LocalDateTime>>> getMinimumClosedDateFromConfiguration(
			Map<ObjectId, Set<String>> duplicateIssues, Map<ObjectId, List<String>> customFieldMapping) {
		Map<ObjectId, Map<String, List<LocalDateTime>>> projectIssueWiseClosedDates = new HashMap<>();
		Map<String, List<String>> mapOfFilters = new LinkedHashMap<>();
		mapOfFilters.put(JiraFeatureHistory.STORY_ID.getFieldValueInFeature(),
				duplicateIssues.values().stream().flatMap(Collection::stream).collect(Collectors.toList()));
		mapOfFilters.put(JiraFeatureHistory.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
				duplicateIssues.keySet().stream().map(ObjectId::toString).collect(Collectors.toList()));
		List<JiraIssueCustomHistory> jiraIssueCustomHistoryList = jiraIssueCustomHistoryRepository
				.findByFilterAndFromStatusMap(mapOfFilters, new HashMap<>());

		duplicateIssues.forEach((objectId, issues) -> {
			List<String> customFields = customFieldMapping.getOrDefault(objectId, Collections.emptyList());
			if (CollectionUtils.isNotEmpty(customFields)) {
				Map<String, List<LocalDateTime>> issueWiseMinDateTime = new HashMap<>();
				for (String issue : issues) {
					List<JiraHistoryChangeLog> statusUpdationLog = jiraIssueCustomHistoryList.stream()
							.filter(history -> history.getStoryID().equalsIgnoreCase(issue)
									&& objectId.toString().equalsIgnoreCase(history.getBasicProjectConfigId()))
							.flatMap(history -> history.getStatusUpdationLog().stream())
							.sorted(Comparator.comparing(JiraHistoryChangeLog::getUpdatedOn))
							.collect(Collectors.toList());
					/*
					 * iterate over status logs and if some not completed status appears then that
					 * has to be considered as reopen scenario, and at that time whatever statuses
					 * present in minimumCompletedStatusWiseMap, out of them the minimum date has to
					 * be considered of that closed cycle.
					 */
					if (CollectionUtils.isNotEmpty(statusUpdationLog)) {
						Map<String, LocalDateTime> minimumCompletedStatusWiseMap = new HashMap<>();
						List<LocalDateTime> minimumDate = new ArrayList<>();

						KpiDataHelper.getMiniDateOfCompleteCycle(customFields, statusUpdationLog,
								minimumCompletedStatusWiseMap, minimumDate);

						// if some status is left in the last cycle then that has to added in the
						// minimum set
						if (MapUtils.isNotEmpty(minimumCompletedStatusWiseMap)) {
							LocalDateTime minDate = minimumCompletedStatusWiseMap.values().stream()
									.min(LocalDateTime::compareTo).orElse(null);
							if (minDate != null) {
								minimumDate.add(minDate);
								minimumCompletedStatusWiseMap.clear();
							}
						}
						issueWiseMinDateTime.put(issue, minimumDate);
					}
				}
				projectIssueWiseClosedDates.put(objectId, issueWiseMinDateTime);
			}
		});
		return projectIssueWiseClosedDates;
	}

	/**
	 * convert hours into work hours by 8 factor
	 *
	 * @param timeInHours
	 *            time in hours
	 * @return time in work hours
	 */
	public long getTimeInWorkHours(long timeInHours) {
		long timeInHrs = (timeInHours / 24) * 8;
		long remainingTimeInMin = (timeInHours % 24);
		if (remainingTimeInMin >= 8) {
			timeInHrs = timeInHrs + 8;
		} else {
			timeInHrs = timeInHrs + remainingTimeInMin;
		}
		return timeInHrs;
	}

	/**
	 * convert total hours to days
	 *
	 * @param hours
	 *            hours
	 * @return time in days
	 */
	public String convertHoursToDaysString(long hours) {
		hours = getTimeInWorkHours(hours);
		long days = hours / 8;
		long remainingHours = hours % 8;
		if (days == 0 && remainingHours == 0) {
			return "0";
		} else if (remainingHours == 0) {
			return String.format("%dd", days);
		} else {
			return String.format("%dd %dhrs", days, remainingHours);
		}
	}

	/**
	 * get weekend between two dates
	 *
	 * @param d1
	 *            start date
	 * @param d2
	 *            end date
	 * @return weekends between start date and end date
	 */
	public int minusHoursOfWeekEndDays(LocalDateTime d1, LocalDateTime d2) {
		int countOfWeekEndDays = saturdaySundayCount(d1, d2);
		if (countOfWeekEndDays != 0) {
			return countOfWeekEndDays * 24;
		} else {
			return 0;
		}
	}

	/**
	 * check number of saturday, sunday between dates
	 *
	 * @param d1
	 *            start date
	 * @param d2
	 *            end date
	 * @return number of sat, sun
	 */
	public int saturdaySundayCount(LocalDateTime d1, LocalDateTime d2) {
		int countWeekEnd = 0;
		while (!d1.isAfter(d2)) {
			if (isWeekEnd(d1)) {
				countWeekEnd++;
			}
			d1 = d1.plusDays(1);
		}
		return countWeekEnd;
	}

	/**
	 * check if day is weekend
	 *
	 * @param localDateTime
	 *            localdatetime of day
	 * @return boolean
	 */
	public boolean isWeekEnd(LocalDateTime localDateTime) {
		int dayOfWeek = localDateTime.getDayOfWeek().getValue();
		return dayOfWeek == 6 || dayOfWeek == 7;
	}

	/**
	 * convert milliseconds to hours
	 *
	 * @param milliseconds
	 *            milliseconds
	 * @return time in hours
	 */
	public static long convertMilliSecondsToHours(double milliseconds) {
		double hoursExact = milliseconds / (3600000);
		return Math.round(hoursExact);
	}

	/**
	 * gets next date excluding weekends
	 *
	 * @param duration
	 *            time duration
	 * @param currentDate
	 *            current date
	 * @return next local date excluding weekends
	 */
	public static LocalDate getNextRangeDate(String duration, LocalDate currentDate) {
		if ((CommonConstant.WEEK).equalsIgnoreCase(duration)) {
			currentDate = currentDate.minusWeeks(1);
		} else {
			currentDate = currentDate.minusDays(1);
			while (currentDate.getDayOfWeek() == DayOfWeek.SATURDAY || currentDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
				currentDate = currentDate.minusDays(1);
			}
		}
		return currentDate;
	}

	/**
	 * get date range
	 *
	 * @param dateRange
	 *            date range
	 * @param duration
	 *            time duration
	 * @return date range string
	 */
	public static String getDateRange(CustomDateRange dateRange, String duration) {
		String range = null;
		if (CommonConstant.WEEK.equalsIgnoreCase(duration)) {
			range = DateUtil.dateTimeConverter(dateRange.getStartDate().toString(), DateUtil.DATE_FORMAT,
					DateUtil.DISPLAY_DATE_FORMAT) + " to "
					+ DateUtil.dateTimeConverter(dateRange.getEndDate().toString(), DateUtil.DATE_FORMAT,
							DateUtil.DISPLAY_DATE_FORMAT);
		} else {
			range = dateRange.getStartDate().toString();
		}
		return range;
	}

	/**
	 * get date range excluding weekends
	 *
	 * @param date
	 *            start date
	 * @param period
	 *            week or day
	 * @return CustomDateRange
	 */
	public static CustomDateRange getStartAndEndDateExcludingWeekends(LocalDate date, String period) {
		CustomDateRange dateRange = new CustomDateRange();
		LocalDate startDate = null;
		LocalDate endDate = null;
		if (period.equalsIgnoreCase(CommonConstant.WEEK)) {
			LocalDate monday = date;
			while (monday.getDayOfWeek() != DayOfWeek.MONDAY) {
				monday = monday.minusDays(1);
			}
			startDate = monday;
			LocalDate friday = date;
			while (friday.getDayOfWeek() != DayOfWeek.FRIDAY) {
				friday = friday.plusDays(1);
			}
			endDate = friday;
		} else {
			startDate = date;
			endDate = date;
		}
		dateRange.setStartDate(startDate);
		dateRange.setEndDate(endDate);
		return dateRange;
	}

	/**
	 * @param kpiRequest
	 *            kpiRequest
	 * @param filteredAccountDataList
	 *            filteredAccountDataList
	 * @return list of AccountHierarchyData
	 */
	public List<AccountHierarchyData> getAuthorizedFilteredList(KpiRequest kpiRequest,
			List<AccountHierarchyData> filteredAccountDataList, boolean referFromProjectCache) {
		kpiResolution(kpiRequest.getKpiList());
		if (Boolean.TRUE.equals(referFromProjectCache) && !authorizedProjectsService.ifSuperAdminUser()) {
			filteredAccountDataList = authorizedProjectsService.filterProjects(filteredAccountDataList);
		}

		return filteredAccountDataList;
	}

	/**
	 * @param kpiRequest
	 *            kpiRequest
	 * @param filteredAccountDataList
	 *            filteredAccountDataList
	 */
	public String[] getProjectKeyCache(KpiRequest kpiRequest, List<AccountHierarchyData> filteredAccountDataList,
			boolean referFromProjectCache) {
		String[] projectKeyCache;
		if (Boolean.TRUE.equals(referFromProjectCache) && !authorizedProjectsService.ifSuperAdminUser()) {
			projectKeyCache = authorizedProjectsService.getProjectKey(filteredAccountDataList, kpiRequest);
		} else {
			projectKeyCache = kpiRequest.getIds();
		}

		return projectKeyCache;
	}

	/**
	 * @param kpiRequest
	 *            kpiRequest
	 * @param responseList
	 *            responseList
	 * @param groupId
	 *            groupId
	 */
	public void setIntoApplicationCache(KpiRequest kpiRequest, List<KpiElement> responseList, Integer groupId,
			String[] projects) {
		Integer sprintLevel = flterHelperService.getHierarchyIdLevelMap(false)
				.get(CommonConstant.HIERARCHY_LEVEL_ID_SPRINT);

		if (!kpiRequest.getRequestTrackerId().toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())
				&& sprintLevel >= kpiRequest.getLevel() && isLeadTimeDuration(kpiRequest.getKpiList())) {
			cacheService.setIntoApplicationCache(projects, responseList, KPISource.JIRA.name(), groupId,
					kpiRequest.getSprintIncluded());
		}
	}

	private boolean isLeadTimeDuration(List<KpiElement> kpiList) {
		return kpiList.size() != 1 || !kpiList.get(0).getKpiId().equalsIgnoreCase("kpi171");
	}

	/**
	 * Create PriorityWise Count map from FieldMapping & configPriority
	 *
	 * @param projectWisePriorityCount
	 *            projectWisePriorityCount
	 * @param configPriority
	 *            configPriority
	 * @param leaf
	 *            Node
	 * @param defectPriorityCount
	 *            From FieldMapping
	 */
	public static void addPriorityCountProjectWise(Map<String, Map<String, Integer>> projectWisePriorityCount,
			Map<String, List<String>> configPriority, Node leaf, List<LabelCount> defectPriorityCount) {
		if (CollectionUtils.isNotEmpty(defectPriorityCount)) {
			defectPriorityCount
					.forEach(labelCount -> labelCount.setLabelValue(labelCount.getLabelValue().toUpperCase()));
			if (CollectionUtils.isNotEmpty(defectPriorityCount)) {
				Map<String, Integer> priorityValues = new HashMap<>();
				defectPriorityCount.forEach(label -> configPriority.get(label.getLabelValue()).forEach(
						priorityValue -> priorityValues.put(priorityValue.toLowerCase(), label.getCountValue())));
				projectWisePriorityCount.put(leaf.getProjectFilter().getBasicProjectConfigId().toString(),
						priorityValues);
			}
		}
	}

	/**
	 * Create PriorityWise Count map from FieldMapping & configPriority
	 *
	 * @param projectWisePriorityCount
	 *            projectWisePriorityCount
	 * @param configPriority
	 *            configPriority
	 * @param basicProjectConfigId
	 *            Node
	 * @param defectPriorityCount
	 *            From FieldMapping
	 */
	public static void addPriorityCountProjectWiseForQuality(Map<String, Map<String, Integer>> projectWisePriorityCount,
			Map<String, List<String>> configPriority, ObjectId basicProjectConfigId,
			List<LabelCount> defectPriorityCount) {
		if (CollectionUtils.isNotEmpty(defectPriorityCount)) {
			defectPriorityCount
					.forEach(labelCount -> labelCount.setLabelValue(labelCount.getLabelValue().toUpperCase()));
			if (CollectionUtils.isNotEmpty(defectPriorityCount)) {
				Map<String, Integer> priorityValues = new HashMap<>();
				defectPriorityCount.forEach(label -> configPriority.get(label.getLabelValue()).forEach(
						priorityValue -> priorityValues.put(priorityValue.toLowerCase(), label.getCountValue())));
				projectWisePriorityCount.put(basicProjectConfigId.toString(), priorityValues);
			}
		}
	}

	/**
	 * get kpi data from repo tools api, for project level hierarchy only
	 *
	 * @param endDate
	 *            end date
	 * @param tools
	 *            tool map from cache
	 * @param node
	 *            project node
	 * @param dataPoint
	 *            no of days/weeks
	 * @param duration
	 *            time duration
	 * @return lis of RepoToolKpiMetricResponse object
	 */
	public List<RepoToolKpiMetricResponse> getRepoToolsKpiMetricResponse(LocalDate endDate, List<Tool> tools, Node node,
			String duration, Integer dataPoint, String repoToolKpi) {

		List<String> projectCodeList = new ArrayList<>();
		if (!CollectionUtils.isEmpty(tools)) {
			projectCodeList.add(node.getProjectFilter().getBasicProjectConfigId().toString());
		}

		List<RepoToolKpiMetricResponse> repoToolKpiMetricResponseList = new ArrayList<>();
		if (CollectionUtils.isNotEmpty(projectCodeList)) {
			LocalDate startDate = LocalDate.now().minusDays(dataPoint);
			startDate = getStartDate(duration, dataPoint, startDate);
			String debbieDuration = duration.equalsIgnoreCase(CommonConstant.WEEK) ? WEEK_FREQUENCY : DAY_FREQUENCY;
			repoToolKpiMetricResponseList = repoToolsConfigService.getRepoToolKpiMetrics(projectCodeList, repoToolKpi,
					startDate.toString(), endDate.toString(), debbieDuration);
		}

		return repoToolKpiMetricResponseList;
	}

	private static LocalDate getStartDate(String duration, Integer dataPoint, LocalDate startDate) {
		if (duration.equalsIgnoreCase(CommonConstant.WEEK)) {
			startDate = LocalDate.now().minusWeeks(dataPoint);
			while (startDate.getDayOfWeek() != DayOfWeek.MONDAY) {
				startDate = startDate.minusDays(1);
			}
		} else {
			int daysSubtracted = 0;
			while (daysSubtracted < dataPoint) {
				// Skip the weekend days
				if (!(startDate.getDayOfWeek() == DayOfWeek.SATURDAY || startDate.getDayOfWeek() == DayOfWeek.SUNDAY)) {
					daysSubtracted++;
				}
				startDate = startDate.minusDays(1);
			}
		}
		return startDate;
	}

	/**
	 * Exclude Defects based on the Priority Count tagged to Story
	 *
	 * @param projectWisePriority
	 *            projectWisePriorityCount Map
	 * @param defects
	 *            List<JiraIssue> Defect List
	 * @return List of Defects which are remaining after exclusion of priority count
	 */
	public static List<JiraIssue> excludeDefectByPriorityCount(Map<String, Map<String, Integer>> projectWisePriority,
			Set<JiraIssue> defects) {
		// creating storyWise linked defects priority count map
		Map<String, Map<String, Integer>> storiesBugPriorityCount = new HashMap<>();
		defects.forEach(defect -> {
			Set<String> linkedStories = defect.getDefectStoryID();
			linkedStories
					.forEach(linkedStory -> storiesBugPriorityCount.computeIfAbsent(linkedStory, k -> new HashMap<>())
							.merge(Optional.ofNullable(defect.getPriority()).orElse("").toLowerCase(), 1, Integer::sum));
		});

		List<JiraIssue> remainingDefects = new ArrayList<>();
		for (JiraIssue defect : defects) {
			if (MapUtils
					.isNotEmpty(projectWisePriority.get(defect.getBasicProjectConfigId()))) {
				Map<String, Integer> projPriorityCountMap = projectWisePriority.get(defect.getBasicProjectConfigId());
				Set<String> linkedStories = defect.getDefectStoryID();
				linkedStories.forEach(linked -> { // iterating through all linked stories
					Map<String, Integer> storyLinkedBugPriority = storiesBugPriorityCount.getOrDefault(linked,
							new HashMap<>());
					storyLinkedBugPriority.forEach((priority, defectCount) -> {
						// if defectCount of the story w.r.t priority is greater than of fieldMapping or
						// no exclusion for priority is defined in field mapping
						// include it as defect
						if (!projPriorityCountMap.containsKey(priority)
								|| projPriorityCountMap.get(priority) < defectCount) {
							remainingDefects.add(defect);
						}
					});
				});
			} else {
				remainingDefects.add(defect);
			}
		}
		return remainingDefects;
	}

	/**
	 * Retrieves a list of SCM (Source Control Management) tool jobs for a given
	 * project node.
	 *
	 * @param toolListMap
	 *            a map where the key is a string representing the SCM tool type
	 *            (e.g., "Bitbucket", "AzureRepository", "GitLab", "GitHub") and the
	 *            value is a list of Tool objects associated with that SCM tool
	 *            type.
	 * @param node
	 *            the project node for which the SCM tool jobs are to be retrieved.
	 * @return a list of Tool objects representing the SCM tool jobs for the given
	 *         project node.
	 */
	public List<Tool> getScmToolJobs(Map<String, List<Tool>> toolListMap, Node node) {

		List<Tool> bitbucketJob = new ArrayList<>();
		if (null != toolListMap) {
			bitbucketJob
					.addAll(toolListMap.get(BITBUCKET) == null ? Collections.emptyList() : toolListMap.get(BITBUCKET));
			bitbucketJob.addAll(
					toolListMap.get(AZURE_REPO) == null ? Collections.emptyList() : toolListMap.get(AZURE_REPO));
			bitbucketJob.addAll(toolListMap.get(GITLAB) == null ? Collections.emptyList() : toolListMap.get(GITLAB));
			bitbucketJob.addAll(toolListMap.get(GITHUB) == null ? Collections.emptyList() : toolListMap.get(GITHUB));
		}
		if (CollectionUtils.isEmpty(bitbucketJob)) {
			log.error("[BITBUCKET]. No repository found for this project {}", node.getProjectFilter());
		}
		return bitbucketJob;
	}

	/**
	 * Populates a list of SCM (Source Control Management) tools repositories.
	 *
	 * @param mapOfListOfTools
	 *            a map where the key is a string representing the SCM tool type
	 *            (e.g., "Bitbucket", "AzureRepository", "GitLab", "GitHub") and the
	 *            value is a list of Tool objects associated with that SCM tool
	 *            type.
	 * @return a list of Tool objects representing the SCM tool repositories.
	 */
	public List<Tool> populateSCMToolsRepoList(Map<String, List<Tool>> mapOfListOfTools) {
		List<Tool> reposList = new ArrayList<>();
		if (null != mapOfListOfTools) {
			reposList.addAll(mapOfListOfTools.get(BITBUCKET) == null ? Collections.emptyList()
					: mapOfListOfTools.get(BITBUCKET));
			reposList.addAll(mapOfListOfTools.get(AZURE_REPO) == null ? Collections.emptyList()
					: mapOfListOfTools.get(AZURE_REPO));
			reposList.addAll(
					mapOfListOfTools.get(GITLAB) == null ? Collections.emptyList() : mapOfListOfTools.get(GITLAB));
			reposList.addAll(
					mapOfListOfTools.get(GITHUB) == null ? Collections.emptyList() : mapOfListOfTools.get(GITHUB));
		}
		return reposList;
	}

	public boolean isToolConfigured(KPICode kpi, KpiElement kpiElement, Node nodeDataClone) {
		ObjectId basicProjectConfigId = nodeDataClone.getProjectFilter().getBasicProjectConfigId();
		if (!isToolConfigured(kpi, basicProjectConfigId)) {
			kpiElement.setResponseCode(CommonConstant.TOOL_NOT_CONFIGURED);
			return false;
		}
		return true;
	}

	public boolean isMandatoryFieldSet(KPICode kpi, KpiElement kpiElement, Node nodeDataClone) {
		ObjectId basicProjectConfigId = nodeDataClone.getProjectFilter().getBasicProjectConfigId();
		if (!isMandatoryFieldSet(kpi, basicProjectConfigId)) {
			kpiElement.setResponseCode(CommonConstant.MANDATORY_FIELD_MAPPING);
			return false;
		}
		return true;
	}

	private boolean isMandatoryFieldSet(KPICode kpi, ObjectId basicProjectConfigId) {
		try {
			List<String> fieldMappingName = FieldMappingEnum.valueOf(kpi.getKpiId().toUpperCase()).getFields();
			List<FieldMappingStructure> fieldMappingStructureList = (List<FieldMappingStructure>) configHelperService
					.loadFieldMappingStructure();
			List<String> mandatoryFieldMappingName = fieldMappingStructureList.stream()
					.filter(fieldMappingStructure -> fieldMappingStructure.isMandatory()
							&& fieldMappingName.contains(fieldMappingStructure.getFieldName()))
					.map(FieldMappingStructure::getFieldName).toList();

			FieldMapping fieldMapping = configHelperService.getFieldMappingMap().get(basicProjectConfigId);

			for (String fieldName : mandatoryFieldMappingName) {
				if (checkNullValues(fieldName, fieldMapping))
					return false;
			}
		} catch (IllegalArgumentException exception) {
			log.warn(kpi.getKpiId() + " No fieldMapping Found");
			return true;
		}
		return true;
	}

	private static boolean checkNullValues(String fieldName, FieldMapping fieldMapping) {
		try {
			Field field = FieldMapping.class.getDeclaredField(fieldName);
			field.setAccessible(true); // NOSONAR
			if (CommonUtils.checkObjectNullValue(field.get(fieldMapping)))
				return true;
		} catch (NoSuchFieldException e) {
			log.warn(fieldName + " does not exist in fieldMapping.");
		} catch (IllegalAccessException e) {
			log.warn("Error accessing " + fieldName + " field.");
		}
		return false;
	}

	private boolean isToolConfigured(KPICode kpi, ObjectId basicProjectConfigId) {
		Set<String> configuredTools = configHelperService.getProjectToolConfigMap()
				.getOrDefault(basicProjectConfigId, Collections.emptyMap()).keySet().stream().map(String::toUpperCase)
				.collect(Collectors.toSet());

		List<KpiMaster> masterList = (List<KpiMaster>) configHelperService.loadKpiMaster();
		Map<String, String> toolWiseKpiSource = masterList.stream().filter(
				d -> StringUtils.isNotEmpty(d.getCombinedKpiSource()) || StringUtils.isNotEmpty(d.getKpiSource()))
				.collect(Collectors.toMap(k -> k.getKpiId().toUpperCase(),
						k -> (StringUtils.isNotEmpty(k.getCombinedKpiSource()) ? k.getCombinedKpiSource().toUpperCase()
								: k.getKpiSource().toUpperCase())));

		return Arrays.stream(toolWiseKpiSource.get(kpi.getKpiId().toUpperCase()).split("/"))
				.anyMatch(configuredTools::contains);
	}

	/**
	 * checking the tool configuration for Zephyr KPIs, as testing kpis works on 2
	 * processor run and JIRA/AZURE is mandatory and in sprint and regression kpis
	 * have upload data option to have data on the kpis, while test execution kpi do
	 * not rely on the execution of any testing tool processor run
	 *
	 * @param kpi
	 * @param kpiElement
	 * @param basicProjectConfigId
	 * @return
	 */
	public boolean isRequiredTestToolConfigured(KPICode kpi, KpiElement kpiElement, ObjectId basicProjectConfigId) {
		Set<String> configuredTools = configHelperService.getProjectToolConfigMap()
				.getOrDefault(basicProjectConfigId, Collections.emptyMap()).keySet().stream().map(String::toUpperCase)
				.collect(Collectors.toSet());

		Set<KPICode> kpisTestToolRequired = Set.of(KPICode.INSPRINT_AUTOMATION_COVERAGE,
				KPICode.REGRESSION_AUTOMATION_COVERAGE, KPICode.KANBAN_REGRESSION_PASS_PERCENTAGE);

		// setting tool not configured as by default message and overriding it later
		kpiElement.setResponseCode(CommonConstant.TOOL_NOT_CONFIGURED);
		if (configuredTools.contains("JIRA") || configuredTools.contains("AZURE")) {
			if (kpisTestToolRequired.contains(kpi)) {
				return checkUpload(kpi, basicProjectConfigId) || testToolCheck(configuredTools);
			} else {
				return true;
			}
		}
		return false;
	}

	private boolean testToolCheck(Set<String> configureTools) {
		return Stream.of("ZEPHYR", "ZYPHER", "JIRATEST").anyMatch(configureTools::contains);
	}

	private boolean checkUpload(KPICode kpiCode, ObjectId projectBasicConfigId) {
		FieldMapping fieldMapping = configHelperService.getFieldMappingMap().get(projectBasicConfigId);
		if (kpiCode.equals(KPICode.INSPRINT_AUTOMATION_COVERAGE)) {
			return fieldMapping.isUploadDataKPI16();
		} else if (kpiCode.equals(KPICode.KANBAN_REGRESSION_PASS_PERCENTAGE)) {
			return false;
		} else {
			return fieldMapping.isUploadDataKPI42();
		}
	}

	public String updateKPISource(ObjectId basicProjectConfId, ObjectId projectToolConfigId) {
		Map<ObjectId, Map<String, List<ProjectToolConfig>>> toolMap = (Map<ObjectId, Map<String, List<ProjectToolConfig>>>) cacheService
				.cacheProjectToolConfigMapData();

		String source = "";
		if (MapUtils.isNotEmpty(toolMap) && toolMap.get(basicProjectConfId) != null) {
			List<ProjectToolConfig> allToolConfigs = new ArrayList<>();
			toolMap.get(basicProjectConfId).values().forEach(allToolConfigs::addAll);
			ProjectToolConfig projectToolConfig = allToolConfigs.stream()
					.filter(a -> a.getId().equals(projectToolConfigId)).toList().get(0);
			return projectToolConfig.getToolName();
		}
		return source;
	}

	public static List<JiraIssue> getSprintReOpenedDefects(SprintDetails sprintDetails,
			List<JiraIssueCustomHistory> defectHistoryList, List<String> closedStatusList,
			List<JiraIssue> totalDefectList, String defectReopenStatusKPI190,
			Map<String, List<DefectTransitionInfo>> sprintDefectStatusInfo) {

		List<JiraIssue> reopenedDefects = new ArrayList<>();
		LocalDateTime sprintStartDate = getParseDateFromSprint(sprintDetails.getActivatedDate(),
				sprintDetails.getStartDate());
		LocalDateTime sprintEndDate = getParseDateFromSprint(sprintDetails.getCompleteDate(),
				sprintDetails.getEndDate());

		Map<String, JiraIssue> totalDefectMap = totalDefectList.stream()
				.collect(Collectors.toMap(JiraIssue::getNumber, Function.identity()));

		for (JiraIssueCustomHistory defectHistory : defectHistoryList) {
			List<JiraHistoryChangeLog> statusUpdationLog = defectHistory.getStatusUpdationLog();
			if (CollectionUtils.isEmpty(statusUpdationLog))
				continue;
			statusUpdationLog.sort(Comparator.comparing(JiraHistoryChangeLog::getUpdatedOn));

			Map<String, DateTime> closedStatusDateMap = new HashMap<>();
			for (JiraHistoryChangeLog statusLog : statusUpdationLog) {
				String changedTo = statusLog.getChangedTo().toLowerCase();
				if (statusLog.getUpdatedOn().isAfter(sprintEndDate)) {
					break;
				}
				if (closedStatusList.contains(changedTo)) {
					processClosedStatusDate(statusLog, closedStatusDateMap, changedTo);
				}
				// Reopen event
				else if (statusLog.getChangedTo().equalsIgnoreCase(defectReopenStatusKPI190)
						&& DateUtil.isWithinDateTimeRange(statusLog.getUpdatedOn(), sprintStartDate, sprintEndDate)) {
					processReopenDetail(sprintDefectStatusInfo, defectHistory, statusLog, closedStatusDateMap,
							totalDefectMap, reopenedDefects);
				}
			}
		}
		return reopenedDefects;
	}

	private static void processClosedStatusDate(JiraHistoryChangeLog statusLog,
			Map<String, DateTime> closedStatusDateMap, String changedTo) {
		if (closedStatusDateMap.containsKey(changedTo)) {
			closedStatusDateMap.clear();
		}
		closedStatusDateMap.put(changedTo, DateUtil.convertLocalDateTimeToDateTime(statusLog.getUpdatedOn()));
	}

	private static void processReopenDetail(Map<String, List<DefectTransitionInfo>> sprintDefectStatusInfo,
			JiraIssueCustomHistory defectHistory, JiraHistoryChangeLog statusLog,
			Map<String, DateTime> closedStatusDateMap, Map<String, JiraIssue> totalDefectMap,
			List<JiraIssue> reopenedDefects) {
		DefectTransitionInfo transitionInfo = new DefectTransitionInfo();
		DateTime reopenTime = DateUtil.convertLocalDateTimeToDateTime(statusLog.getUpdatedOn());
		DateTime lastClosedLogTime = closedStatusDateMap.values().stream().filter(Objects::nonNull)
				.min(DateTime::compareTo).orElse(null);
		if (lastClosedLogTime != null) {
			transitionInfo.setClosedDate(lastClosedLogTime);
			transitionInfo.setReopenDate(reopenTime);
			transitionInfo
					.setReopenDuration(Double.parseDouble(KpiDataHelper.calWeekHours(lastClosedLogTime, reopenTime)));
			final JiraIssue jiraIssue = totalDefectMap.get(defectHistory.getStoryID());
			reopenedDefects.add(jiraIssue);
			transitionInfo.setPriority(jiraIssue.getPriority());
			transitionInfo.setDefectJiraIssue(jiraIssue);
			List<DefectTransitionInfo> transitionList = sprintDefectStatusInfo.getOrDefault(defectHistory.getStoryID(),
					new ArrayList<>());
			transitionList.add(transitionInfo);
			sprintDefectStatusInfo.put(defectHistory.getStoryID(), transitionList);
		}
	}

	private static LocalDateTime getParseDateFromSprint(String sprintDetails, String sprintDetails1) {
		return sprintDetails != null ? LocalDateTime.parse(sprintDetails.split("\\.")[0], DATE_TIME_FORMATTER)
				: LocalDateTime.parse(sprintDetails1.split("\\.")[0], DATE_TIME_FORMATTER);
	}

}
