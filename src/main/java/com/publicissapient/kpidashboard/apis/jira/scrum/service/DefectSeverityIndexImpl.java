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

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.Filters;
import com.publicissapient.kpidashboard.apis.enums.JiraFeature;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.jira.service.JiraKPIService;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.constant.NormalizedJira;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.SprintWiseStory;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of Defect Count By Severity KPI service.
 * 
 * This class calculates and provides defect count metrics categorized by severity/severity levels.
 * It fetches defect data from Jira and processes it to generate KPI metrics for dashboard visualization.
 * The trend analysis shows defect counts on the y-axis against sprint IDs on the x-axis,
 * with separate trend lines for each severity level (P1, P2, P3, P4, and miscellaneous).
 * 
 * The class handles:
 * - Fetching defect data from the database based on sprint information
 * - Calculating defect counts by severity/severity for each sprint
 * - Generating trend analysis data for visualization
 * - Preparing Excel export data when requested
 * 
 * @author girpatha
 * @see JiraKPIService
 */
@Component
@Slf4j
public class DefectSeverityIndexImpl extends JiraKPIService<Long, List<Object>, Map<String, Object>> {

		/** Constant used for suppressing unchecked cast warnings */
	public static final String UNCHECKED = "unchecked";

    /** Constant used for logging separators */
	private static final String SEPARATOR_ASTERISK = "*************************************";

    /** Key for storing total defect data in maps */
    private static final String TOTAL_DEFECT_DATA = "totalBugKey";

    /** Key for storing story list in maps */
    public static final String STORY_LIST = "storyList";

    /** Key for storing sprint-wise story data */
    private static final String SPRINT_WISE_STORY_DATA = "storyData";

    /** Constant for developer KPI type */
    public static final String DEVELOPER_KPI = "DeveloperKpi";

    /** Repository for accessing Jira issue data */
    @Autowired
    private JiraIssueRepository jiraIssueRepository;

    /** Service for caching data */
    @Autowired
    private CacheService cacheService;

    /** Service for filter-related operations */
    @Autowired
    private FilterHelperService filterHelperService;

    /** Configuration for custom API settings */
    @Autowired
    private CustomApiConfig customApiConfig;

    /** Service for configuration helper functions */
    @Autowired
    private ConfigHelperService configHelperService;

    /**
     * Returns the qualifier type for this KPI service.
     * 
     * @return Qualifier type for Defect Count by Severity KPI
     */
    @Override
    public String getQualifierType() {
        return KPICode.DEFECT_SEVERITY_INDEX.name();
    }

    /**
     * Calculates and returns the KPI data for the provided request.
     * 
     * This method processes the request, fetches the required data, and calculates
     * the KPI metrics. It returns the calculated KPI data in a KpiElement object.
     * 
     * @param kpiRequest         Request containing filters and other parameters
     * @param kpiElement         KPI element to be populated
     * @param treeAggregatorDetail Tree aggregator detail object
     * @return Calculated KPI data in a KpiElement object
     * @throws ApplicationException If an error occurs during KPI calculation
     */
    @Override
    public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
            throws ApplicationException {

        List<DataCount> trendValueList = new ArrayList<>();
        Node root = treeAggregatorDetail.getRoot();
        Map<String, Node> nodeIdMap = treeAggregatorDetail.getMapTmp();

        treeAggregatorDetail.getMapOfListOfLeafNodes().forEach((k, v) -> {
            if (Filters.getFilter(k) == Filters.SPRINT) {
                sprintWiseLeafNodeValue(nodeIdMap, v, trendValueList, kpiElement, kpiRequest);
            }
        });

        log.debug("[DC-LEAF-NODE-VALUE][{}]. Values of leaf node after KPI calculation {}",
                kpiRequest.getRequestTrackerId(), root);

        Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
        calculateAggregatedValueMap(root, nodeWiseKPIValue, KPICode.DEFECT_SEVERITY_INDEX);
        Map<String, List<DataCount>> trendValuesMap = getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue,
                KPICode.DEFECT_SEVERITY_INDEX);

