package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot;

import static com.publicissapient.kpidashboard.common.constant.CommonConstant.HIERARCHY_LEVEL_ID_PROJECT;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.util.concurrent.AtomicDouble;
import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.service.JiraKPIService;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SprintVelocitySlingshotServiceImpl
		extends JiraKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String VELOCITY = "Velocity";
	private static final String AVERAGE_VELOCITY = "Average Velocity";
	private static final String COMMITTED_SCOPE = "Committed Scope";
	private static final String JIRA_ISSUES = "JIRAISSUES";
	private static final String NON_VELOCITY_ISSUES = "NON_VELOCITY_ISSUES";
	private static final String WEEKLY = "Weekly";
	private static final String BI_WEEKLY = "Bi-Weekly";
	private static final String MONTHLY = "Monthly";
	private static final DateTimeFormatter WEEK_LABEL_FORMATTER =
			DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter MONTH_LABEL_FORMATTER =
			DateTimeFormatter.ofPattern("MMM-yyyy", Locale.ENGLISH);

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

		if (customApiConfig.isSlingshotSprintVelocityMultiGranularity()) {
			List<DataCountGroup> groups = new ArrayList<>();
			DataCountGroup weeklyGroup = new DataCountGroup();
			weeklyGroup.setFilter(WEEKLY);
			weeklyGroup.setValue(trendValues);
			groups.add(weeklyGroup);
			DataCountGroup biWeeklyGroup = new DataCountGroup();
			biWeeklyGroup.setFilter(BI_WEEKLY);
			biWeeklyGroup.setValue(wrapAggregated(trendValues, this::aggregateWeeklyToBiWeekly));
			groups.add(biWeeklyGroup);
			DataCountGroup monthlyGroup = new DataCountGroup();
			monthlyGroup.setFilter(MONTHLY);
			monthlyGroup.setValue(wrapAggregated(trendValues, this::aggregateWeeklyToMonthly));
			groups.add(monthlyGroup);
			kpiElement.setTrendValueList(groups);
		} else {
			kpiElement.setTrendValueList(trendValues);
		}

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

		ObjectId basicProjectConfigId =
				leafNodeList.get(0).getProjectFilter().getBasicProjectConfigId();

		FieldMapping fieldMapping = configHelperService.getFieldMapping(basicProjectConfigId);
		List<String> closedStatuses =
				CollectionUtils.isEmpty(fieldMapping.getJiraTicketClosedStatus())
						? new ArrayList<>()
						: new ArrayList<>(fieldMapping.getJiraTicketClosedStatus());
		closedStatuses.addAll(
				CollectionUtils.isEmpty(fieldMapping.getJiraIterationCompletionStatusKPI205())
						? List.of()
						: fieldMapping.getJiraIterationCompletionStatusKPI205());

		List<String> issueTypesKPI205 =
				CollectionUtils.isEmpty(fieldMapping.getJiraIterationIssueTypeKPI205())
						? List.of()
						: fieldMapping.getJiraIterationIssueTypeKPI205().stream()
								.map(String::toLowerCase)
								.toList();

		List<JiraIssue> jiraIssueList =
				jiraIssueRepository.findByBasicProjectConfigId(basicProjectConfigId.toString());

		LocalDateTime endDateTime = LocalDateTime.now();
		LocalDateTime startDateTime = endDateTime.minusWeeks(11);
		List<String> closedStatusesLower = closedStatuses.stream().map(String::toLowerCase).toList();

		List<JiraIssue> jiraIssuesFiltered =
				jiraIssueList.stream()
						.filter(
								jiraIssue ->
										(issueTypesKPI205.isEmpty()
														|| issueTypesKPI205.contains(jiraIssue.getTypeName().toLowerCase()))
												&& closedStatusesLower.contains(jiraIssue.getStatus().toLowerCase())
												&& DateUtil.isWithinDateTimeRange(
														DateUtil.convertToUTCLocalDateTime(jiraIssue.getChangeDate()),
														startDateTime,
														endDateTime))
						.toList();

		List<JiraIssue> nonVelocityIssues =
				jiraIssueList.stream()
						.filter(
								jiraIssue ->
										(issueTypesKPI205.isEmpty()
														|| issueTypesKPI205.contains(jiraIssue.getTypeName().toLowerCase()))
												&& DateUtil.isWithinDateTimeRange(
														DateUtil.convertToUTCLocalDateTime(jiraIssue.getChangeDate()),
														startDateTime,
														endDateTime))
						.toList();

		resultListMap.put(JIRA_ISSUES, jiraIssuesFiltered);
		resultListMap.put(NON_VELOCITY_ISSUES, nonVelocityIssues);

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
				fetchKPIDataFromDb(
						List.of(projectNode), startDate.toString(), endDate.toString(), kpiRequest);
		log.info("Sprint Velocity taking fetchKPIDataFromDb {}", System.currentTimeMillis() - time);

		List<JiraIssue> allJiraIssue = (List<JiraIssue>) sprintVelocityStoryMap.get(JIRA_ISSUES);
		List<JiraIssue> allNonVelocityIssues =
				(List<JiraIssue>) sprintVelocityStoryMap.get(NON_VELOCITY_ISSUES);

		FieldMapping fieldMapping =
				configHelperService
						.getFieldMappingMap()
						.get(projectNode.getProjectFilter().getBasicProjectConfigId());

		Map<String, Set<JiraIssue>> jiraIssuesByDateRange = new LinkedHashMap<>();
		Map<String, Double> velocityByDateRange = new LinkedHashMap<>();
		Map<String, Double> committedScopeByDateRange = new LinkedHashMap<>();

		Map<String, LocalDateTime> issueDateTimeCache = new HashMap<>();
		allJiraIssue.forEach(
				ji ->
						issueDateTimeCache.put(
								ji.getNumber(), DateUtil.convertToUTCLocalDateTime(ji.getChangeDate())));
		allNonVelocityIssues.forEach(
				ji ->
						issueDateTimeCache.putIfAbsent(
								ji.getNumber(), DateUtil.convertToUTCLocalDateTime(ji.getChangeDate())));

		for (int i = 0; i < 12; i++) {
			CustomDateRange periodRange =
					KpiDataHelper.getStartAndEndDateTimeForDataFiltering(endDate, CommonConstant.WEEK);

			Set<JiraIssue> issueDetailsSet =
					allJiraIssue.stream()
							.filter(
									jiraIssue ->
											DateUtil.isWithinDateTimeRange(
													issueDateTimeCache.get(jiraIssue.getNumber()),
													periodRange.getStartDateTime(),
													periodRange.getEndDateTime()))
							.collect(Collectors.toSet());

			Set<JiraIssue> nonVelocityIssuesSet =
					allNonVelocityIssues.stream()
							.filter(
									jiraIssue ->
											DateUtil.isWithinDateTimeRange(
													issueDateTimeCache.get(jiraIssue.getNumber()),
													periodRange.getStartDateTime(),
													periodRange.getEndDateTime()))
							.collect(Collectors.toSet());

			double periodSpringVelocity;
			double periodCommittedScope;
			if (StringUtils.isNotEmpty(fieldMapping.getEstimationCriteria())
					&& fieldMapping.getEstimationCriteria().equalsIgnoreCase(CommonConstant.STORY_POINT)) {
				periodSpringVelocity =
						issueDetailsSet.stream()
								.mapToDouble(ji -> Optional.ofNullable(ji.getStoryPoints()).orElse(0.0d))
								.sum();
				periodCommittedScope =
						nonVelocityIssuesSet.stream()
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

				double nonVelocityEstimate =
						nonVelocityIssuesSet.stream()
								.filter(
										jiraIssue ->
												Objects.nonNull(jiraIssue.getAggregateTimeOriginalEstimateMinutes()))
								.mapToDouble(JiraIssue::getAggregateTimeOriginalEstimateMinutes)
								.sum();
				double nonVelocityHours = nonVelocityEstimate / 60;
				periodCommittedScope = nonVelocityHours / fieldMapping.getStoryPointToHourMapping();
			}

			String dateLabel = KpiHelperService.getDateRange(periodRange, CommonConstant.WEEK);

			jiraIssuesByDateRange.put(dateLabel, issueDetailsSet);
			velocityByDateRange.put(dateLabel, periodSpringVelocity);
			committedScopeByDateRange.put(dateLabel, periodCommittedScope);

			endDate = DeveloperKpiHelper.getNextRangeDate(CommonConstant.WEEK, endDate);
		}

		List<KPIExcelData> excelData = new ArrayList<>();
		populateExcelDataObject(
				requestTrackerId, excelData, new HashSet<>(allJiraIssue), projectNode, fieldMapping);
		Map<String, Integer> avgVelocityCount = new HashMap<>();

		velocityByDateRange.forEach(
				(key, value) -> {
					String projId = projectNode.getProjectFilter().getBasicProjectConfigId().toString();
					String trendLineName = projectNode.getProjectFilter().getName();

					DataCount dataCount = new DataCount();
					dataCount.setData(String.valueOf(roundingOff(value)));
					dataCount.setSProjectName(trendLineName);
					dataCount.setSSprintID(key);
					dataCount.setSSprintName(key);
					dataCount.setValue(roundingOff(value));
					if (!avgVelocityCount.containsKey(projId)) {
						avgVelocityCount.put(projId, 0);
					}

					double averageVelocity = getAverageVelocity(velocityByDateRange, key);
					double committedScope = committedScopeByDateRange.getOrDefault(key, 0.0);
					if (averageVelocity >= 0) {
						dataCount.setLineValue(roundingOff(averageVelocity));
						dataCount.setAggregationValue(roundingOff(committedScope));
						Map<String, Object> hoverValue = new HashMap<>();
						hoverValue.put(VELOCITY, roundingOff((Double) dataCount.getValue()));
						hoverValue.put(AVERAGE_VELOCITY, roundingOff(averageVelocity));
						hoverValue.put(COMMITTED_SCOPE, roundingOff(committedScope));
						dataCount.setHoverValue(hoverValue);
						avgVelocityCount.put(projId, avgVelocityCount.get(projId) + 1);
					} else {
						dataCount.setValue(0.0);
					}

					trendValueList.add(dataCount);
				});

		mapTmp.get(projectNode.getId()).setValue(trendValueList);
		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(KPIExcelColumn.SPRINT_VELOCITY.getColumns());
	}

	/**
	 * Get average velocity of 5 sprints
	 *
	 * @param sprintVelocityMap
	 * @param
	 * @return
	 */
	private double getAverageVelocity(
			Map<String, Double> sprintVelocityMap, String currentSprintComponentId) {
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
							if ((velocityMap.getKey().equalsIgnoreCase(currentSprintComponentId) || flag.get())
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
			Set<JiraIssue> jiraIssueSet,
			Node node,
			FieldMapping fieldMapping) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())
				&& CollectionUtils.isNotEmpty(jiraIssueSet)) {
			Map<String, JiraIssue> totalSprintStoryMap = new HashMap<>();
			jiraIssueSet.forEach(issue -> totalSprintStoryMap.putIfAbsent(issue.getNumber(), issue));
			KPIExcelUtility.populateSprintVelocity(
					node.getProjectFilter().getName(), totalSprintStoryMap, excelData, fieldMapping);
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

	@SuppressWarnings("unchecked")
	private List<DataCount> wrapAggregated(
			List<DataCount> projectDataCounts, Function<List<DataCount>, List<DataCount>> aggregator) {
		List<DataCount> result = new ArrayList<>();
		for (DataCount projectDc : projectDataCounts) {
			if (projectDc.getValue() instanceof List<?> innerList) {
				DataCount wrapper = new DataCount();
				wrapper.setData(projectDc.getData());
				wrapper.setValue(aggregator.apply((List<DataCount>) innerList));
				result.add(wrapper);
			}
		}
		return result;
	}

	private List<DataCount> aggregateWeeklyToBiWeekly(List<DataCount> weeklyList) {
		List<DataCount> biWeeklyList = new ArrayList<>();
		for (int i = 0; i < weeklyList.size(); i += 2) {
			List<DataCount> pair = weeklyList.subList(i, Math.min(i + 2, weeklyList.size()));
			double pairVelocity =
					pair.stream()
							.mapToDouble(dc -> dc.getValue() instanceof Number n ? n.doubleValue() : 0.0)
							.sum();
			double pairCommittedScope =
					pair.stream()
							.filter(dc -> dc.getAggregationValue() instanceof Number)
							.mapToDouble(dc -> ((Number) dc.getAggregationValue()).doubleValue())
							.sum();
			double avgLineValue =
					pair.stream()
							.filter(dc -> dc.getLineValue() instanceof Number)
							.mapToDouble(dc -> ((Number) dc.getLineValue()).doubleValue())
							.average()
							.orElse(0.0);
			String firstId = pair.get(0).getsSprintID();
			String lastId = pair.get(pair.size() - 1).getsSprintID();
			String startPart =
					firstId != null && firstId.contains(" to ") ? firstId.split(" to ")[0].trim() : firstId;
			String endPart =
					lastId != null && lastId.contains(" to ") ? lastId.split(" to ")[1].trim() : lastId;
			String biWeekLabel = startPart + " to " + endPart;
			DataCount dc = new DataCount();
			dc.setSProjectName(pair.get(0).getSProjectName());
			dc.setSSprintID(biWeekLabel);
			dc.setSSprintName(biWeekLabel);
			dc.setData(String.valueOf(roundingOff(pairVelocity)));
			dc.setValue(roundingOff(pairVelocity));
			dc.setLineValue(roundingOff(avgLineValue));
			dc.setAggregationValue(roundingOff(pairCommittedScope));
			Map<String, Object> hoverValue = new HashMap<>();
			hoverValue.put(VELOCITY, roundingOff(pairVelocity));
			hoverValue.put(AVERAGE_VELOCITY, roundingOff(avgLineValue));
			hoverValue.put(COMMITTED_SCOPE, roundingOff(pairCommittedScope));
			dc.setHoverValue(hoverValue);
			biWeeklyList.add(dc);
		}
		return biWeeklyList;
	}

	private List<DataCount> aggregateWeeklyToMonthly(List<DataCount> weeklyList) {
		Map<YearMonth, List<DataCount>> byMonth = new LinkedHashMap<>();
		for (DataCount dc : weeklyList) {
			String sprintId = dc.getsSprintID();
			if (sprintId == null) continue;
			try {
				String startPart =
						sprintId.contains(" to ") ? sprintId.split(" to ")[0].trim() : sprintId.trim();
				LocalDate startDate = LocalDate.parse(startPart, WEEK_LABEL_FORMATTER);
				byMonth.computeIfAbsent(YearMonth.from(startDate), k -> new ArrayList<>()).add(dc);
			} catch (Exception e) {
				log.warn("Could not parse week label for monthly grouping: {}", sprintId);
			}
		}
		List<DataCount> monthlyList = new ArrayList<>();
		byMonth.forEach(
				(ym, monthSlice) -> {
					double monthVelocity =
							monthSlice.stream()
									.mapToDouble(dc -> dc.getValue() instanceof Number n ? n.doubleValue() : 0.0)
									.sum();
					double monthCommittedScope =
							monthSlice.stream()
									.filter(dc -> dc.getAggregationValue() instanceof Number)
									.mapToDouble(dc -> ((Number) dc.getAggregationValue()).doubleValue())
									.sum();
					double avgLineValue =
							monthSlice.stream()
									.filter(dc -> dc.getLineValue() instanceof Number)
									.mapToDouble(dc -> ((Number) dc.getLineValue()).doubleValue())
									.average()
									.orElse(0.0);
					String monthLabel = ym.format(MONTH_LABEL_FORMATTER);
					DataCount dc = new DataCount();
					dc.setSProjectName(monthSlice.get(0).getSProjectName());
					dc.setSSprintID(monthLabel);
					dc.setSSprintName(monthLabel);
					dc.setData(String.valueOf(roundingOff(monthVelocity)));
					dc.setValue(roundingOff(monthVelocity));
					dc.setLineValue(roundingOff(avgLineValue));
					dc.setAggregationValue(roundingOff(monthCommittedScope));
					Map<String, Object> hoverValue = new HashMap<>();
					hoverValue.put(VELOCITY, roundingOff(monthVelocity));
					hoverValue.put(AVERAGE_VELOCITY, roundingOff(avgLineValue));
					hoverValue.put(COMMITTED_SCOPE, roundingOff(monthCommittedScope));
					dc.setHoverValue(hoverValue);
					monthlyList.add(dc);
				});
		return monthlyList;
	}
}
