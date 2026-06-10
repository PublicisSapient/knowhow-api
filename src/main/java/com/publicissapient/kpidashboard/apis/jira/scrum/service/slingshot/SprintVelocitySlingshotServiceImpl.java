package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.util.concurrent.AtomicDouble;
import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.KpiDataCacheService;
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
import com.publicissapient.kpidashboard.apis.jira.service.SprintVelocityServiceHelper;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepositoryCustom;

import lombok.extern.slf4j.Slf4j;

import static com.publicissapient.kpidashboard.common.constant.CommonConstant.HIERARCHY_LEVEL_ID_PROJECT;

@Slf4j
@Service
public class SprintVelocitySlingshotServiceImpl
		extends JiraKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String VELOCITY = "Velocity";
	private static final String AVERAGE_VELOCITY = "AverageVelocity";
	private static final String SEPARATOR_ASTERISK = "*************************************";
	private static final String JIRA_ISSUES = "JIRAISSUES";

	private static final String STORY_LOG = "Story[{}]: {}";
	@Autowired private CustomApiConfig customApiConfig;
	@Autowired private ConfigHelperService configHelperService;
	@Autowired private JiraIssueRepository jiraIssueRepository;

	/**
	 * Gets Qualifier Type
	 *
	 * @return KPICode's <tt>SPRINT_VELOCITY</tt> enum
	 */
	@Override
	public String getQualifierType() {
		return KPICode.SPRINT_VELOCITY_SLINGSHOT.name();
	}

	/**
	 * Gets KPI Data
	 *
	 * @param kpiRequest
	 * @param kpiElement
	 * @param treeAggregatorDetail
	 */
	@Override
	public KpiElement getKpiData(
			KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
			throws ApplicationException {

		List<DataCount> trendValueList = new ArrayList<>();
		Node root = treeAggregatorDetail.getRoot();
		Map<String, Node> mapTmp = treeAggregatorDetail.getMapTmp();
		List<Node> projectList =
				treeAggregatorDetail.getMapOfListOfProjectNodes().get(HIERARCHY_LEVEL_ID_PROJECT);

								sprintWiseLeafNodeValue(mapTmp, projectList.get(0), trendValueList, kpiElement, kpiRequest);

		log.debug(
				"[SPRINT-VELOCITY-LEAF-NODE-VALUE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				root);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValue(root, nodeWiseKPIValue, KPICode.SPRINT_VELOCITY);
		List<DataCount> trendValues =
				getTrendValues(kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.SPRINT_VELOCITY);
		kpiElement.setTrendValueList(trendValues);
		log.debug(
				"[SPRINT-VELOCITY-AGGREGATED-VALUE][{}]. Aggregated Value at each level in the tree {}",
				kpiRequest.getRequestTrackerId(),
				root);
		return kpiElement;
	}

	/**
	 * Fetches KPI Data from DB
	 *
	 * @param leafNodeList
	 * @param startDate
	 * @param endDate
	 * @param kpiRequest
	 * @return {@code Map<String, Object>}
	 */
	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
		Map<String, Object> resultListMap = new HashMap<>();

		ObjectId basicProjectConfigId = leafNodeList.get(0).getProjectFilter().getBasicProjectConfigId();

		FieldMapping fieldMapping = configHelperService.getFieldMapping(basicProjectConfigId);
		List<String> closedStatuses = CollectionUtils.isEmpty(fieldMapping.getJiraTicketClosedStatus())?new ArrayList<>():fieldMapping.getJiraTicketClosedStatus();
		closedStatuses.addAll(CollectionUtils.isEmpty(fieldMapping.getJiraIterationCompletionStatusKPI205())?List.of():fieldMapping.getJiraIterationCompletionStatusKPI205());

		List<JiraIssue> jiraIssueList = jiraIssueRepository.findByBasicProjectConfigId(basicProjectConfigId.toString());

		LocalDateTime endDateTime = LocalDateTime.now();
		LocalDateTime startDateTime = endDateTime.minusWeeks(11);
		List<JiraIssue> jiraIssuesFiltered = jiraIssueList.stream().filter(jiraIssue -> closedStatuses.stream().map(String::toLowerCase).toList().contains(jiraIssue.getStatus().toLowerCase()) &&
				DateUtil.isWithinDateTimeRange(DateUtil.convertToUTCLocalDateTime(jiraIssue.getChangeDate()), startDateTime, endDateTime)).toList();

		resultListMap.put(JIRA_ISSUES, jiraIssuesFiltered);

		return resultListMap;
	}



	/**
	 * Calculates KPI Metrics
	 *
	 * @param techDebtStoryMap
	 * @return Double
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Double calculateKPIMetrics(Map<String, Object> techDebtStoryMap) {

		String requestTrackerId = getRequestTrackerId();
		double sprintVelocity = 0.0d;
		List<JiraIssue> sprintVelocityList = (List<JiraIssue>) techDebtStoryMap.get(JIRA_ISSUES);
		log.debug(
				"[SPRINT-VELOCITY][{}]. Stories Count: {}", requestTrackerId, sprintVelocityList.size());
		for (JiraIssue jiraIssue : sprintVelocityList) {
			sprintVelocity = sprintVelocity + Double.parseDouble(jiraIssue.getEstimate());
		}
		return sprintVelocity;
	}

	/**
	 * Populates KPI value to sprint leaf nodes and gives the trend analysis at sprint wise.
	 *
	 * @param mapTmp
	 * @param trendValueList
	 * @param projectNode
	 * @param kpiElement
	 */
	@SuppressWarnings("unchecked")
	private void sprintWiseLeafNodeValue(
			Map<String, Node> mapTmp,
			Node projectNode,
			List<DataCount> trendValueList,
			KpiElement kpiElement,
			KpiRequest kpiRequest) {

		String requestTrackerId = getRequestTrackerId();
		long time = System.currentTimeMillis();
		LocalDateTime endDate = LocalDateTime.now();
		LocalDateTime startDate = endDate.minusWeeks(13);
		Map<String, Object> sprintVelocityStoryMap =
				fetchKPIDataFromDb(List.of(projectNode), startDate.toString(), endDate.toString(), kpiRequest);
		log.info("Sprint Velocity taking fetchKPIDataFromDb {}", System.currentTimeMillis() - time);

		List<JiraIssue> allJiraIssue = (List<JiraIssue>) sprintVelocityStoryMap.get(JIRA_ISSUES);



		FieldMapping fieldMapping =
				configHelperService
						.getFieldMappingMap()
						.get(projectNode.getProjectFilter().getBasicProjectConfigId());

		Map<String, Set<JiraIssue>> jiraIssuesByDateRange = new HashMap<>();
		Map<String, Double> velocityByDateRange = new HashMap<>();

		for (int i = 0; i < 10; i++) {
			CustomDateRange periodRange =
					KpiDataHelper.getStartAndEndDateTimeForDataFiltering(endDate, CommonConstant.WEEK);

			Set<JiraIssue> issueDetailsSet = allJiraIssue.stream().filter(jiraIssue -> DateUtil.isWithinDateTimeRange(DateUtil.convertToUTCLocalDateTime(jiraIssue.getChangeDate()), periodRange.getStartDateTime(), periodRange.getEndDateTime())).collect(Collectors.toSet());
			double periodSpringVelocity;
			if (StringUtils.isNotEmpty(fieldMapping.getEstimationCriteria())
					&& fieldMapping.getEstimationCriteria().equalsIgnoreCase(CommonConstant.STORY_POINT)) {
				periodSpringVelocity =
						issueDetailsSet.stream()
								.mapToDouble(ji -> Optional.ofNullable(ji.getStoryPoints()).orElse(0.0d))
								.sum();
			} else {
				double totalOriginalEstimate =
						issueDetailsSet.stream()
								.filter(
										jiraIssue ->
												Objects.nonNull(jiraIssue.getAggregateTimeOriginalEstimateMinutes()))
								.mapToDouble(JiraIssue::getAggregateTimeOriginalEstimateMinutes)
								.sum();
				double inHours = totalOriginalEstimate / 60;
				periodSpringVelocity = inHours / fieldMapping.getStoryPointToHourMapping();
			}

			String dateLabel = KpiHelperService.getDateRange(periodRange, CommonConstant.WEEK);

			jiraIssuesByDateRange.put(dateLabel, issueDetailsSet);
			velocityByDateRange.put(dateLabel, periodSpringVelocity);

			endDate = DeveloperKpiHelper.getNextRangeDate(CommonConstant.WEEK, endDate);
		}



		List<KPIExcelData> excelData = new ArrayList<>();
		Map<String, Integer> avgVelocityCount = new HashMap<>();

		velocityByDateRange.forEach((key, value) -> {
			String projId = projectNode.getProjectFilter().getBasicProjectConfigId().toString();
			String trendLineName = projectNode.getProjectFilter().getName();

//					populateExcelDataObject(
//							requestTrackerId, excelData, currentSprintLeafVelocityMap, projectNode, fieldMapping);
//					setSprintWiseLogger(
//							projectNode.getSprintFilter().getName(),
//							currentSprintLeafVelocityMap.get(currentNodeIdentifier),
//							sprintVelocityForCurrentLeaf);

			DataCount dataCount = new DataCount();
			dataCount.setData(String.valueOf(roundingOff(value)));
			dataCount.setSProjectName(trendLineName);
			dataCount.setSSprintID(key);
			dataCount.setSSprintName(key);
			dataCount.setValue(roundingOff(value));
			if (!avgVelocityCount.containsKey(projId)) {
				avgVelocityCount.put(projId, 0);
			}

			double averageVelocity =
					getAverageVelocity(
							velocityByDateRange, key);
			if (averageVelocity >= 0) {
				dataCount.setLineValue(averageVelocity);
				Map<String, Object> hoverValue = new HashMap<>();
				hoverValue.put(AVERAGE_VELOCITY, roundingOff(averageVelocity));
				hoverValue.put(VELOCITY, roundingOff((Double) dataCount.getValue()));
				dataCount.setHoverValue(hoverValue);
				avgVelocityCount.put(projId, avgVelocityCount.get(projId) + 1);
			} else {
				dataCount.setValue(0.0);
			}

			trendValueList.add(dataCount);
		});

		mapTmp.get(projectNode.getId()).setValue(trendValueList);
		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(
				KPIExcelColumn.SPRINT_VELOCITY.getColumns());
	}


	/**
	 * Get average velocity of 5 sprints
	 *
	 * @param sprintVelocityMap
	 * @param
	 * @return
	 */
	private double getAverageVelocity(
			Map<String, Double> sprintVelocityMap,
			String currentSprintComponentId) {
		AtomicDouble sumVelocity = new AtomicDouble();
		AtomicInteger count = new AtomicInteger();
		int sprintCountForAvgVel = customApiConfig.getSprintVelocityLimit() + 1;
		AtomicInteger validCount = new AtomicInteger();
		AtomicBoolean flag = new AtomicBoolean(false);
		sprintVelocityMap
				.entrySet()
				.forEach(
						velocityMap -> {
								count.set(count.get() + 1);
								if ((velocityMap.getKey().equalsIgnoreCase(currentSprintComponentId)
												|| flag.get())
										&& validCount.get() < sprintCountForAvgVel) {
									flag.set(true);
									validCount.set(validCount.get() + 1);
									sumVelocity.set(sumVelocity.get() + velocityMap.getValue());
								}
						});
		if (validCount.get() < sprintCountForAvgVel) {
			return sumVelocity.get() / validCount.get();
		}
		return sumVelocity.get() / sprintCountForAvgVel;
	}

	private void populateExcelDataObject(
			String requestTrackerId,
			List<KPIExcelData> excelData,
			Map<Pair<String, String>, Set<JiraIssue>> currentSprintLeafVelocityMap,
			Node node,
			FieldMapping fieldMapping) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			Pair<String, String> currentNodeIdentifier =
					Pair.of(
							node.getProjectFilter().getBasicProjectConfigId().toString(),
							node.getSprintFilter().getId());

			if (MapUtils.isNotEmpty(currentSprintLeafVelocityMap)
					&& CollectionUtils.isNotEmpty(currentSprintLeafVelocityMap.get(currentNodeIdentifier))) {
				Set<JiraIssue> jiraIssues = currentSprintLeafVelocityMap.get(currentNodeIdentifier);
				Map<String, JiraIssue> totalSprintStoryMap = new HashMap<>();
				jiraIssues.stream()
						.forEach(issue -> totalSprintStoryMap.putIfAbsent(issue.getNumber(), issue));
				KPIExcelUtility.populateSprintVelocity(
						node.getSprintFilter().getName(), totalSprintStoryMap, excelData, fieldMapping);
			}
		}
	}

	/**
	 * Sets Sprint wise Logger
	 *
	 * @param sprint
	 * @param issueDetailsSet
	 * @param sprintVelocity
	 */
	private void setSprintWiseLogger(
			String sprint, Set<JiraIssue> issueDetailsSet, Double sprintVelocity) {

		if (customApiConfig.getApplicationDetailedLogger().equalsIgnoreCase("on")) {
			log.info(SEPARATOR_ASTERISK);
			log.info("************* SPRINT WISE Sprint Velocity *******************");
			log.info("Sprint: {}", sprint);
			if (CollectionUtils.isNotEmpty(issueDetailsSet)) {
				List<String> storyIdList = issueDetailsSet.stream().map(JiraIssue::getNumber).toList();
				log.info(STORY_LOG, storyIdList.size(), storyIdList);
				List<Double> storyPointIdList =
						issueDetailsSet.stream().map(JiraIssue::getStoryPoints).toList();
				log.info(STORY_LOG, storyIdList.size(), storyPointIdList);
			}
			log.info("Sprint Velocity: {}", sprintVelocity);
			log.info(SEPARATOR_ASTERISK);
			log.info(SEPARATOR_ASTERISK);
		}
	}

	@Override
	public Double calculateKpiValue(List<Double> valueList, String kpiName) {
		return calculateKpiValueForDouble(valueList, kpiName);
	}

	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI205(), KPICode.SPRINT_VELOCITY.getKpiId());
	}
}