        trendValuesMap = KPIHelperUtil.sortTrendMapByKeyOrder(trendValuesMap, severityTypes(true));
        Map<String, Map<String, List<DataCount>>> issueTypeProjectWiseDc = new LinkedHashMap<>();
        trendValuesMap.forEach((issueType, dataCounts) -> {
            Map<String, List<DataCount>> projectWiseDc = dataCounts.stream()
                    .collect(Collectors.groupingBy(DataCount::getData));
            issueTypeProjectWiseDc.put(issueType, projectWiseDc);
        });

        List<DataCountGroup> dataCountGroups = new ArrayList<>();
        issueTypeProjectWiseDc.forEach((issueType, projectWiseDc) -> {
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
     * Fetches KPI data from the database based on the provided parameters.
     * 
     * This method retrieves defect data from the database for the specified nodes,
     * date range, and filters. It processes the raw data to extract defect counts
     * by severity for each sprint.
     * 
     * @param leafNodeList List of leaf nodes for which data needs to be fetched
     * @param startDate    Start date for data retrieval
     * @param endDate      End date for data retrieval
     * @param kpiRequest   Request containing filters and other parameters
     * @return Map containing the fetched and processed KPI data
     */
    @Override
    public Map<String, Object> fetchKPIDataFromDb(final List<Node> leafNodeList, final String startDate,
            final String endDate, final KpiRequest kpiRequest) {

        List<String> sprintList = new ArrayList<>();
        List<String> basicProjectConfigIds = new ArrayList<>();

        final Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();
        final Map<String, Map<String, List<String>>> droppedDefects = new HashMap<>();
        final List<String> defectType = new ArrayList<>();

        leafNodeList.forEach(leaf -> {
            ObjectId basicProjectConfigId = leaf.getProjectFilter().getBasicProjectConfigId();
            sprintList.add(leaf.getSprintFilter().getId());
            basicProjectConfigIds.add(basicProjectConfigId.toString());

            final Map<String, Object> mapOfProjectFilters = new LinkedHashMap<>();

            final FieldMapping fieldMapping = configHelperService.getFieldMappingMap().get(basicProjectConfigId);

            mapOfProjectFilters.put(JiraFeature.ISSUE_TYPE.getFieldValueInFeature(),
                    CommonUtils.convertToPatternList(fieldMapping.getJiraDefectCountlIssueTypeKPI28()));
            KpiHelperService.getDroppedDefectsFilters(droppedDefects, basicProjectConfigId,
                    fieldMapping.getResolutionTypeForRejectionKPI28(), fieldMapping.getJiraDefectRejectionStatusKPI28());
            uniqueProjectMap.put(basicProjectConfigId.toString(), mapOfProjectFilters);
        });

        final Map<String, List<String>> mapOfFilters = new LinkedHashMap<>();
        final Map<String, Object> resultListMap = new HashMap<>();

        /** additional filter * */
        KpiDataHelper.createAdditionalFilterMap(kpiRequest, mapOfFilters, Constant.SCRUM, DEVELOPER_KPI, filterHelperService);

        mapOfFilters.put(JiraFeature.SPRINT_ID.getFieldValueInFeature(),
                sprintList.stream().distinct().collect(Collectors.toList()));
        mapOfFilters.put(JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
                basicProjectConfigIds.stream().distinct().collect(Collectors.toList()));

        // Fetch Story ID List grouped by Sprint
        List<SprintWiseStory> sprintWiseStoryList = jiraIssueRepository.findIssuesGroupBySprint(mapOfFilters,
                uniqueProjectMap, kpiRequest.getFilterToShowOnTrend(), DEVELOPER_KPI);

        final List<String> storyIdList = new ArrayList<>();
        sprintWiseStoryList.forEach(s -> storyIdList.addAll(s.getStoryList()));

        defectType.add(NormalizedJira.DEFECT_TYPE.getValue());
        mapOfFilters.put(JiraFeature.ISSUE_TYPE.getFieldValueInFeature(), defectType);

        // remove keys when search defects based on stories
        mapOfFilters.remove(JiraFeature.SPRINT_ID.getFieldValueInFeature());

        // Fetch Defects linked with story ID's
        mapOfFilters.put(JiraFeature.DEFECT_STORY_ID.getFieldValueInFeature(), storyIdList);

        List<JiraIssue> defectListWoDrop = getDefectListWoDrop(jiraIssueRepository.findIssuesByType(mapOfFilters),
                droppedDefects);
        setDbQueryLogger(storyIdList, defectListWoDrop);
        resultListMap.put(SPRINT_WISE_STORY_DATA, sprintWiseStoryList);
        resultListMap.put(TOTAL_DEFECT_DATA, defectListWoDrop);
        resultListMap.put(STORY_LIST, jiraIssueRepository.findIssueAndDescByNumber(storyIdList));
        return resultListMap;
    }
    /**
     * Filters out dropped defects from the list of defects linked with stories.
     * 
     * This method removes defects that have been marked as dropped from the
     * provided list of defects linked with stories.
     * 
     * @param defectLinkedWithStory List of defects linked with stories
     * @param droppedDefects        Map containing information about dropped defects
     * @return List of defects after removing dropped defects
     */
    private List<JiraIssue> getDefectListWoDrop(List<JiraIssue> defectLinkedWithStory,
            Map<String, Map<String, List<String>>> droppedDefects) {
        List<JiraIssue> defectListWoDrop = new ArrayList<>();
        KpiHelperService.getDefectsWithoutDrop(droppedDefects, defectLinkedWithStory, defectListWoDrop);
        return defectListWoDrop;
    }

    /**
     * Populates KPI values to sprint leaf nodes and generates trend analysis data.
     * 
     * This method processes the defect data for each sprint and calculates the
     * defect counts by severity. It then populates the leaf nodes with this data
     * and generates trend analysis data for visualization.
     * 
     * @param mapTmp             Map of node IDs to Node objects
     * @param sprintLeafNodeList List of sprint leaf nodes
     * @param trendValueList     List to store trend values
     * @param kpiElement         KPI element to be populated
     * @param kpiRequest         KPI request containing parameters
     */
    @SuppressWarnings(UNCHECKED)
    private void sprintWiseLeafNodeValue(Map<String, Node> mapTmp, List<Node> sprintLeafNodeList,
            List<DataCount> trendValueList, KpiElement kpiElement, KpiRequest kpiRequest) {

        String requestTrackerId = getRequestTrackerId();

        String startDate;
        String endDate;

        // Sort sprint nodes by start date for chronological processing
        sprintLeafNodeList.sort(
                (node1, node2) -> node1.getSprintFilter().getStartDate().compareTo(node2.getSprintFilter().getStartDate()));

        startDate = sprintLeafNodeList.get(0).getSprintFilter().getStartDate();
        endDate = sprintLeafNodeList.get(sprintLeafNodeList.size() - 1).getSprintFilter().getEndDate();

        // Fetch data from database for the date range
        Map<String, Object> storyDefectDataListMap = fetchKPIDataFromDb(sprintLeafNodeList, startDate, endDate, kpiRequest);

        List<JiraIssue> storyList = (List<JiraIssue>) storyDefectDataListMap.get(STORY_LIST);
        List<SprintWiseStory> sprintWiseStoryList = (List<SprintWiseStory>) storyDefectDataListMap
                .get(SPRINT_WISE_STORY_DATA);

        // Group stories by sprint
        Map<Pair<String, String>, List<SprintWiseStory>> sprintWiseMap = sprintWiseStoryList.stream().collect(
                Collectors.groupingBy(sws -> Pair.of(sws.getBasicProjectConfigId(), sws.getSprint()), Collectors.toList()));

        // Maps to store processed data
        Map<Pair<String, String>, Map<String, Long>> sprintWiseDefectSeverityCountMap = new HashMap<>();
        Map<Pair<String, String>, Integer> sprintWiseTDCMap = new HashMap<>();
        Map<Pair<String, String>, List<JiraIssue>> sprintWiseDefectDataListMap = new HashMap<>();

        List<KPIExcelData> excelData = new ArrayList<>();

        // Process each sprint to calculate defect counts by severity
        Set<String> projectWiseSeverityList = new HashSet<>();
        sprintWiseMap.forEach((sprintFilter, sprintWiseStories) -> {
            List<String> storyIdList = new ArrayList<>();
            sprintWiseStories.stream().map(SprintWiseStory::getStoryList).collect(Collectors.toList())
                    .forEach(storyIdList::addAll);

            // Filter defects linked to stories in this sprint
            List<JiraIssue> sprintWiseDefectDataList = ((List<JiraIssue>) storyDefectDataListMap.get(TOTAL_DEFECT_DATA))
                    .stream().filter(f -> CollectionUtils.containsAny(f.getDefectStoryID(), storyIdList))
                    .collect(Collectors.toList());

            // Calculate severity counts for defects
            Map<String, Long> severityCountMap = KPIHelperUtil.setSeverityScrum(sprintWiseDefectDataList, customApiConfig);
            projectWiseSeverityList.addAll(severityCountMap.keySet());
            sprintWiseDefectDataListMap.put(sprintFilter, sprintWiseDefectDataList);

            // Log sprint-wise details if detailed logging is enabled
            setSprintWiseLogger(sprintFilter, storyIdList, sprintWiseDefectDataList, severityCountMap);

            // Store severity counts and total defect count
            sprintWiseDefectSeverityCountMap.put(sprintFilter, severityCountMap);
            sprintWiseTDCMap.put(sprintFilter, sprintWiseDefectDataList.size());
        });

        // Process each sprint node to populate with calculated data
        sprintLeafNodeList.forEach(node -> {
            String trendLineName = node.getProjectFilter().getName();
            Pair<String, String> currentNodeIdentifier = Pair.of(node.getProjectFilter().getBasicProjectConfigId().toString(),
                    node.getSprintFilter().getId());

            Map<String, List<DataCount>> dataCountMap = new HashMap<>();
            Map<String, Long> severityMap = sprintWiseDefectSeverityCountMap.getOrDefault(currentNodeIdentifier, new HashMap<>());
            Map<String, Long> finalMap = new HashMap<>();
            Map<String, Object> overAllHoverValueMap = new HashMap<>();
            
            if (CollectionUtils.isNotEmpty(projectWiseSeverityList)) {
                // Process each severity and prepare data for visualization
                projectWiseSeverityList.forEach(severity -> {
                    Long severityWiseCount = severityMap.getOrDefault(severity, 0L);
                    finalMap.put(StringUtils.capitalize(severity), severityWiseCount);
                    overAllHoverValueMap.put(StringUtils.capitalize(severity), severityWiseCount.intValue());
                });
                
                // Ensure all serverites have entries, even if count is zero
                projectWiseSeverityList.forEach(severity -> finalMap.computeIfAbsent(severity, val -> 0L));
                
                // Calculate overall count
                Long overAllCount = finalMap.values().stream().mapToLong(val -> val).sum();
                finalMap.put(CommonConstant.OVERALL, overAllCount);

                // Create DataCount objects for each severity
                String finalTrendLineName = trendLineName;
                finalMap.forEach((severity, value) -> {
                    /// kkk
                    DataCount dataCount = getDataCountObject(node, finalTrendLineName, overAllHoverValueMap, severity, value);
                    trendValueList.add(dataCount);
                    dataCountMap.computeIfAbsent(severity, k -> new ArrayList<>()).add(dataCount);
                });

                // Populate Excel data if needed
                populateExcelDataObject(requestTrackerId, node.getSprintFilter().getName(), excelData,
                        sprintWiseDefectDataListMap.get(currentNodeIdentifier), customApiConfig, storyList);
            }

            // Log debug information
            log.debug("[DC-SPRINT-WISE][{}]. DC for sprint {}  is {} and trend value is {}", requestTrackerId,
                    node.getSprintFilter().getName(), sprintWiseDefectSeverityCountMap.get(currentNodeIdentifier),
                    sprintWiseTDCMap.get(currentNodeIdentifier));

            // Set value in the node
            mapTmp.get(node.getId()).setValue(dataCountMap);
        });

        // Set Excel data and columns in KPI element
        kpiElement.setExcelData(excelData);
        kpiElement.setExcelColumns(
                KPIExcelColumn.DEFECT_SEVERITY_INDEX.getColumns(sprintLeafNodeList, cacheService, filterHelperService));
	}

	/**
	 * Creates a DataCount object for trend analysis visualization.
	 * 
	 * This method creates a DataCount object with the provided parameters for
	 * use in trend analysis visualization. It sets up the data structure with
	 * appropriate values and hover information.
	 * 
	 * @param node                Node for which the data count is being created
	 * @param trendLineName       Name of the trend line
	 * @param overAllHoverValueMap Map containing hover values for the 'Overall' category
	 * @param key                 Key identifying the severity category (P1, P2, etc.)
	 * @param value               Count value for the specified severity
	 * @return DataCount object populated with the provided data
	 */
	private DataCount getDataCountObject(Node node, String trendLineName, Map<String, Object> overAllHoverValueMap,
			String key, Long value) {
		DataCount dataCount = new DataCount();
		dataCount.setData(String.valueOf(value));
		dataCount.setSProjectName(trendLineName);
		dataCount.setSSprintID(node.getSprintFilter().getId());
		dataCount.setSSprintName(node.getSprintFilter().getName());
		dataCount.setValue(value);
		dataCount.setKpiGroup(key);
		Map<String, Object> hoverValueMap = new HashMap<>();
		if (key.equalsIgnoreCase(CommonConstant.OVERALL)) {
			dataCount.setHoverValue(overAllHoverValueMap);
		} else {
			hoverValueMap.put(key, value.intValue());
			dataCount.setHoverValue(hoverValueMap);
		}
		return dataCount;
	}

	/**
	 * Populates Excel data objects for export functionality.
	 * 
	 * This method prepares data for Excel export when requested. It formats the
	 * defect data in a structure suitable for Excel export.
	 * 
	 * @param requestTrackerId        Tracker ID for the request
	 * @param sprintName              Name of the sprint
	 * @param excelData               List to store Excel data
	 * @param sprintWiseDefectDataList List of defects for the sprint
	 * @param customApiConfig         Custom API configuration
	 * @param storyList               List of stories
	 */
	private void populateExcelDataObject(String requestTrackerId, String sprintName, List<KPIExcelData> excelData,
			List<JiraIssue> sprintWiseDefectDataList, CustomApiConfig customApiConfig, List<JiraIssue> storyList) {

		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			KPIExcelUtility.populateDefectSeverityRelatedExcelData(sprintName, sprintWiseDefectDataList, excelData, customApiConfig,
					storyList);
		}
	}

	/**
	 * Sets up and logs database query information for debugging purposes.
	 * 
	 * This method logs detailed information about stories and their linked defects
	 * when the detailed logger is enabled. This is useful for debugging database
	 * query results.
	 *
	 * @param storyIdList           List of story IDs retrieved from the database
	 * @param defectLinkedWithStory List of defects linked with stories
	 */
	private void setDbQueryLogger(List<String> storyIdList, List<JiraIssue> defectLinkedWithStory) {

		if (customApiConfig.getApplicationDetailedLogger().equalsIgnoreCase("on")) {
			log.info(SEPARATOR_ASTERISK);
			log.info("************* DC (dB) *******************");
			log.info("Story[{}]: {}", storyIdList.size(), storyIdList);
			log.info("DefectLinkedWith -> story[{}]: {}", defectLinkedWithStory.size(),
					defectLinkedWithStory.stream().map(JiraIssue::getNumber).collect(Collectors.toList()));
			log.info(SEPARATOR_ASTERISK);
			log.info("******************X----X*******************");
		}
	}

	/**
	 * Sets up and logs sprint-level details for debugging purposes.
	 * 
	 * This method logs detailed information about a sprint, including its stories,
	 * defects, and severity counts when the detailed logger is enabled. This is
	 * useful for debugging sprint-level data processing.
	 *
	 * @param sprint                 Pair containing sprint ID and name
	 * @param storyIdList            List of story IDs in the sprint
	 * @param sprintWiseDefectDataList List of defects for the current sprint
	 * @param severityCountMap       Map of defect counts by severity
	 */
	private void setSprintWiseLogger(Pair<String, String> sprint, List<String> storyIdList,
			List<JiraIssue> sprintWiseDefectDataList, Map<String, Long> severityCountMap) {

		if (customApiConfig.getApplicationDetailedLogger().equalsIgnoreCase("on")) {
			log.info(SEPARATOR_ASTERISK);
			log.info("************* SPRINT WISE DC *******************");
			log.info("Sprint: {}", sprint.getValue());
			log.info("Story[{}]: {}", storyIdList.size(), storyIdList);
			log.info("DefectDataList[{}]: {}", sprintWiseDefectDataList.size(),
					sprintWiseDefectDataList.stream().map(JiraIssue::getNumber).collect(Collectors.toList()));
			log.info("DefectSeverityCountMap: {}", severityCountMap);
			log.info(SEPARATOR_ASTERISK);
			log.info(SEPARATOR_ASTERISK);
		}
	}

	/**
	 * Calculates the KPI value from a list of values.
	 * 
	 * This method calculates the final KPI value from a list of individual values.
	 * For Defect Count by Severity, it delegates to the calculateKpiValueForLong method.
	 *
	 * @param valueList List of values to calculate the KPI from
	 * @param kpiName   Name of the KPI
	 * @return Calculated KPI value
	 */
	@Override
	public Long calculateKpiValue(List<Long> valueList, String kpiName) {
		return calculateKpiValueForLong(valueList, kpiName);
	}

	/**
	 * Returns a list of severity types for defect categorization.
	 * 
	 * This method provides a list of severity types (1, 2, 3, 4, High, Low, Medium) with an
	 * optional 'OVERALL' category based on the input parameter.
	 *
	 * @param addOverall If true, includes the 'OVERALL' category in the list
	 * @return List of severity types
	 */
	private List<String> severityTypes(boolean addOverall) {
		if (addOverall) {
			return Arrays.asList(CommonConstant.OVERALL, Constant.DSE_1, Constant.DSE_2, Constant.DSE_3, Constant.DSE_4, Constant.DSE_H,Constant.DSE_M,Constant.DSE_L);
		} else {
			return Arrays.asList(Constant.DSE_1, Constant.DSE_2, Constant.DSE_3, Constant.DSE_4, Constant.DSE_H,Constant.DSE_M,Constant.DSE_L);
		}
	}

	/**
	 * Calculates the threshold value for the KPI.
	 * 
	 * This method calculates the threshold value for the Defect Count by Severity KPI
	 * based on the field mapping configuration.
	 *
	 * @param fieldMapping Field mapping containing threshold configuration
	 * @return Calculated threshold value
	 */
	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(fieldMapping.getJiraDefectSeverityKPI194(), KPICode.DEFECT_SEVERITY_INDEX.getKpiId());
	}

    /**
     * Calculates KPI metrics from the provided data.
     *
     * This method processes the raw data to calculate the actual KPI metrics.
     * For Defect Count by Severity, it returns the object map as is since the
     * actual calculation is done in other methods.
     *
     * @param objectMap Map containing the data needed for calculation
     * @return Map with calculated KPI metrics
     */
    @Override
    public Long calculateKPIMetrics(Map<String, Object> objectMap) {
        return 0L;
    }
}